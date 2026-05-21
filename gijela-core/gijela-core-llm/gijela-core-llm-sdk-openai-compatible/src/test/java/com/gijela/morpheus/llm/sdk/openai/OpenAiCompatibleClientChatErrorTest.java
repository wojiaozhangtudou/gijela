package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.error.LlmException;
import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiCompatibleClientChatErrorTest {

    @Test
    void shouldReportNetworkErrorWhenChatConnectionRefused() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/v1").toString();
        server.shutdown();

        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OkHttpClient(),
                new OpenAiCompatibleProperties(baseUrl, "sk-test", "gpt-test", 1, 1, 2)
        );

        LlmException exception = assertThrows(LlmException.class, () -> client.chat(createRequest()));
        assertEquals("NETWORK_ERROR", exception.getErrorCode().name());
    }

    @Test
    void shouldReportTimeoutWhenChatBodyDelayed() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"chatcmpl-timeout\",\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}")
                    .setBodyDelay(3, TimeUnit.SECONDS));

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 1, 1, 2)
            );

            LlmException exception = assertThrows(LlmException.class, () -> client.chat(createRequest()));
            assertEquals("TIMEOUT", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldReportAuthErrorOnChatUnauthorized() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"unauthorized\"}}"));

            OpenAiCompatibleClient client = createClient(server);
            LlmException exception = assertThrows(LlmException.class, () -> client.chat(createRequest()));

            assertEquals("AUTH_ERROR", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldReportAuthErrorOnChatForbidden() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"forbidden\"}}"));

            OpenAiCompatibleClient client = createClient(server);
            LlmException exception = assertThrows(LlmException.class, () -> client.chat(createRequest()));

            assertEquals("AUTH_ERROR", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldReportRateLimitedErrorOnChatFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"rate limit\"}}"));

            OpenAiCompatibleClient client = createClient(server);
            LlmException exception = assertThrows(LlmException.class, () -> client.chat(createRequest()));

            assertEquals("RATE_LIMITED", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldReportModelErrorOnChatServerFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"server error\"}}"));

            OpenAiCompatibleClient client = createClient(server);
            LlmException exception = assertThrows(LlmException.class, () -> client.chat(createRequest()));

            assertEquals("MODEL_ERROR", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldReportModelErrorOnMalformedChatBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"choices\":[broken]}"));

            OpenAiCompatibleClient client = createClient(server);
            LlmException exception = assertThrows(LlmException.class, () -> client.chat(createRequest()));

            assertEquals("MODEL_ERROR", exception.getErrorCode().name());
        }
    }

    private static OpenAiCompatibleClient createClient(MockWebServer server) {
        return new OpenAiCompatibleClient(
                new OkHttpClient(),
                new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 3, 60, 65)
        );
    }

    private static ChatRequest createRequest() {
        return new ChatRequest(
                null,
                List.of(new ChatMessage("user", "hi")),
                0.2,
                32,
                Map.of()
        );
    }
}
