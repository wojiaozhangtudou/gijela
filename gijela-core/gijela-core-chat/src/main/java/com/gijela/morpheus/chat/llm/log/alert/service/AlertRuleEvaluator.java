package com.gijela.morpheus.chat.llm.log.alert.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chat.llm.log.alert.domain.entity.LlmAlertRule;
import com.gijela.morpheus.chat.llm.log.alert.service.model.AlertEvaluationResult;
import com.gijela.morpheus.chat.llm.log.alert.service.model.MetricBreakdown;
import com.gijela.morpheus.chat.llm.log.dto.request.LogQueryFilter;
import com.gijela.morpheus.chat.llm.log.dto.request.TimeSeriesRequest;
import com.gijela.morpheus.chat.llm.log.dto.request.TopNRequest;
import com.gijela.morpheus.chat.llm.log.dto.response.TimeSeriesResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.TopNResponse;
import com.gijela.morpheus.chat.llm.log.service.LlmLogQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AlertRuleEvaluator {

    private final LlmLogQueryService llmLogQueryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlertRuleEvaluator(LlmLogQueryService llmLogQueryService) {
        this.llmLogQueryService = llmLogQueryService;
    }

    public AlertEvaluationResult evaluate(LlmAlertRule rule) {
        try {
            LogQueryFilter filter = buildFilter(rule);
            TimeSeriesResponse response = llmLogQueryService.timeseries(TimeSeriesRequest.builder()
                    .filter(filter)
                    .metric(resolveMetric(rule.getMetricType()))
                    .interval(resolveInterval(rule.getWindowMinutes()))
                    .build());
            BigDecimal value = resolveMetricValue(response);
            List<MetricBreakdown> breakdown = resolveBreakdown(rule, filter);
            return AlertEvaluationResult.builder()
                    .ruleId(rule.getId())
                    .triggered(compare(value, rule.getCondition(), rule.getThreshold()))
                    .metricValue(value)
                    .breakdown(breakdown)
                    .build();
        } catch (Exception ex) {
            log.error("Failed to evaluate alert rule {}", rule.getId(), ex);
            return AlertEvaluationResult.builder()
                    .ruleId(rule.getId())
                    .triggered(false)
                    .error(ex.getMessage())
                    .metricValue(BigDecimal.ZERO)
                    .breakdown(List.of())
                    .build();
        }
    }

    private LogQueryFilter buildFilter(LlmAlertRule rule) {
        LogQueryFilter filter = new LogQueryFilter();
        filter.setTenantId(rule.getTenantId());
        filter.setStartAt(LocalDateTime.now().minusMinutes(defaultWindow(rule.getWindowMinutes())));
        filter.setEndAt(LocalDateTime.now());
        if (rule.getFilterJson() == null || rule.getFilterJson().isBlank()) {
            return filter;
        }
        try {
            Map<String, Object> values = objectMapper.readValue(rule.getFilterJson(), new TypeReference<>() {
            });
            applyFilter(filter, values, "tenantId", value -> filter.setTenantId(String.valueOf(value)));
            applyFilter(filter, values, "userId", value -> filter.setUserId(String.valueOf(value)));
            applyFilter(filter, values, "modelRoute", value -> filter.setModelRoute(String.valueOf(value)));
            applyFilter(filter, values, "status", value -> filter.setStatus(String.valueOf(value)));
            applyFilter(filter, values, "errorCode", value -> filter.setErrorCode(String.valueOf(value)));
            applyFilter(filter, values, "keyword", value -> filter.setKeyword(String.valueOf(value)));
            applyFilter(filter, values, "minLatency", value -> filter.setMinLatency(asInteger(value)));
            applyFilter(filter, values, "maxLatency", value -> filter.setMaxLatency(asInteger(value)));
            applyFilter(filter, values, "sampled", value -> filter.setSampled(Boolean.parseBoolean(String.valueOf(value))));
        } catch (Exception ex) {
            log.warn("Failed to parse alert filter json, ruleId={}, err={}", rule.getId(), ex.getMessage());
        }
        return filter;
    }

    private List<MetricBreakdown> resolveBreakdown(LlmAlertRule rule, LogQueryFilter filter) {
        try {
            TopNResponse response = llmLogQueryService.topN(TopNRequest.builder()
                    .filter(filter)
                    .metric(resolveMetric(rule.getMetricType()))
                    .limit(5)
                    .sortOrder("desc")
                    .build());
            List<MetricBreakdown> items = new ArrayList<>();
            for (TopNResponse.Item item : response.getItems()) {
                String key = item.getModelRoute();
                if (key == null || key.isBlank()) {
                    key = item.getTraceId();
                }
                items.add(MetricBreakdown.builder()
                        .key(key)
                        .value(item.getValue() == null ? BigDecimal.ZERO : BigDecimal.valueOf(item.getValue()))
                        .build());
            }
            return items;
        } catch (Exception ex) {
            log.warn("Failed to resolve breakdown for rule {}, err={}", rule.getId(), ex.getMessage());
            return List.of();
        }
    }

    private BigDecimal resolveMetricValue(TimeSeriesResponse response) {
        if (response == null || response.getBuckets() == null || response.getBuckets().isEmpty()) {
            return BigDecimal.ZERO;
        }
        double value = response.getBuckets().stream()
                .map(TimeSeriesResponse.TimeSeriesBucket::getValue)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0D);
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private boolean compare(BigDecimal value, String condition, BigDecimal threshold) {
        if (threshold == null) {
            return false;
        }
        int cmp = value.compareTo(threshold);
        return switch (condition == null ? "gt" : condition) {
            case "gt" -> cmp > 0;
            case "lt" -> cmp < 0;
            case "eq" -> cmp == 0;
            case "gte" -> cmp >= 0;
            case "lte" -> cmp <= 0;
            default -> false;
        };
    }

    private Long asInteger(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private void applyFilter(LogQueryFilter filter,
                             Map<String, Object> values,
                             String key,
                             java.util.function.Consumer<Object> consumer) {
        Object value = values.get(key);
        if (value != null) {
            consumer.accept(value);
        }
    }

    private Integer defaultWindow(Integer windowMinutes) {
        return windowMinutes == null || windowMinutes <= 0 ? 5 : windowMinutes;
    }

    private String resolveMetric(String metricType) {
        if (metricType == null || metricType.isBlank()) {
            return "qps";
        }
        return switch (metricType) {
            case "firstTokenMs" -> "avgLatency";
            case "toolErrorRate", "esIngestFailure" -> "errorRate";
            default -> metricType;
        };
    }

    private String resolveInterval(Integer windowMinutes) {
        int minutes = defaultWindow(windowMinutes);
        return minutes >= 60 ? "1h" : "5m";
    }
}
