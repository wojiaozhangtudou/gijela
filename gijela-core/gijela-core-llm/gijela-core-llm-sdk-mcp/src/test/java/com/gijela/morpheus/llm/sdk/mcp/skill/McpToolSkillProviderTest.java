package com.gijela.morpheus.llm.sdk.mcp.skill;

import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpJsonRpcClient;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBinding;
import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolSkillProvider;
import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import com.gijela.morpheus.llm.sdk.skill.SkillSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class McpToolSkillProviderTest {
    McpToolSkillProviderTest() {
    }

    private static McpEndpointConfig endpoint() {
        return McpEndpointConfig.http((String)"http://localhost:9999/mcp", null);
    }

    private static McpToolBinding binding(String fqName, String original, Map<String, Object> inputSchema) {
        return new McpToolBinding(fqName, "demo", original, "demo description", inputSchema, McpToolSkillProviderTest.endpoint());
    }

    private static McpToolBinding binding(String serverName, String fqName, String original, Map<String, Object> inputSchema) {
        return new McpToolBinding(fqName, serverName, original, "demo description", inputSchema, McpToolSkillProviderTest.endpoint());
    }

    @Test
    void schema_shouldExposeFunctionTypeWithNormalizedParameters() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        LinkedHashMap<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("properties", Map.of("city", Map.of("type", "string")));
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("mcp__demo__weather", "weather", raw), client, 25000);
        Map schema = p.schema();
        Assertions.assertEquals((Object)"function", schema.get("type"));
        Map fn = (Map)schema.get("function");
        Assertions.assertEquals((Object)"mcp__demo__weather", fn.get("name"));
        Assertions.assertTrue((boolean)((String)fn.get("description")).startsWith("[MCP:demo] "));
        Map params = (Map)fn.get("parameters");
        Assertions.assertEquals((Object)"object", params.get("type"));
        Assertions.assertNotNull(params.get("properties"));
        Assertions.assertTrue((boolean)(params.get("required") instanceof List));
    }

    @Test
    void schema_shouldHandleNullOrEmptyInputSchema() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("mcp__demo__noop", "noop", Map.of()), client, 25000);
        Map params = (Map)((Map)p.schema().get("function")).get("parameters");
        Assertions.assertEquals((Object)"object", params.get("type"));
        Assertions.assertEquals(Map.of(), params.get("properties"));
        Assertions.assertEquals(List.of(), params.get("required"));
    }

    @Test
    void execute_shouldDispatchToOriginalToolNameWithArguments() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        Mockito.when((Object)client.callTool((McpEndpointConfig)ArgumentMatchers.any(), (String)ArgumentMatchers.eq((Object)"weather"), (Map)ArgumentMatchers.any())).thenReturn(Map.of("temperature", 21));
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("mcp__demo__weather", "weather", Map.of()), client, 25000);
        ToolResult r = p.execute(new ToolCall("call-1", "mcp__demo__weather", Map.of("city", "Beijing")), new ToolContext("tenant-a", Map.of()));
        Assertions.assertTrue((boolean)r.success());
        Assertions.assertEquals((Object)"call-1", (Object)r.toolCallId());
        Assertions.assertEquals((Object)21, r.result().get("temperature"));
        ArgumentCaptor argsCap = ArgumentCaptor.forClass(Map.class);
        ((McpJsonRpcClient)Mockito.verify((Object)client)).callTool((McpEndpointConfig)ArgumentMatchers.any(McpEndpointConfig.class), (String)ArgumentMatchers.eq((Object)"weather"), (Map)argsCap.capture());
        Assertions.assertEquals((Object)"Beijing", ((Map)argsCap.getValue()).get("city"));
    }

    @Test
    void execute_shouldNormalizeBaiduWeatherLocationToLngLat() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        Mockito.when((Object)client.callTool((McpEndpointConfig)ArgumentMatchers.any(), (String)ArgumentMatchers.eq((Object)"map_weather"), (Map)ArgumentMatchers.any())).thenReturn(Map.of("status", "ok"));
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("baidu-map", "mcp__baidu-map__map_weather", "map_weather", Map.of()), client, 25000);

        ToolResult r = p.execute(new ToolCall("call-2", "mcp__baidu-map__map_weather", Map.of("location", "41.7075,123.4397")), new ToolContext("tenant-a", Map.of()));

        Assertions.assertTrue((boolean)r.success());
        ArgumentCaptor argsCap = ArgumentCaptor.forClass(Map.class);
        ((McpJsonRpcClient)Mockito.verify((Object)client)).callTool((McpEndpointConfig)ArgumentMatchers.any(McpEndpointConfig.class), (String)ArgumentMatchers.eq((Object)"map_weather"), (Map)argsCap.capture());
        Assertions.assertEquals((Object)"123.4397,41.7075", ((Map)argsCap.getValue()).get("location"));
    }

    @Test
    void execute_shouldKeepBaiduWeatherLocationWhenAlreadyLngLat() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        Mockito.when((Object)client.callTool((McpEndpointConfig)ArgumentMatchers.any(), (String)ArgumentMatchers.eq((Object)"map_weather"), (Map)ArgumentMatchers.any())).thenReturn(Map.of("status", "ok"));
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("baidu-map", "mcp__baidu-map__map_weather", "map_weather", Map.of()), client, 25000);

        ToolResult r = p.execute(new ToolCall("call-3", "mcp__baidu-map__map_weather", Map.of("location", "123.4397,41.7075")), new ToolContext("tenant-a", Map.of()));

        Assertions.assertTrue((boolean)r.success());
        ArgumentCaptor argsCap = ArgumentCaptor.forClass(Map.class);
        ((McpJsonRpcClient)Mockito.verify((Object)client)).callTool((McpEndpointConfig)ArgumentMatchers.any(McpEndpointConfig.class), (String)ArgumentMatchers.eq((Object)"map_weather"), (Map)argsCap.capture());
        Assertions.assertEquals((Object)"123.4397,41.7075", ((Map)argsCap.getValue()).get("location"));
    }

    @Test
    void execute_shouldReturnSoftFailureOnMcpClientException() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        Mockito.when((Object)client.callTool((McpEndpointConfig)ArgumentMatchers.any(), (String)ArgumentMatchers.eq((Object)"boom"), (Map)ArgumentMatchers.any())).thenThrow(new Throwable[]{new McpClientException("upstream timeout")});
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("mcp__demo__boom", "boom", Map.of()), client, 25000);
        ToolResult r = p.execute(new ToolCall("call-x", "mcp__demo__boom", Map.of()), new ToolContext("t", Map.of()));
        Assertions.assertFalse((boolean)r.success());
        Assertions.assertNotNull((Object)r.errorMessage());
        Assertions.assertTrue((boolean)r.errorMessage().contains("MCP \u5de5\u5177\u8c03\u7528\u5931\u8d25"));
        Assertions.assertTrue((boolean)r.errorMessage().contains("upstream timeout"));
    }

    @Test
    void execute_shouldReturnSoftFailureOnUnexpectedException() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        Mockito.when((Object)client.callTool((McpEndpointConfig)ArgumentMatchers.any(), (String)ArgumentMatchers.eq((Object)"npe"), (Map)ArgumentMatchers.any())).thenThrow(new Throwable[]{new IllegalStateException("oops")});
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("mcp__demo__npe", "npe", Map.of()), client, 25000);
        ToolResult r = p.execute(new ToolCall("c", "mcp__demo__npe", Map.of()), new ToolContext("t", Map.of()));
        Assertions.assertFalse((boolean)r.success());
        Assertions.assertTrue((boolean)r.errorMessage().contains("MCP \u5de5\u5177\u5f02\u5e38"));
    }

    @Test
    void execute_shouldClipLargeStringFields() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        String huge = "x".repeat(50000);
        Mockito.when((Object)client.callTool((McpEndpointConfig)ArgumentMatchers.any(), (String)ArgumentMatchers.eq((Object)"dump"), (Map)ArgumentMatchers.any())).thenReturn(Map.of("payload", huge, "meta", "ok"));
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("mcp__demo__dump", "dump", Map.of()), client, 2048);
        ToolResult r = p.execute(new ToolCall("c", "mcp__demo__dump", Map.of()), new ToolContext("t", Map.of()));
        Assertions.assertTrue((boolean)r.success());
        Assertions.assertEquals((Object)Boolean.TRUE, r.result().get("_truncated"));
        Assertions.assertNotNull(r.result().get("_originalChars"));
        Assertions.assertTrue((((Number)r.result().get("_originalChars")).intValue() >= 50000 ? 1 : 0) != 0);
        String payload = (String)r.result().get("payload");
        Assertions.assertTrue((boolean)payload.endsWith("...[truncated]"));
        Assertions.assertTrue((payload.length() < huge.length() ? 1 : 0) != 0);
        Assertions.assertEquals((Object)"ok", r.result().get("meta"));
    }

    @Test
    void execute_shouldNotClipSmallResponse() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        Map<String, Object> small = Map.of("a", 1, "b", "hello");
        Mockito.when((Object)client.callTool((McpEndpointConfig)ArgumentMatchers.any(), (String)ArgumentMatchers.eq((Object)"small"), (Map)ArgumentMatchers.any())).thenReturn(small);
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("mcp__demo__small", "small", Map.of()), client, 25000);
        ToolResult r = p.execute(new ToolCall("c", "mcp__demo__small", Map.of()), new ToolContext("t", Map.of()));
        Assertions.assertTrue((boolean)r.success());
        Assertions.assertSame(small, (Object)r.result());
    }

    @Test
    void metadata_shouldReportMcpSourceAndNonBuiltin() {
        McpJsonRpcClient client = (McpJsonRpcClient)Mockito.mock(McpJsonRpcClient.class);
        McpToolSkillProvider p = new McpToolSkillProvider(McpToolSkillProviderTest.binding("mcp__demo__x", "x", Map.of()), client, 25000);
        Assertions.assertEquals((Object)SkillSource.MCP, (Object)p.source());
        Assertions.assertFalse((boolean)p.builtin());
        Assertions.assertEquals((Object)"mcp__demo__x", (Object)p.name());
    }
}
