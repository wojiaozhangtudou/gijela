package com.gijela.morpheus.llm.sdk.mcp.client;

import java.util.Map;

public record McpInitializeResult(String serverName, String serverVersion, String protocolVersion, Map<String, Object> capabilities) {
}
