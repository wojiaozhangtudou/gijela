package com.gijela.morpheus.llm.sdk.openai;

/**
 * OpenAI 兼容客户端 metadata 键常量。
 */
public final class OpenAiMetadataKeys {

    public static final String TOOLS = "tools";
    public static final String TOOL_CHOICE = "tool_choice";

    public static final String TOOL_EXECUTOR = "toolExecutor";
    public static final String TOOL_CONTEXT = "toolContext";
    public static final String TENANT_ID = "tenantId";
    public static final String REQUEST_ID = "requestId";
    public static final String AUTO_TOOL_CONTINUE = "autoToolContinue";

    public static final String METRICS_COLLECTOR = "metricsCollector";
    public static final String AUDIT_LOGGER = "auditLogger";
    public static final String OBSERVATION_INTERCEPTOR = "observationInterceptor";

    public static final String BUDGET_EVENT_PUBLISHER = "budgetEventPublisher";
    public static final String BUDGET_EVENT_LEVEL = "budgetEventLevel";

    private OpenAiMetadataKeys() {
        // utility class
    }
}
