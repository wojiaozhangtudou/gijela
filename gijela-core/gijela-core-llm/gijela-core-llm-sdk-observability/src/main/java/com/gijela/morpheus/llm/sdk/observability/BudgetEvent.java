package com.gijela.morpheus.llm.sdk.observability;

/**
 * 预算事件。
 */
public record BudgetEvent(String tenantId, String level, String message) {
}
