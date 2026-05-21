package com.gijela.morpheus.chat.adapter.storage;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectStorageGatewayTest {

    private MockWebServer mockWebServer;
    private ObjectStorageGateway gateway;
    private ChatModuleProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new ChatModuleProperties();
        properties.getStorage().setEndpoint(mockWebServer.url("/").toString());
        properties.getStorage().setUploadPath("/upload");
        properties.getStorage().setBucket("chat-attachments");
        gateway = new ObjectStorageGateway(properties, new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void upload_shouldCallRemoteStorageAndReturnMappedResult() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"bucket\":\"chat-attachments\",\"objectKey\":\"tenant-a/s-1/20260429/abc.txt\",\"provider\":\"rustfs\"}")
                .addHeader("Content-Type", "application/json"));

        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> result = gateway.upload(new ChatContext("tenant-a", "req-1", "s-1"), file);

        assertEquals("chat-attachments", result.get("bucket"));
        assertEquals("rustfs", result.get("provider"));
        assertEquals("tenant-a/s-1/20260429/abc.txt", result.get("objectKey"));

        var request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/upload", request.getPath());
        assertTrue(request.getBody().readUtf8().contains("chat-attachments"));
    }

    @Test
    void upload_shouldThrowWhenRemoteReturnsError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertThrows(RuntimeException.class,
                () -> gateway.upload(new ChatContext("tenant-a", "req-1", "s-1"), file));
    }

    @Test
    void upload_shouldUseBasicAuthWhenAccessKeyAndSecretKeyConfigured() throws Exception {
        properties.getStorage().setAccessKey("minioadmin");
        properties.getStorage().setSecretKey("minioadmin");
        gateway = new ObjectStorageGateway(properties, new OkHttpClient());

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"bucket\":\"chat-attachments\",\"objectKey\":\"tenant-a/s-1/20260429/abc.txt\",\"provider\":\"rustfs\"}")
                .addHeader("Content-Type", "application/json"));

        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        gateway.upload(new ChatContext("tenant-a", "req-1", "s-1"), file);

        var request = mockWebServer.takeRequest();
        assertEquals("Basic bWluaW9hZG1pbjptaW5pb2FkbWlu", request.getHeader("Authorization"));
    }

    @Test
    void upload_shouldUseSigV4WhenModeIsS3() throws Exception {
        properties.getStorage().setMode("s3");
        properties.getStorage().setAccessKey("minioadmin");
        properties.getStorage().setSecretKey("minioadmin");
        properties.getStorage().setRegion("us-east-1");
        properties.getStorage().setS3PathStyle(true);
        gateway = new ObjectStorageGateway(properties, new OkHttpClient());

        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        gateway.upload(new ChatContext("tenant-a", "req-1", "s-1"), file);

        var request = mockWebServer.takeRequest();
        assertEquals("PUT", request.getMethod());
        assertTrue(request.getPath().startsWith("/chat-attachments/tenant-a/s-1/"));
        assertTrue(request.getHeader("Authorization").startsWith("AWS4-HMAC-SHA256 Credential=minioadmin/"));
        assertEquals("text/plain", request.getHeader("Content-Type"));
        assertTrue(request.getHeader("x-amz-date") != null && !request.getHeader("x-amz-date").isBlank());
    }
}
