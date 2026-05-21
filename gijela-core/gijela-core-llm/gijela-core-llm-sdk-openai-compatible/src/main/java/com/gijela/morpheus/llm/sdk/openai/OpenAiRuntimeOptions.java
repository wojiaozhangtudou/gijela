package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.client.ToolExecutor;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.observability.BudgetEventPublisher;
import com.gijela.morpheus.llm.sdk.observability.LlmAuditLogger;
import com.gijela.morpheus.llm.sdk.observability.LlmMetricsCollector;
import com.gijela.morpheus.llm.sdk.observability.LlmObservationInterceptor;

import java.util.Map;

/**
 * OpenAI 兼容客户端运行时选项（由 ChatRequest.metadata 解析）。
 */
public record OpenAiRuntimeOptions(
        ToolExecutor toolExecutor,
        ToolContext toolContext,
        String tenantId,
        String requestId,
        boolean autoToolContinue,
        LlmMetricsCollector metricsCollector,
        LlmAuditLogger auditLogger,
        LlmObservationInterceptor observationInterceptor,
        BudgetEventPublisher budgetEventPublisher,
        String budgetEventLevel,
        Map<String, Object> metadata
) {

    public static OpenAiRuntimeOptions fromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return new OpenAiRuntimeOptions(
                    null,
                    null,
                    null,
                    null,
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()
            );
        }

        ToolExecutor toolExecutor = metadata.get(OpenAiMetadataKeys.TOOL_EXECUTOR) instanceof ToolExecutor value ? value : null;
        ToolContext toolContext = metadata.get(OpenAiMetadataKeys.TOOL_CONTEXT) instanceof ToolContext value ? value : null;
        String tenantId = asText(metadata.get(OpenAiMetadataKeys.TENANT_ID));
        String requestId = asText(metadata.get(OpenAiMetadataKeys.REQUEST_ID));

        boolean autoToolContinue = true;
        Object auto = metadata.get(OpenAiMetadataKeys.AUTO_TOOL_CONTINUE);
        if (auto instanceof Boolean b) {
            autoToolContinue = b;
        } else if (auto != null) {
            autoToolContinue = Boolean.parseBoolean(String.valueOf(auto));
        }

        LlmMetricsCollector metricsCollector = metadata.get(OpenAiMetadataKeys.METRICS_COLLECTOR) instanceof LlmMetricsCollector value ? value : null;
        LlmAuditLogger auditLogger = metadata.get(OpenAiMetadataKeys.AUDIT_LOGGER) instanceof LlmAuditLogger value ? value : null;
        LlmObservationInterceptor observationInterceptor = metadata.get(OpenAiMetadataKeys.OBSERVATION_INTERCEPTOR) instanceof LlmObservationInterceptor value ? value : null;
        BudgetEventPublisher budgetEventPublisher = metadata.get(OpenAiMetadataKeys.BUDGET_EVENT_PUBLISHER) instanceof BudgetEventPublisher value ? value : null;
        String budgetEventLevel = asText(metadata.get(OpenAiMetadataKeys.BUDGET_EVENT_LEVEL));

        return new OpenAiRuntimeOptions(
                toolExecutor,
                toolContext,
                tenantId,
                requestId,
                autoToolContinue,
                metricsCollector,
                auditLogger,
                observationInterceptor,
                budgetEventPublisher,
                budgetEventLevel,
                metadata
        );
    }

    public LlmObservationInterceptor observationOrDefault() {
        return observationInterceptor == null ? new LlmObservationInterceptor() : observationInterceptor;
    }

    public String budgetLevelOrDefault() {
        return (budgetEventLevel == null || budgetEventLevel.isBlank()) ? "WARN" : budgetEventLevel;
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
