package com.gijela.morpheus.chat.adapter.mcp;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.entity.ChatModelConfig;
import com.gijela.morpheus.chat.mapper.ChatModelConfigMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeSearchGatewayTest {

    private MockWebServer mockWebServer;
    private KnowledgeSearchGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        ChatModuleProperties properties = new ChatModuleProperties();
        // Qdrant endpoint
        properties.getRetrieval().setEndpoint(baseUrl);
        properties.getRetrieval().setCollection("chat_knowledge");
        properties.getRetrieval().setTopK(3);
                // Embedding 模型配置（改为 DB 配置来源）
                ChatModelConfigMapper modelConfigMapper = mock(ChatModelConfigMapper.class);
                ChatModelConfig config = new ChatModelConfig();
                config.setTenantId("default");
                config.setConfigType("EMBEDDING");
                config.setProviderKey("mock");
                config.setModel("nomic-embed-text");
                config.setBaseUrl(baseUrl);
                config.setApiKey("");
                config.setConnectTimeoutSeconds(10);
                config.setReadTimeoutSeconds(30);
                config.setCallTimeoutSeconds(60);
                config.setEnabled(1);
                when(modelConfigMapper.selectOne(any())).thenReturn(config);

                gateway = new KnowledgeSearchGateway(properties, new OkHttpClient(), modelConfigMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void search_shouldEmbedThenCallQdrantAndMapHits() throws Exception {
        // 第一个请求：embedding 接口
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}")
                .addHeader("Content-Type", "application/json"));
        // 第二个请求：Qdrant /points/search 接口
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\",\"result\":[{\"id\":\"p1\",\"score\":0.91,\"payload\":{\"title\":\"知识片段\",\"content\":\"命中内容\"}}]}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = gateway.search("tenant-a", "权限模型");

        assertEquals("qdrant", result.get("provider"));
        List<?> hits = (List<?>) result.get("hits");
        assertEquals(1, hits.size());

        // 验证 embedding 请求
        RecordedRequest embReq = mockWebServer.takeRequest();
        assertEquals("POST", embReq.getMethod());
        assertTrue(embReq.getPath().endsWith("/embeddings"));
        String embBody = embReq.getBody().readUtf8();
        assertTrue(embBody.contains("nomic-embed-text"));
        assertTrue(embBody.contains("权限模型"));

        // 验证 Qdrant /points/search 请求
        RecordedRequest qdrantReq = mockWebServer.takeRequest();
        assertEquals("POST", qdrantReq.getMethod());
        assertTrue(qdrantReq.getPath().endsWith("/collections/chat_knowledge/points/search"));
        String qdrantBody = qdrantReq.getBody().readUtf8();
        assertTrue(qdrantBody.contains("vector"));
        assertTrue(qdrantBody.contains("tenant_id"));
    }

    @Test
    void search_shouldReturnEmptyWhenQueryBlank() {
        Map<String, Object> result = gateway.search("tenant-a", "  ");
        List<?> hits = (List<?>) result.get("hits");
        assertTrue(hits.isEmpty());
    }

    @Test
    void indexText_shouldEmbedChunksAndUpsertQdrantPoints() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"data\":[{\"embedding\":[0.11,0.22,0.33]}]}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"status\":\"ok\",\"result\":{\"name\":\"chat_knowledge\"}}")
            .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\",\"result\":{\"operation_id\":1}}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = gateway.indexText("tenant-a", "知识标题", "这是一段需要写入知识库的内容", 500, 100);

        assertEquals("indexed", result.get("status"));
        assertEquals(1, result.get("chunks"));

        RecordedRequest embReq = mockWebServer.takeRequest();
        assertEquals("POST", embReq.getMethod());
        assertTrue(embReq.getPath().endsWith("/embeddings"));

        RecordedRequest checkReq = mockWebServer.takeRequest();
        assertEquals("GET", checkReq.getMethod());
        assertTrue(checkReq.getPath().endsWith("/collections/chat_knowledge"));

        RecordedRequest upsertReq = mockWebServer.takeRequest();
        assertEquals("PUT", upsertReq.getMethod());
        assertTrue(upsertReq.getPath().endsWith("/collections/chat_knowledge/points"));
        String upsertBody = upsertReq.getBody().readUtf8();
        assertTrue(upsertBody.contains("\"points\""));
        assertTrue(upsertBody.contains("知识标题"));
        assertTrue(upsertBody.contains("tenant_id"));
    }

    @Test
    void initCollection_shouldCreateWhenCollectionMissing() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404)
                .setBody("{\"status\":{\"error\":\"not found\"}}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = gateway.initCollection(1024, "Cosine");
        assertEquals("created", result.get("status"));

        RecordedRequest checkReq = mockWebServer.takeRequest();
        assertEquals("GET", checkReq.getMethod());
        assertTrue(checkReq.getPath().endsWith("/collections/chat_knowledge"));

        RecordedRequest createReq = mockWebServer.takeRequest();
        assertEquals("PUT", createReq.getMethod());
        assertTrue(createReq.getPath().endsWith("/collections/chat_knowledge"));
        String createBody = createReq.getBody().readUtf8();
        assertTrue(createBody.contains("\"vectors\""));
        assertTrue(createBody.contains("1024"));
    }

    @Test
    void initCollection_shouldAutoDetectDimensionWhenVectorSizeMissing() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"data\":[{\"embedding\":[0.1,0.2,0.3,0.4]}]}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(404)
                .setBody("{\"status\":{\"error\":\"not found\"}}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = gateway.initCollection(null, "Cosine");
        assertEquals("created", result.get("status"));
        assertEquals(4, result.get("vectorSize"));
        assertEquals(true, result.get("autoDetected"));

        RecordedRequest embReq = mockWebServer.takeRequest();
        assertEquals("POST", embReq.getMethod());
        assertTrue(embReq.getPath().endsWith("/embeddings"));

        RecordedRequest checkReq = mockWebServer.takeRequest();
        assertEquals("GET", checkReq.getMethod());
        assertTrue(checkReq.getPath().endsWith("/collections/chat_knowledge"));

        RecordedRequest createReq = mockWebServer.takeRequest();
        assertEquals("PUT", createReq.getMethod());
        assertTrue(createReq.getPath().endsWith("/collections/chat_knowledge"));
        String createBody = createReq.getBody().readUtf8();
        assertTrue(createBody.contains("\"vectors\""));
        assertTrue(createBody.contains("4"));
    }

    @Test
    void listCollections_shouldReturnAllCollectionNames() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"ok\",\"result\":{\"collections\":[{\"name\":\"chat_knowledge\"},{\"name\":\"tenant_docs\"}]}}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = gateway.listCollections();
        assertEquals(2, result.get("count"));

        RecordedRequest req = mockWebServer.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().endsWith("/collections"));
    }

    @Test
    void deleteCollection_shouldCallQdrantDeleteCollectionApi() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = gateway.deleteCollection("chat_knowledge");
        assertEquals("deleted", result.get("status"));

        RecordedRequest req = mockWebServer.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertTrue(req.getPath().endsWith("/collections/chat_knowledge"));
    }

    @Test
    void deleteVectors_shouldCallQdrantDeletePointsApi() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"ok\",\"result\":{\"operation_id\":1}}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = gateway.deleteVectors("chat_knowledge", List.of("1001", "1002"));
        assertEquals(2, result.get("deleted"));

        RecordedRequest req = mockWebServer.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().endsWith("/collections/chat_knowledge/points/delete"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("1001"));
        assertTrue(body.contains("1002"));
    }

    @Test
    void clearVectors_shouldScrollAndDeleteAllPoints() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"ok\",\"result\":{\"points\":[{\"id\":1001},{\"id\":1002}],\"next_page_offset\":1002}}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"ok\",\"result\":{\"operation_id\":1}}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"ok\",\"result\":{\"points\":[],\"next_page_offset\":null}}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = gateway.clearVectors("chat_knowledge");
        assertEquals("cleared", result.get("status"));
        assertEquals(2, result.get("deleted"));

        RecordedRequest scrollReq1 = mockWebServer.takeRequest();
        assertEquals("POST", scrollReq1.getMethod());
        assertTrue(scrollReq1.getPath().endsWith("/collections/chat_knowledge/points/scroll"));

        RecordedRequest deleteReq = mockWebServer.takeRequest();
        assertEquals("POST", deleteReq.getMethod());
        assertTrue(deleteReq.getPath().endsWith("/collections/chat_knowledge/points/delete"));

        RecordedRequest scrollReq2 = mockWebServer.takeRequest();
        assertEquals("POST", scrollReq2.getMethod());
        assertTrue(scrollReq2.getPath().endsWith("/collections/chat_knowledge/points/scroll"));
    }
}
