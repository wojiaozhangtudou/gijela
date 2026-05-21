package com.gijela.morpheus.llm.sdk.core.tool;

import java.util.Map;

/**
 * 工具执行上下文。
 */
public record ToolContext(String tenantId, Map<String, Object> attributes) {
}
