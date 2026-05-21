package com.gijela.morpheus.llm.sdk.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpRpcCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String CLIENT_NAME = "gijela-chat";
    public static final String CLIENT_VERSION = "1.0.0";

    private McpRpcCodec() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String request(long id, String method, Map<String, Object> params) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        body.put("params", params == null ? Map.of() : params);
        try {
            return MAPPER.writeValueAsString(body);
        }
        catch (Exception e) {
            throw new McpClientException("MCP \u8bf7\u6c42\u5e8f\u5217\u5316\u5931\u8d25: " + e.getMessage(), e);
        }
    }

    public static String notification(String method, Map<String, Object> params) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        body.put("params", params == null ? Map.of() : params);
        try {
            return MAPPER.writeValueAsString(body);
        }
        catch (Exception e) {
            throw new McpClientException("MCP \u901a\u77e5\u5e8f\u5217\u5316\u5931\u8d25: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> initializeParams() {
        return Map.of("protocolVersion", PROTOCOL_VERSION, "capabilities", Map.of(), "clientInfo", Map.of("name", CLIENT_NAME, "version", CLIENT_VERSION));
    }

    public static JsonNode parseResponse(String body) {
        if (body == null || body.isBlank()) {
            throw new McpClientException("MCP \u54cd\u5e94\u4f53\u4e3a\u7a7a");
        }
        try {
            JsonNode root = MAPPER.readTree(body.trim());
            JsonNode error = root.get("error");
            if (error != null && !error.isNull()) {
                int code = error.path("code").asInt(-1);
                String msg = error.path("message").asText("unknown");
                throw new McpClientException("MCP \u534f\u8bae\u9519\u8bef code=" + code + ", msg=" + msg);
            }
            return root.path("result");
        }
        catch (McpClientException e) {
            throw e;
        }
        catch (Exception e) {
            throw new McpClientException("MCP \u54cd\u5e94\u89e3\u6790\u5931\u8d25: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        try {
            Map m;
            Object o = MAPPER.convertValue((Object)node, Object.class);
            return o instanceof Map ? (m = (Map)o) : Map.of();
        }
        catch (Exception e) {
            return Map.of();
        }
    }

    public static List<Map<String, Object>> toListOfMap(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        try {
            return (List)MAPPER.convertValue((Object)node, (TypeReference)new TypeReference<List<Map<String, Object>>>(){});
        }
        catch (Exception e) {
            return List.of();
        }
    }

    public static String textAt(JsonNode root, String ... path) {
        JsonNode node = root;
        for (String p : path) {
            if (node == null) {
                return null;
            }
            node = node.path(p);
        }
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
