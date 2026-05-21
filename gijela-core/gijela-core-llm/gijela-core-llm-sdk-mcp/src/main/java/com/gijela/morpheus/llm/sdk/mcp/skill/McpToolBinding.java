package com.gijela.morpheus.llm.sdk.mcp.skill;

import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import java.util.Map;

public record McpToolBinding(String toolName, String serverName, String originalToolName, String description, Map<String, Object> inputSchema, McpEndpointConfig endpointConfig) {
}
