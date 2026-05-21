package com.gijela.morpheus.llm.sdk.core.tool;

import java.util.Map;

/**
 * 工具调用结果。
 */
public record ToolResult(String toolCallId, boolean success, Map<String, Object> result, String errorMessage) {
}
