package com.gijela.morpheus.llm.sdk.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.gijela.morpheus.llm.sdk.mcp.client.McpClientFactory;
import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpInitializeResult;
import com.gijela.morpheus.llm.sdk.mcp.client.McpRpcCodec;
import com.gijela.morpheus.llm.sdk.mcp.client.McpSession;
import java.util.List;
import java.util.Map;

public class McpJsonRpcClient {
    private final McpClientFactory factory;

    public McpJsonRpcClient(McpClientFactory factory) {
        this.factory = factory;
    }

    public McpInitializeResult initialize(McpEndpointConfig config) {
        try (McpSession session = this.factory.open(config);){
            JsonNode result = session.call("initialize", McpRpcCodec.initializeParams());
            String serverName = McpRpcCodec.textAt(result, "serverInfo", "name");
            String serverVersion = McpRpcCodec.textAt(result, "serverInfo", "version");
            String protocolVersion = result.path("protocolVersion").asText("2024-11-05");
            Map<String, Object> capabilities = McpRpcCodec.toMap(result.path("capabilities"));
            McpInitializeResult mcpInitializeResult = new McpInitializeResult(serverName, serverVersion, protocolVersion, capabilities);
            return mcpInitializeResult;
        }
    }

    public List<Map<String, Object>> listTools(McpEndpointConfig config) {
        try (McpSession session = this.factory.open(config);){
            session.call("initialize", McpRpcCodec.initializeParams());
            JsonNode result = session.call("tools/list", Map.of());
            List<Map<String, Object>> list = McpRpcCodec.toListOfMap(result.path("tools"));
            return list;
        }
    }

    public Map<String, Object> callTool(McpEndpointConfig config, String toolName, Map<String, Object> arguments) {
        try (McpSession session = this.factory.open(config);){
            session.call("initialize", McpRpcCodec.initializeParams());
            JsonNode result = session.call("tools/call", Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments));
            Map<String, Object> map = McpRpcCodec.toMap(result);
            return map;
        }
    }
}
