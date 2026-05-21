package com.gijela.morpheus.llm.sdk.core.tool;

import java.util.Map;

/**
 * 工具调用请求。
 */
public record ToolCall(String id, String name, Map<String, Object> arguments) {
}
