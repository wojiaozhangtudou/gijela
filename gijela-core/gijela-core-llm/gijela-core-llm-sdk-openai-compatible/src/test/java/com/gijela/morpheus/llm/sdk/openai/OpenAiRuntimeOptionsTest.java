package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.client.ToolExecutor;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import com.gijela.morpheus.llm.sdk.observability.BudgetEventPublisher;
import com.gijela.morpheus.llm.sdk.observability.LlmAuditLogger;
import com.gijela.morpheus.llm.sdk.observability.LlmMetricsCollector;
import com.gijela.morpheus.llm.sdk.observability.LlmObservationInterceptor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiRuntimeOptionsTest {

    @Test
    void shouldUseDefaultsWhenMetadataIsNull() {
        OpenAiRuntimeOptions options = OpenAiRuntimeOptions.fromMetadata(null);

        assertNull(options.toolExecutor());
        assertNull(options.toolContext());
        assertNull(options.tenantId());
        assertNull(options.requestId());
        assertTrue(options.autoToolContinue());
        assertNull(options.metricsCollector());
        assertNull(options.auditLogger());
        assertNull(options.observationInterceptor());
        assertNull(options.budgetEventPublisher());
        assertNull(options.budgetEventLevel());
        assertNotNull(options.metadata());
        assertTrue(options.metadata().isEmpty());
        assertNotNull(options.observationOrDefault());
        assertEquals("WARN", options.budgetLevelOrDefault());
    }

    @Test
    void shouldParseTypedFieldsAndBooleanString() {
        ToolExecutor executor = (call, context) -> new ToolResult(call.id(), true, Map.of("ok", true), null);
        ToolContext context = new ToolContext("tenant-A", Map.of("k", "v"));

        LlmMetricsCollector metrics = new LlmMetricsCollector() {
            @Override
            public void recordSuccess(String model, long latencyMs) {
                // no-op
            }

            @Override
            public void recordFailure(String model, String errorCode) {
                // no-op
            }
        };

        LlmAuditLogger audit = (tenantId, requestId, action, result) -> {
            // no-op
        };

        LlmObservationInterceptor observation = new LlmObservationInterceptor();

        BudgetEventPublisher budget = event -> {
            // no-op
        };

        Map<String, Object> metadata = Map.of(
                OpenAiMetadataKeys.TOOL_EXECUTOR, executor,
                OpenAiMetadataKeys.TOOL_CONTEXT, context,
                OpenAiMetadataKeys.TENANT_ID, "tenant-A",
                OpenAiMetadataKeys.REQUEST_ID, "req-100",
                OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, "false",
                OpenAiMetadataKeys.METRICS_COLLECTOR, metrics,
                OpenAiMetadataKeys.AUDIT_LOGGER, audit,
                OpenAiMetadataKeys.OBSERVATION_INTERCEPTOR, observation,
                OpenAiMetadataKeys.BUDGET_EVENT_PUBLISHER, budget,
                OpenAiMetadataKeys.BUDGET_EVENT_LEVEL, "ERROR"
        );

        OpenAiRuntimeOptions options = OpenAiRuntimeOptions.fromMetadata(metadata);

        assertSame(executor, options.toolExecutor());
        assertSame(context, options.toolContext());
        assertEquals("tenant-A", options.tenantId());
        assertEquals("req-100", options.requestId());
        assertFalse(options.autoToolContinue());
        assertSame(metrics, options.metricsCollector());
        assertSame(audit, options.auditLogger());
        assertSame(observation, options.observationInterceptor());
        assertSame(budget, options.budgetEventPublisher());
        assertEquals("ERROR", options.budgetEventLevel());
        assertSame(metadata, options.metadata());
        assertSame(observation, options.observationOrDefault());
        assertEquals("ERROR", options.budgetLevelOrDefault());
    }

    @Test
    void shouldFallbackToWarnWhenBudgetLevelIsBlank() {
        OpenAiRuntimeOptions options = OpenAiRuntimeOptions.fromMetadata(Map.of(
                OpenAiMetadataKeys.BUDGET_EVENT_LEVEL, "  "
        ));

        assertEquals("WARN", options.budgetLevelOrDefault());
    }

    @Test
    void shouldKeepAutoToolContinueTrueWhenMetadataPresentButKeyMissing() {
        ToolContext explicitContext = new ToolContext("tenant-empty-attrs", Map.of());
        OpenAiRuntimeOptions options = OpenAiRuntimeOptions.fromMetadata(Map.of(
                OpenAiMetadataKeys.TOOL_CONTEXT, explicitContext,
                OpenAiMetadataKeys.TENANT_ID, "tenant-fallback"
        ));

        assertTrue(options.autoToolContinue());
        assertNotNull(options.toolContext());
        assertEquals("tenant-empty-attrs", options.toolContext().tenantId());
        assertNotNull(options.toolContext().attributes());
        assertTrue(options.toolContext().attributes().isEmpty());
    }

    @Test
    void shouldTreatInvalidAutoToolContinueStringAsFalse() {
        OpenAiRuntimeOptions options = OpenAiRuntimeOptions.fromMetadata(Map.of(
                OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, "not-bool"
        ));

        assertFalse(options.autoToolContinue());
    }

    @Test
    void shouldTreatWhitespaceWrappedTrueAsFalseWithCurrentParser() {
        OpenAiRuntimeOptions options = OpenAiRuntimeOptions.fromMetadata(Map.of(
                OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, " TRUE "
        ));

        assertFalse(options.autoToolContinue());
    }

    @Test
    void shouldTreatNumericAutoToolContinueValuesAsFalseWithCurrentParser() {
        OpenAiRuntimeOptions optionsOne = OpenAiRuntimeOptions.fromMetadata(Map.of(
                OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, 1
        ));
        OpenAiRuntimeOptions optionsZero = OpenAiRuntimeOptions.fromMetadata(Map.of(
                OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, 0
        ));

        assertFalse(optionsOne.autoToolContinue());
        assertFalse(optionsZero.autoToolContinue());
    }

    @Test
    void shouldFallbackToTrueWhenAutoToolContinueKeyExistsButValueIsNull() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, null);

        OpenAiRuntimeOptions options = OpenAiRuntimeOptions.fromMetadata(metadata);

        assertTrue(options.autoToolContinue());
    }
}
