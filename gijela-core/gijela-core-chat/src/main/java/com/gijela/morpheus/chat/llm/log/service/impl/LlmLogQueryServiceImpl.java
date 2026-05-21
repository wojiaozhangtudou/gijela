package com.gijela.morpheus.chat.llm.log.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gijela.morpheus.chat.domain.entity.ChatMessageEntity;
import com.gijela.morpheus.chat.llm.log.cache.LlmLogCacheService;
import com.gijela.morpheus.chat.llm.log.dto.request.DistributionRequest;
import com.gijela.morpheus.chat.llm.log.dto.request.ExportLogsRequest;
import com.gijela.morpheus.chat.llm.log.dto.request.LogQueryFilter;
import com.gijela.morpheus.chat.llm.log.dto.request.TimeSeriesRequest;
import com.gijela.morpheus.chat.llm.log.dto.request.TopNRequest;
import com.gijela.morpheus.chat.llm.log.dto.response.DistributionResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.ExportLogsResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.LogClearResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.LlmLogRecordVO;
import com.gijela.morpheus.chat.llm.log.dto.response.LlmTraceResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.TimeSeriesResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.TopNResponse;
import com.gijela.morpheus.chat.llm.log.es.EsDesensitizer;
import com.gijela.morpheus.chat.llm.log.es.EsQueryBuilder;
import com.gijela.morpheus.chat.mapper.ChatMessageMapper;
import com.gijela.morpheus.chat.llm.log.service.LlmLogQueryService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedHistogram;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.Percentile;
import org.elasticsearch.search.aggregations.metrics.ParsedPercentiles;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.llm.es.enabled", havingValue = "true")
public class LlmLogQueryServiceImpl implements LlmLogQueryService {

    private final RestHighLevelClient restHighLevelClient;
    private final EsQueryBuilder esQueryBuilder;
    private final EsDesensitizer esDesensitizer;
    private final LlmLogCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final ChatMessageMapper chatMessageMapper;

    public LlmLogQueryServiceImpl(RestHighLevelClient restHighLevelClient,
                                  EsQueryBuilder esQueryBuilder,
                                  EsDesensitizer esDesensitizer,
                                  LlmLogCacheService cacheService,
                                  ObjectMapper objectMapper,
                                  ChatMessageMapper chatMessageMapper) {
        this.restHighLevelClient = restHighLevelClient;
        this.esQueryBuilder = esQueryBuilder;
        this.esDesensitizer = esDesensitizer;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    public TimeSeriesResponse timeseries(TimeSeriesRequest request) {
        boolean bypassCache = shouldBypassTimeseriesCache(request);
        String cacheKey = writeValue(request);
        if (!bypassCache) {
            String cached = cacheService.get("timeseries", cacheKey);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, TimeSeriesResponse.class);
                } catch (IOException ignored) {
                    log.warn("Failed to parse cached timeseries result");
                }
            }
        }
        try {
            SearchResponse response = restHighLevelClient.search(esQueryBuilder.buildTimeSeriesDsl(request), RequestOptions.DEFAULT);
            TimeSeriesResponse result = toTimeSeriesResponse(request, response);
            if (!bypassCache) {
                cacheService.put("timeseries", cacheKey, writeValue(result));
            }
            return result;
        } catch (Exception ex) {
            log.error("Timeseries query failed", ex);
            return TimeSeriesResponse.builder().metric(request.getMetric()).interval(request.getInterval()).buckets(List.of()).totalCount(0L).queryTimeMs(0L).build();
        }
    }

    @Override
    public TopNResponse topN(TopNRequest request) {
        try {
            SearchResponse response = restHighLevelClient.search(esQueryBuilder.buildTopNDsl(request), RequestOptions.DEFAULT);
            Map<String, TopNResponse.Item> deduped = new LinkedHashMap<>();
            int limit = request.getLimit() == null ? 10 : request.getLimit();
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = esDesensitizer.desensitize(hit.getSourceAsMap());
                String traceId = stringValue(source.get("traceId"));
                TopNResponse.Item item = TopNResponse.Item.builder()
                        .traceId(traceId)
                        .sessionId(stringValue(source.get("sessionId")))
                        .modelRoute(stringValue(source.get("modelRoute")))
                        .value(numberValue(source.get(resolveTopField(request.getMetric()))))
                        .extras(source)
                        .build();
                String dedupeKey = (traceId == null || traceId.isBlank())
                        ? "__hit__" + hit.getId()
                        : traceId;
                deduped.putIfAbsent(dedupeKey, item);
                if (deduped.size() >= limit) {
                    break;
                }
            }
            List<TopNResponse.Item> items = new ArrayList<>(deduped.values());
            return TopNResponse.builder().metric(request.getMetric()).items(items).build();
        } catch (Exception ex) {
            log.error("TopN query failed", ex);
            return TopNResponse.builder().metric(request.getMetric()).items(List.of()).build();
        }
    }

    @Override
    public DistributionResponse distribution(DistributionRequest request) {
        try {
            SearchResponse response = restHighLevelClient.search(esQueryBuilder.buildDistributionDsl(request), RequestOptions.DEFAULT);
            ParsedHistogram histogram = response.getAggregations().get("distribution");
            List<DistributionResponse.Bucket> buckets = new ArrayList<>();
            for (Histogram.Bucket bucket : histogram.getBuckets()) {
                double from = ((Number) bucket.getKey()).doubleValue();
                Double to = bucket.getKey() instanceof Number ? from + 1 : null;
                buckets.add(DistributionResponse.Bucket.builder().label(String.valueOf(bucket.getKey())).from(from).to(to).count(bucket.getDocCount()).build());
            }
            return DistributionResponse.builder().metric(request.getMetric()).buckets(buckets).build();
        } catch (Exception ex) {
            log.error("Distribution query failed", ex);
            return DistributionResponse.builder().metric(request.getMetric()).buckets(List.of()).build();
        }
    }

    @Override
    public Page<LlmLogRecordVO> records(LogQueryFilter filter, Pageable pageable) {
        try {
            SearchResponse response = restHighLevelClient.search(esQueryBuilder.buildRecordsDsl(filter, pageable), RequestOptions.DEFAULT);
            List<LlmLogRecordVO> rows = new ArrayList<>();
            Map<String, String> outputTextBySession = new HashMap<>();
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = esDesensitizer.desensitize(hit.getSourceAsMap());
                rows.add(toRecord(source, hit.getIndex(), outputTextBySession));
            }
            return new PageImpl<>(rows, pageable, response.getHits().getTotalHits() == null ? rows.size() : response.getHits().getTotalHits().value);
        } catch (Exception ex) {
            log.error("Records query failed", ex);
            return Page.empty(pageable);
        }
    }

    @Override
    public LlmTraceResponse trace(String traceId) {
        try {
            SearchResponse response = restHighLevelClient.search(esQueryBuilder.buildTraceDsl(traceId), RequestOptions.DEFAULT);
            List<LlmLogRecordVO> spans = new ArrayList<>();
            Map<String, String> outputTextBySession = new HashMap<>();
            for (SearchHit hit : response.getHits().getHits()) {
                spans.add(toRecord(esDesensitizer.desensitize(hit.getSourceAsMap()), hit.getIndex(), outputTextBySession));
            }
            return LlmTraceResponse.builder().traceId(traceId).spans(spans).build();
        } catch (Exception ex) {
            log.error("Trace query failed", ex);
            return LlmTraceResponse.builder().traceId(traceId).spans(List.of()).build();
        }
    }

    @Override
    public ExportLogsResponse export(ExportLogsRequest request) {
        return ExportLogsResponse.builder()
                .downloadUrl("/api/v1/llm-logs/download/mock-export." + (request.getFormat() == null ? "csv" : request.getFormat()))
                .fileName("llm-logs-export." + (request.getFormat() == null ? "csv" : request.getFormat()))
                .build();
    }

    @Override
    public LogClearResponse clearLogs(LogQueryFilter filter) {
        try {
            DeleteByQueryRequest deleteRequest = new DeleteByQueryRequest("llm-runtime-*", "llm-access-*", "llm-audit-*");
            deleteRequest.setQuery(esQueryBuilder.buildDeleteQuery(filter));
            deleteRequest.setConflicts("proceed");
            deleteRequest.setRefresh(true);
            BulkByScrollResponse response = restHighLevelClient.deleteByQuery(deleteRequest, RequestOptions.DEFAULT);
            cacheService.clearAll();
            long deleted = response.getDeleted();
            long failed = response.getBulkFailures() == null ? 0L : response.getBulkFailures().size();
            long searchFailed = response.getSearchFailures() == null ? 0L : response.getSearchFailures().size();
            boolean success = failed == 0L && searchFailed == 0L;
            String message = success
                    ? "日志清理完成"
                    : "日志清理完成，但存在部分失败：bulkFailures=" + failed + ", searchFailures=" + searchFailed;
            return LogClearResponse.builder()
                    .success(success)
                    .deletedCount(deleted)
                    .message(message)
                    .build();
        } catch (Exception ex) {
            log.error("Clear logs failed", ex);
            return LogClearResponse.builder()
                    .success(false)
                    .deletedCount(0L)
                    .message("日志清理失败: " + ex.getMessage())
                    .build();
        }
    }

    private TimeSeriesResponse toTimeSeriesResponse(TimeSeriesRequest request, SearchResponse response) {
        Aggregations aggregations = response.getAggregations();
        if (aggregations == null) {
            return TimeSeriesResponse.builder()
                    .metric(request.getMetric())
                    .interval(request.getInterval())
                    .buckets(List.of())
                    .totalCount(0L)
                    .queryTimeMs(response.getTook().getMillis())
                    .build();
        }
        ParsedDateHistogram histogram = aggregations.get("timeseries");
        if (histogram == null) {
            return TimeSeriesResponse.builder()
                    .metric(request.getMetric())
                    .interval(request.getInterval())
                    .buckets(List.of())
                    .totalCount(0L)
                    .queryTimeMs(response.getTook().getMillis())
                    .build();
        }
        List<TimeSeriesResponse.TimeSeriesBucket> buckets = new ArrayList<>();
        for (Histogram.Bucket bucket : histogram.getBuckets()) {
            long bucketTs = resolveBucketTimestamp(bucket.getKey());
            buckets.add(TimeSeriesResponse.TimeSeriesBucket.builder()
                .timestamp(bucketTs)
                .time(LocalDateTime.ofInstant(Instant.ofEpochMilli(bucketTs), ZoneId.systemDefault()))
                    .value(extractMetricValue(bucket.getAggregations(), request.getMetric(), bucket.getDocCount()))
                    .label(request.getMetric())
                    .build());
        }
        return TimeSeriesResponse.builder()
                .metric(request.getMetric())
                .interval(request.getInterval())
                .buckets(buckets)
                .totalCount((long) buckets.size())
                .queryTimeMs(response.getTook().getMillis())
                .build();
    }

    private double extractMetricValue(Aggregations aggregations, String metric, long docCount) {
        if ("errorRate".equals(metric)) {
            ParsedFilter errorCount = aggregations.get("error_count");
            return docCount == 0 ? 0D : (double) errorCount.getDocCount() * 100D / docCount;
        }
        Aggregation aggregation = aggregations.get("metric_value");
        if (aggregation instanceof NumericMetricsAggregation.SingleValue singleValue) {
            return singleValue.value();
        }
        if (aggregation instanceof ParsedPercentiles percentiles) {
            double target = "p99Latency".equals(metric) ? 99.0 : 95.0;
            for (Percentile percentile : percentiles) {
                if (Double.compare(percentile.getPercent(), target) == 0) {
                    return percentile.getValue();
                }
            }
            return 0D;
        }
        return 0D;
    }

    private LlmLogRecordVO toRecord(Map<String, Object> source,
                                    String indexName,
                                    Map<String, String> outputTextBySession) {
        Integer totalTokens = intValue(source.get("totalTokens"));
        if (totalTokens == null) {
            Integer promptTokens = intValue(source.get("promptTokens"));
            Integer completionTokens = intValue(source.get("completionTokens"));
            if (promptTokens != null || completionTokens != null) {
                totalTokens = (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens);
            }
        }
        String status = stringValue(source.get("status"));
        if (status == null || status.isBlank()) {
            status = stringValue(source.get("operationResult"));
        }
        if (status == null || status.isBlank()) {
            status = stringValue(source.get("toolStatus"));
        }
        String eventType = stringValue(source.get("eventType"));
        if (eventType == null || eventType.isBlank()) {
            eventType = stringValue(source.get("operationType"));
        }
        if ("chat.delta".equals(eventType)) {
            Integer completionTokens = intValue(source.get("completionTokens"));
            if (completionTokens != null && completionTokens > 0) {
                totalTokens = completionTokens;
            }
        }
        String sessionId = stringValue(source.get("sessionId"));
        String outputText = stringValue(source.get("outputText"));
        if ((outputText == null || outputText.isBlank())
                && sessionId != null
                && !sessionId.isBlank()
                && ("access".equals(resolveLogType(indexName)) || "chat.finish".equals(eventType))) {
            outputText = outputTextBySession.computeIfAbsent(sessionId,
                    key -> findLatestAssistantOutput(stringValue(source.get("tenantId")), key));
        }
        return LlmLogRecordVO.builder()
                .traceId(stringValue(source.get("traceId")))
                .sessionId(sessionId)
                .eventType(eventType)
                .logType(resolveLogType(indexName))
                .modelRoute(stringValue(source.get("modelRoute")))
                .status(status)
                .latencyMs(longValue(source.get("latencyMs")))
                .totalTokens(totalTokens)
                .errorCode(stringValue(source.get("errorCode")))
                .outputText(outputText)
                .eventTime(longValue(source.get("eventTime")))
                .build();
    }

    private String findLatestAssistantOutput(String tenantId, String sessionId) {
        if (chatMessageMapper == null || sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try {
            QueryWrapper<ChatMessageEntity> wrapper = new QueryWrapper<ChatMessageEntity>()
                    .eq("session_id", sessionId)
                    .eq("role", "assistant")
                    .orderByDesc("id")
                    .last("limit 1");
            if (tenantId != null && !tenantId.isBlank()) {
                wrapper.eq("tenant_id", tenantId);
            }
            ChatMessageEntity entity = chatMessageMapper.selectOne(wrapper);
            return entity == null ? null : entity.getContent();
        } catch (Exception ex) {
            log.warn("Fallback outputText query failed, sessionId={}, err={}", sessionId, ex.getMessage());
            return null;
        }
    }

    private String resolveLogType(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            return "unknown";
        }
        if (indexName.startsWith("llm-access-")) {
            return "access";
        }
        if (indexName.startsWith("llm-runtime-")) {
            return "runtime";
        }
        if (indexName.startsWith("llm-audit-")) {
            return "audit";
        }
        return "unknown";
    }

    private String resolveTopField(String metric) {
        if (metric == null || metric.isBlank()) {
            return "latencyMs";
        }
        return switch (metric) {
            case "avgLatency", "p95Latency", "p99Latency" -> "latencyMs";
            case "avgTokens" -> "totalTokens";
            case "totalCost" -> "costMicros";
            default -> metric;
        };
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private boolean shouldBypassTimeseriesCache(TimeSeriesRequest request) {
        if (request == null || request.getMetric() == null) {
            return false;
        }
        return switch (request.getMetric()) {
            case "qps", "errorRate", "p95Latency", "p99Latency", "avgFirstToken", "totalTokens" -> true;
            default -> false;
        };
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double numberValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private long resolveBucketTimestamp(Object key) {
        if (key instanceof Number number) {
            return number.longValue();
        }
        if (key instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant().toEpochMilli();
        }
        if (key instanceof java.util.Date date) {
            return date.getTime();
        }
        return 0L;
    }
}
