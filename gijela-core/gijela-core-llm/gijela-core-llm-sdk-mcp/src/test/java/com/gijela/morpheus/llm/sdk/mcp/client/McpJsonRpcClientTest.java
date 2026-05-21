package com.gijela.morpheus.llm.sdk.mcp.client;

import com.gijela.morpheus.llm.sdk.mcp.client.McpClientException;
import com.gijela.morpheus.llm.sdk.mcp.client.McpClientFactory;
import com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig;
import com.gijela.morpheus.llm.sdk.mcp.client.McpInitializeResult;
import com.gijela.morpheus.llm.sdk.mcp.client.McpJsonRpcClient;
import com.gijela.morpheus.llm.sdk.mcp.client.transport.StreamableHttpTransport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpJsonRpcClientTest {
    private MockWebServer server;
    private McpJsonRpcClient client;

    McpJsonRpcClientTest() {
    }

    @BeforeEach
    void setUp() throws Exception {
        this.server = new MockWebServer();
        this.server.start();
        OkHttpClient http = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(2L)).readTimeout(Duration.ofSeconds(3L)).callTimeout(Duration.ofSeconds(5L)).retryOnConnectionFailure(false).build();
        StreamableHttpTransport transport = new StreamableHttpTransport(http);
        this.client = new McpJsonRpcClient(new McpClientFactory(Map.of(transport.name(), transport)));
    }

    @AfterEach
    void tearDown() throws Exception {
        this.server.shutdown();
    }

    private void enqueueJson(String body) {
        this.server.enqueue(new MockResponse().setHeader("Content-Type", (Object)"application/json").setBody(body));
    }

    @Test
    void initialize_shouldParseServerInfo() throws Exception {
        this.enqueueJson("{\"jsonrpc\":\"2.0\",\"id\":1,\n \"result\":{\"protocolVersion\":\"2024-11-05\",\n           \"serverInfo\":{\"name\":\"demo-mcp\",\"version\":\"0.1.0\"},\n           \"capabilities\":{\"tools\":{}}}}\n");
        McpInitializeResult r = this.client.initialize(McpEndpointConfig.http((String)this.server.url("/mcp").toString(), null));
        Assertions.assertEquals((Object)"demo-mcp", (Object)r.serverName());
        Assertions.assertEquals((Object)"0.1.0", (Object)r.serverVersion());
        Assertions.assertEquals((Object)"2024-11-05", (Object)r.protocolVersion());
        Assertions.assertNotNull((Object)r.capabilities());
        RecordedRequest req = this.server.takeRequest();
        Assertions.assertEquals((Object)"POST", (Object)req.getMethod());
        String body = req.getBody().readUtf8();
        Assertions.assertTrue((boolean)body.contains("\"method\":\"initialize\""));
        Assertions.assertTrue((boolean)body.contains("\"clientInfo\""));
    }

    @Test
    void listTools_shouldInitializeThenList() {
        this.enqueueJson("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n");
        this.enqueueJson("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\n    \"tools\":[\n        {\"name\":\"weather\",\"description\":\"get weather\"},\n        {\"name\":\"echo\",\"description\":\"echo back\"}\n    ]\n}}\n");
        List tools = this.client.listTools(McpEndpointConfig.http((String)this.server.url("/mcp").toString(), null));
        Assertions.assertEquals((int)2, (int)tools.size());
        Assertions.assertEquals((Object)"weather", ((Map)tools.get(0)).get("name"));
    }

    @Test
    void bearerToken_shouldBeAttached() throws Exception {
        this.enqueueJson("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n");
        this.client.initialize(McpEndpointConfig.http((String)this.server.url("/mcp").toString(), (String)"secret-token"));
        RecordedRequest req = this.server.takeRequest();
        Assertions.assertEquals((Object)"Bearer secret-token", (Object)req.getHeader("Authorization"));
    }

    @Test
    void serverError_shouldThrow() {
        this.server.enqueue(new MockResponse().setResponseCode(500).setBody("oops"));
        Assertions.assertThrows(McpClientException.class, () -> this.client.initialize(McpEndpointConfig.http((String)this.server.url("/mcp").toString(), null)));
    }

    @Test
    void rpcError_shouldThrow() {
        this.enqueueJson("{\"jsonrpc\":\"2.0\",\"id\":1,\n \"error\":{\"code\":-32601,\"message\":\"Method not found\"}}\n");
        McpClientException ex = (McpClientException)Assertions.assertThrows(McpClientException.class, () -> this.client.initialize(McpEndpointConfig.http((String)this.server.url("/mcp").toString(), null)));
        Assertions.assertTrue((boolean)ex.getMessage().contains("-32601"));
    }

    @Test
    void sseFormattedResponse_shouldBeParsed() {
        this.server.enqueue(new MockResponse().setHeader("Content-Type", (Object)"text/event-stream").setBody("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"serverInfo\":{\"name\":\"sse-mcp\",\"version\":\"1.0\"}}}\n\n"));
        McpInitializeResult r = this.client.initialize(McpEndpointConfig.http((String)this.server.url("/mcp").toString(), null));
        Assertions.assertEquals((Object)"sse-mcp", (Object)r.serverName());
    }

    @Test
    void mcpSessionIdHeader_shouldBeAttachedOnSubsequentCalls() throws Exception {
        this.server.enqueue(new MockResponse().setHeader("Content-Type", (Object)"application/json").setHeader("Mcp-Session-Id", (Object)"sess-xyz").setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}"));
        this.enqueueJson("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[]}}");
        this.client.listTools(McpEndpointConfig.http((String)this.server.url("/mcp").toString(), null));
        this.server.takeRequest();
        RecordedRequest second = this.server.takeRequest();
        Assertions.assertEquals((Object)"sess-xyz", (Object)second.getHeader("Mcp-Session-Id"));
    }

    @Test
    void unknownTransport_shouldThrow() {
        Assertions.assertThrows(McpClientException.class, () -> this.client.initialize(new McpEndpointConfig("nope", "x", null, null, List.of(), Map.of(), null)));
    }
}
