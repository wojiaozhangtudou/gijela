package com.gijela.morpheus.chat.llm.log.es;

import com.gijela.morpheus.chat.llm.log.dto.request.DistributionRequest;
import com.gijela.morpheus.chat.llm.log.dto.request.LogQueryFilter;
import com.gijela.morpheus.chat.llm.log.dto.request.TimeSeriesRequest;
import com.gijela.morpheus.chat.llm.log.dto.request.TopNRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.collapse.CollapseBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class EsQueryBuilder {

    public SearchRequest buildTimeSeriesDsl(TimeSeriesRequest request) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(0);
        sourceBuilder.query(buildFilterQuery(request.getFilter()));

        var histogram = AggregationBuilders.dateHistogram("timeseries")
                .field("eventTime")
                .fixedInterval(resolveInterval(request.getInterval()));
        appendMetricAggregation(histogram, request.getMetric());
        sourceBuilder.aggregation(histogram);
        return new SearchRequest(resolveIndexByMetric(request.getMetric())).source(sourceBuilder);
    }

    public SearchRequest buildTopNDsl(TopNRequest request) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(buildFilterQuery(request.getFilter()));
        int limit = request.getLimit() == null ? 10 : request.getLimit();
        int fetchSize = Math.min(Math.max(limit, 1), 200);
        sourceBuilder.size(fetchSize);
        sourceBuilder.sort(resolveMetricField(request.getMetric()), resolveSortOrder(request.getSortOrder()));
        sourceBuilder.collapse(new CollapseBuilder("traceId.keyword"));
        return new SearchRequest(resolveIndexByMetric(request.getMetric())).source(sourceBuilder);
    }

    public SearchRequest buildDistributionDsl(DistributionRequest request) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(0);
        sourceBuilder.query(buildFilterQuery(request.getFilter()));
        sourceBuilder.aggregation(AggregationBuilders.histogram("distribution")
                .field(resolveMetricField(request.getMetric()))
                .interval(resolveHistogramInterval(request.getMetric(), request.getBuckets())));
        return new SearchRequest(resolveIndexByMetric(request.getMetric())).source(sourceBuilder);
    }

    public SearchRequest buildRecordsDsl(LogQueryFilter filter, Pageable pageable) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(buildFilterQuery(filter));
        sourceBuilder.from((int) pageable.getOffset());
        sourceBuilder.size(pageable.getPageSize());
        sourceBuilder.sort("eventTime", SortOrder.DESC);
        return new SearchRequest("llm-access-*").source(sourceBuilder);
    }

    public SearchRequest buildTraceDsl(String traceId) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(exactMatchQuery("traceId", traceId));
        sourceBuilder.size(200);
        sourceBuilder.sort("eventTime", SortOrder.ASC);
        return new SearchRequest("llm-runtime-*", "llm-access-*", "llm-audit-*").source(sourceBuilder);
    }

    public QueryBuilder buildDeleteQuery(LogQueryFilter filter) {
        return buildFilterQuery(filter);
    }

    private BoolQueryBuilder buildFilterQuery(LogQueryFilter filter) {
        BoolQueryBuilder query = QueryBuilders.boolQuery();
        if (filter == null) {
            return query;
        }
        if (filter.getStartAt() != null || filter.getEndAt() != null) {
            var range = QueryBuilders.rangeQuery("eventTime");
            if (filter.getStartAt() != null) {
                range.gte(filter.getStartAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            if (filter.getEndAt() != null) {
                range.lte(filter.getEndAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            query.filter(range);
        }
        termFilter(query, "tenantId", filter.getTenantId());
        termFilter(query, "userId", filter.getUserId());
        termFilter(query, "modelRoute", filter.getModelRoute());
        termFilter(query, "status", filter.getStatus());
        termFilter(query, "errorCode", filter.getErrorCode());
        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            query.must(QueryBuilders.multiMatchQuery(filter.getKeyword(), "promptPreview", "errorMsg", "outputText"));
        }
        if (filter.getMinLatency() != null || filter.getMaxLatency() != null) {
            var range = QueryBuilders.rangeQuery("latencyMs");
            if (filter.getMinLatency() != null) {
                range.gte(filter.getMinLatency());
            }
            if (filter.getMaxLatency() != null) {
                range.lte(filter.getMaxLatency());
            }
            query.filter(range);
        }
        if (filter.getSampled() != null) {
            query.filter(QueryBuilders.termQuery("sampled", filter.getSampled()));
        }
        return query;
    }

    private void termFilter(BoolQueryBuilder query, String field, String value) {
        if (value != null && !value.isBlank()) {
            query.filter(exactMatchQuery(field, value));
        }
    }

    private QueryBuilder exactMatchQuery(String field, String value) {
        return QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(keywordField(field), value))
                .should(QueryBuilders.termQuery(field, value))
                .minimumShouldMatch(1);
    }

    private String keywordField(String field) {
        if (field == null || field.endsWith(".keyword")) {
            return field;
        }
        return field + ".keyword";
    }

    private void appendMetricAggregation(org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder histogram,
                                         String metric) {
        String field = resolveMetricField(metric);
        switch (metric) {
            case "qps" -> histogram.subAggregation(AggregationBuilders.count("metric_value").field("eventTime"));
            case "errorRate" -> {
                histogram.subAggregation(AggregationBuilders.count("total_count").field("eventTime"));
                histogram.subAggregation(AggregationBuilders.filter("error_count", QueryBuilders.boolQuery().mustNot(exactMatchQuery("status", "SUCCESS"))));
            }
            case "p95Latency" -> histogram.subAggregation(AggregationBuilders.percentiles("metric_value").field(field).percentiles(95.0));
            case "p99Latency" -> histogram.subAggregation(AggregationBuilders.percentiles("metric_value").field(field).percentiles(99.0));
            case "totalTokens" -> histogram.subAggregation(AggregationBuilders.sum("metric_value").field(field));
            default -> histogram.subAggregation(AggregationBuilders.avg("metric_value").field(field));
        }
    }

    private DateHistogramInterval resolveInterval(String interval) {
        return switch (interval == null ? "5m" : interval) {
            case "1m" -> DateHistogramInterval.minutes(1);
            case "15m" -> DateHistogramInterval.minutes(15);
            case "1h" -> DateHistogramInterval.hours(1);
            case "1d" -> DateHistogramInterval.days(1);
            default -> DateHistogramInterval.minutes(5);
        };
    }

    private SortOrder resolveSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? SortOrder.ASC : SortOrder.DESC;
    }

    private double resolveHistogramInterval(String metric, Integer buckets) {
        if ("latencyMs".equals(metric) || "p95Latency".equals(metric)) {
            return 200D;
        }
        if ("totalTokens".equals(metric)) {
            return 500D;
        }
        return buckets == null || buckets <= 0 ? 10D : 100D / buckets;
    }

    private String resolveMetricField(String metric) {
        return switch (metric == null ? "latencyMs" : metric) {
            case "avgLatency", "p95Latency", "p99Latency", "latencyMs" -> "latencyMs";
            case "avgFirstToken", "firstTokenMs" -> "firstTokenMs";
            case "avgTokens", "totalTokens" -> "totalTokens";
            case "totalCost", "costMicros" -> "costMicros";
            case "errorRate" -> "status";
            case "qps" -> "traceId";
            default -> metric;
        };
    }

    private String[] resolveIndexByMetric(String metric) {
        if ("qps".equals(metric) || "errorRate".equals(metric)) {
            return new String[]{"llm-access-*", "llm-runtime-*"};
        }
        return new String[]{"llm-runtime-*", "llm-access-*"};
    }
}
