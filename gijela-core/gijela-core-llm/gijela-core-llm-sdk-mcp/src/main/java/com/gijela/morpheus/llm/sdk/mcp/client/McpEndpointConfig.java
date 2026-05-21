package com.gijela.morpheus.llm.sdk.mcp.client;

import java.util.List;
import java.util.Map;

public record McpEndpointConfig(String transport, String endpoint, String bearerToken, String command, List<String> args, Map<String, String> env, String workingDir) {
    public static McpEndpointConfig http(String endpoint, String bearerToken) {
        return new McpEndpointConfig("streamable_http", endpoint, bearerToken, null, List.of(), Map.of(), null);
    }

    public static McpEndpointConfig sse(String endpoint, String bearerToken) {
        return new McpEndpointConfig("sse", endpoint, bearerToken, null, List.of(), Map.of(), null);
    }

    public static McpEndpointConfig stdio(String command, List<String> args, Map<String, String> env, String workingDir) {
        return new McpEndpointConfig("stdio", null, null, command, args == null ? List.of() : args, env == null ? Map.of() : env, workingDir);
    }
}
