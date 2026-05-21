package com.gijela.morpheus.llm.sdk.observability;

/**
 * 审计日志抽象。
 */
public interface LlmAuditLogger {

    void record(String tenantId, String requestId, String action, String result);
}
