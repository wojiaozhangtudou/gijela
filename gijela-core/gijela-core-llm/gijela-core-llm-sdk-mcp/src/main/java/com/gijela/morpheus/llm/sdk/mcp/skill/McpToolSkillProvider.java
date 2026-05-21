package com.gijela.morpheus.llm.sdk.mcp.skill;

import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import com.gijela.morpheus.llm.sdk.mcp.client.McpJsonRpcClient;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBinding;
import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import com.gijela.morpheus.llm.sdk.skill.SkillProvider;
import com.gijela.morpheus.llm.sdk.skill.SkillSource;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpToolSkillProvider
implements SkillProvider {
    private static final Logger log = LoggerFactory.getLogger(McpToolSkillProvider.class);
    private final McpToolBinding binding;
    private final McpJsonRpcClient client;
    private final int maxResponseChars;

    public McpToolSkillProvider(McpToolBinding binding, McpJsonRpcClient client, int maxResponseChars) {
        this.binding = binding;
        this.client = client;
        this.maxResponseChars = Math.max(1024, maxResponseChars);
    }

    public String name() {
        return this.binding.toolName();
    }

    public Map<String, Object> schema() {
        LinkedHashMap<String, Object> function = new LinkedHashMap<String, Object>();
        function.put("name", this.binding.toolName());
        function.put("description", this.buildDescription());
        function.put("parameters", McpToolSkillProvider.normalizeSchema(this.binding.inputSchema()));
        return Map.of("type", "function", "function", function);
    }

    public ToolResult execute(ToolCall call, ToolContext context) {
        try {
            Map<String, Object> normalizedArgs = this.normalizeArguments(call.arguments());
            Map<String, Object> result = this.client.callTool(this.binding.endpointConfig(), this.binding.originalToolName(), normalizedArgs);
            Map<String, Object> compact = this.clip(result);
            return new ToolResult(call.id(), true, compact, null);
        }
        catch (McpClientException e) {
            log.warn("[mcp-skill] call failed: tool={}, server={}, err={}", new Object[]{this.binding.toolName(), this.binding.serverName(), e.getMessage()});
            return new ToolResult(call.id(), false, Map.of(), "MCP \u5de5\u5177\u8c03\u7528\u5931\u8d25: " + e.getMessage());
        }
        catch (Exception e) {
            log.warn("[mcp-skill] unexpected: tool={}, server={}, err={}", new Object[]{this.binding.toolName(), this.binding.serverName(), e.getMessage()});
            return new ToolResult(call.id(), false, Map.of(), "MCP \u5de5\u5177\u5f02\u5e38: " + e.getMessage());
        }
    }

    public SkillSource source() {
        return SkillSource.MCP;
    }

    public boolean builtin() {
        return false;
    }

    public McpToolBinding binding() {
        return this.binding;
    }

    private String buildDescription() {
        String d = this.binding.description();
        if (d == null || d.isBlank()) {
            d = this.binding.originalToolName();
        }
        return "[MCP:" + this.binding.serverName() + "] " + d;
    }

    private Map<String, Object> normalizeArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<String, Object>(arguments);
        normalizeBaiduWeatherLocation(normalized);
        return normalized;
    }

    private void normalizeBaiduWeatherLocation(Map<String, Object> arguments) {
        if (!"baidu-map".equalsIgnoreCase(this.binding.serverName())
                || !"map_weather".equalsIgnoreCase(this.binding.originalToolName())) {
            return;
        }
        Object rawLocation = arguments.get("location");
        if (!(rawLocation instanceof String location) || location.isBlank()) {
            return;
        }
        String[] parts = location.split(",");
        if (parts.length != 2) {
            return;
        }
        BigDecimal first = parseCoordinate(parts[0]);
        BigDecimal second = parseCoordinate(parts[1]);
        if (first == null || second == null) {
            return;
        }
        double firstValue = first.doubleValue();
        double secondValue = second.doubleValue();
        if (Math.abs(firstValue) <= 90 && Math.abs(secondValue) > 90 && Math.abs(secondValue) <= 180) {
            String swapped = formatCoordinate(second) + "," + formatCoordinate(first);
            arguments.put("location", swapped);
            log.info("[mcp-skill] normalized baidu weather location from lat,lng to lng,lat: {} -> {}",
                    location, swapped);
        }
    }

    private static BigDecimal parseCoordinate(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String formatCoordinate(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static Map<String, Object> normalizeSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return Map.of("type", "object", "properties", Map.of(), "required", List.of());
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>(schema);
        copy.putIfAbsent("type", "object");
        copy.putIfAbsent("properties", Map.of());
        Object required = copy.get("required");
        if (!(required instanceof List)) {
            copy.put("required", List.of());
        }
        return copy;
    }

    private Map<String, Object> clip(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return Map.of();
        }
        String preview = String.valueOf(result);
        if (preview.length() <= this.maxResponseChars) {
            return result;
        }
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>(result);
        out.put("_truncated", true);
        out.put("_originalChars", preview.length());
        for (Map.Entry e : out.entrySet()) {
            String s;
            Object v = e.getValue();
            if (!(v instanceof String) || (s = (String)v).length() <= this.maxResponseChars / 2) continue;
            out.put((String)e.getKey(), s.substring(0, this.maxResponseChars / 2) + "...[truncated]");
        }
        return out;
    }
}
