package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.client.ToolExecutor;
import com.gijela.morpheus.llm.sdk.core.error.LlmException;
import com.gijela.morpheus.llm.sdk.core.event.LlmEvent;
import com.gijela.morpheus.llm.sdk.core.event.LlmEventListener;
import com.gijela.morpheus.llm.sdk.core.event.LlmEventType;
import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientStreamFunctionCallingTest {

    @Test
    void shouldReportNetworkErrorWhenStreamingConnectionRefused() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/v1").toString();
        server.shutdown();

        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                new OkHttpClient(),
                new OpenAiCompatibleProperties(baseUrl, "sk-test", "gpt-test", 1, 1, 2)
        );

        ChatRequest request = new ChatRequest(
                null,
                List.of(new ChatMessage("user", "hi")),
                0.2,
                32,
                Map.of()
        );

        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AutoCloseable closeable = client.stream(request, new LlmEventListener() {
            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                errorLatch.countDown();
            }
        });

        try {
            assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
        } finally {
            closeable.close();
        }

        LlmException exception = assertInstanceOf(LlmException.class, errorRef.get());
        assertEquals("NETWORK_ERROR", exception.getErrorCode().name());
    }

    @Test
    void shouldReportTimeoutWhenStreamingBodyDelayed() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}

                            data: [DONE]

                            """)
                    .setBodyDelay(3, TimeUnit.SECONDS));

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 1, 1, 2)
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "hi")),
                    0.2,
                    32,
                    Map.of()
            );

            CountDownLatch errorLatch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onError(Throwable throwable) {
                    errorRef.set(throwable);
                    errorLatch.countDown();
                }
            });

            try {
                assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            LlmException exception = assertInstanceOf(LlmException.class, errorRef.get());
            assertEquals("TIMEOUT", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldReportAuthErrorOnStreamingUnauthorized() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"unauthorized\"}}"));

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 3, 60, 65)
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "hi")),
                    0.2,
                    32,
                    Map.of()
            );

            CountDownLatch errorLatch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onError(Throwable throwable) {
                    errorRef.set(throwable);
                    errorLatch.countDown();
                }
            });

            try {
                assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            LlmException exception = assertInstanceOf(LlmException.class, errorRef.get());
            assertEquals("AUTH_ERROR", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldReportAuthErrorOnStreamingForbidden() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"forbidden\"}}"));

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 3, 60, 65)
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "hi")),
                    0.2,
                    32,
                    Map.of()
            );

            CountDownLatch errorLatch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onError(Throwable throwable) {
                    errorRef.set(throwable);
                    errorLatch.countDown();
                }
            });

            try {
                assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            LlmException exception = assertInstanceOf(LlmException.class, errorRef.get());
            assertEquals("AUTH_ERROR", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldReportRateLimitedErrorOnStreamingFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"rate limit\"}}"));

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 3, 60, 65)
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "hi")),
                    0.2,
                    32,
                    Map.of()
            );

            CountDownLatch errorLatch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onError(Throwable throwable) {
                    errorRef.set(throwable);
                    errorLatch.countDown();
                }
            });

            try {
                assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            LlmException exception = assertInstanceOf(LlmException.class, errorRef.get());
            assertEquals("RATE_LIMITED", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldReportModelErrorOnStreamingServerFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"server error\"}}"));

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 3, 60, 65)
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "hi")),
                    0.2,
                    32,
                    Map.of()
            );

            CountDownLatch errorLatch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onError(Throwable throwable) {
                    errorRef.set(throwable);
                    errorLatch.countDown();
                }
            });

            try {
                assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            LlmException exception = assertInstanceOf(LlmException.class, errorRef.get());
            assertEquals("MODEL_ERROR", exception.getErrorCode().name());
        }
    }

    @Test
    void shouldEmitErrorEventForMalformedStreamingChunk() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {\"choices\":[broken]}
                            
                            data: [DONE]
                            
                            """));

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 3, 60, 65)
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "hi")),
                    0.2,
                    32,
                    Map.of()
            );

            CountDownLatch doneLatch = new CountDownLatch(1);
            List<LlmEvent> events = new CopyOnWriteArrayList<>();
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onEvent(LlmEvent event) {
                    events.add(event);
                    if (event.type() == LlmEventType.DONE) {
                        doneLatch.countDown();
                    }
                }
            });

            try {
                assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            assertTrue(events.stream().anyMatch(event -> event.type() == LlmEventType.ERROR));
            assertTrue(events.stream().anyMatch(event -> event.type() == LlmEventType.DONE));
        }
    }

    @Test
    void shouldStopReadingAfterClose() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {\"choices\":[{\"delta\":{\"content\":\"第一段\"}}]}
                            
                            data: {\"choices\":[{\"delta\":{\"content\":\"第二段\"}}]}
                            
                            data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}
                            
                            data: [DONE]
                            
                            """)
                            .throttleBody(32, 200, TimeUnit.MILLISECONDS));

            String baseUrl = server.url("/v1").toString();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(baseUrl, "sk-test", "gpt-test", 3, 60, 65)
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "说点什么")),
                    0.2,
                    128,
                        Map.of(OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, false)
            );

            List<LlmEvent> events = new CopyOnWriteArrayList<>();
            CountDownLatch firstDeltaLatch = new CountDownLatch(1);
            AtomicReference<AutoCloseable> closeableRef = new AtomicReference<>();

            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onEvent(LlmEvent event) {
                    events.add(event);
                    if (event.type() == LlmEventType.DELTA && firstDeltaLatch.getCount() > 0) {
                        firstDeltaLatch.countDown();
                        AutoCloseable current = closeableRef.get();
                        if (current != null) {
                            try {
                                current.close();
                            } catch (Exception ignored) {
                                // no-op
                            }
                        }
                    }
                }
            });
            closeableRef.set(closeable);

            assertTrue(firstDeltaLatch.await(5, TimeUnit.SECONDS));
            Thread.sleep(500);

            long deltaCount = events.stream()
                    .filter(event -> event.type() == LlmEventType.DELTA)
                    .count();
            assertEquals(1, deltaCount);
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void shouldExecuteToolAndContinueStreamingCompletion() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"weather\",\"arguments\":\"{\\\"city\\\":\\\"北京\\\"}\"}}]}}]}
                            
                            data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}
                            
                            data: [DONE]
                            
                            """));

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {\"choices\":[{\"delta\":{\"content\":\"今天北京晴天\"}}]}
                            
                            data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}
                            
                            data: [DONE]
                            
                            """));

            String baseUrl = server.url("/v1").toString();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(baseUrl, "sk-test", "gpt-test", 3, 60, 65)
            );

            ToolExecutor executor = (call, context) -> new ToolResult(
                    call.id(),
                    true,
                    Map.of("city", call.arguments().get("city"), "weather", "晴"),
                    null
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "北京天气怎么样")),
                    0.2,
                    256,
                        Map.of(OpenAiMetadataKeys.TOOL_EXECUTOR, executor)
            );

            List<LlmEvent> events = new CopyOnWriteArrayList<>();
            CountDownLatch doneLatch = new CountDownLatch(1);
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onEvent(LlmEvent event) {
                    events.add(event);
                    if (event.type() == LlmEventType.DONE) {
                        doneLatch.countDown();
                    }
                }
            });

            try {
                assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            assertEquals(2, server.getRequestCount());
            assertTrue(events.stream().anyMatch(event -> event.type() == LlmEventType.TOOL_CALL));
            assertTrue(events.stream().anyMatch(event -> event.type() == LlmEventType.TOOL_RESULT));
            assertTrue(events.stream().anyMatch(event -> event.type() == LlmEventType.DELTA && "今天北京晴天".equals(event.textDelta())));

            RecordedRequest firstRequest = server.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest secondRequest = server.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(firstRequest);
            assertNotNull(secondRequest);
            String secondBody = secondRequest.getBody().readUtf8();
            assertTrue(secondBody.contains("\"role\":\"tool\""));
            assertTrue(secondBody.contains("\"tool_call_id\":\"call_1\""));
        }
    }

    @Test
    void shouldNotContinueStreamingWhenAutoToolContinueDisabled() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"weather\",\"arguments\":\"{\\\"city\\\":\\\"北京\\\"}\"}}]}}]}

                            data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}

                            data: [DONE]

                            """));

            String baseUrl = server.url("/v1").toString();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(baseUrl, "sk-test", "gpt-test", 3, 60, 65)
            );

            ToolExecutor executor = (call, context) -> new ToolResult(
                    call.id(),
                    true,
                    Map.of("city", call.arguments().get("city"), "weather", "晴"),
                    null
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "北京天气怎么样")),
                    0.2,
                    256,
                    Map.of(
                            OpenAiMetadataKeys.TOOL_EXECUTOR, executor,
                            OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, false
                    )
            );

            List<LlmEvent> events = new CopyOnWriteArrayList<>();
            CountDownLatch doneLatch = new CountDownLatch(1);
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onEvent(LlmEvent event) {
                    events.add(event);
                    if (event.type() == LlmEventType.DONE) {
                        doneLatch.countDown();
                    }
                }
            });

            try {
                assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            assertEquals(1, server.getRequestCount());
            assertTrue(events.stream().anyMatch(event -> event.type() == LlmEventType.TOOL_CALL));
            assertTrue(events.stream().anyMatch(event -> event.type() == LlmEventType.TOOL_RESULT));
        }
    }

    @Test
    void shouldPreferExplicitToolContextDuringStreamingToolExecution() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"weather","arguments":"{\\\"city\\\":\\\"北京\\\"}"}}]}}]}

                            data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                            data: [DONE]

                            """));

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"content":"今天北京晴天"}}]}

                            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                            data: [DONE]

                            """));

            String baseUrl = server.url("/v1").toString();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(baseUrl, "sk-test", "gpt-test", 3, 60, 65)
            );

            AtomicReference<ToolContext> capturedContext = new AtomicReference<>();
            ToolExecutor executor = (call, context) -> {
                capturedContext.set(context);
                return new ToolResult(
                        call.id(),
                        true,
                        Map.of("city", call.arguments().get("city"), "weather", "晴"),
                        null
                );
            };

            ToolContext explicitContext = new ToolContext("tenant-explicit-stream", Map.of("scope", "finance"));
            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "北京天气怎么样")),
                    0.2,
                    256,
                    Map.of(
                            OpenAiMetadataKeys.TOOL_EXECUTOR, executor,
                            OpenAiMetadataKeys.TOOL_CONTEXT, explicitContext,
                            OpenAiMetadataKeys.TENANT_ID, "tenant-fallback-stream"
                    )
            );

            CountDownLatch doneLatch = new CountDownLatch(1);
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onEvent(LlmEvent event) {
                    if (event.type() == LlmEventType.DONE) {
                        doneLatch.countDown();
                    }
                }
            });

            try {
                assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            assertEquals(2, server.getRequestCount());
            assertNotNull(capturedContext.get());
            assertEquals("tenant-explicit-stream", capturedContext.get().tenantId());
            assertEquals("finance", String.valueOf(capturedContext.get().attributes().get("scope")));
        }
    }

    @Test
    void shouldUseExplicitToolContextWhenAutoContinueDisabledInStreaming() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"weather","arguments":"{\\\"city\\\":\\\"北京\\\"}"}}]}}]}

                            data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                            data: [DONE]

                            """));

            String baseUrl = server.url("/v1").toString();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(baseUrl, "sk-test", "gpt-test", 3, 60, 65)
            );

            AtomicReference<ToolContext> capturedContext = new AtomicReference<>();
            ToolExecutor executor = (call, context) -> {
                capturedContext.set(context);
                return new ToolResult(
                        call.id(),
                        true,
                        Map.of("city", call.arguments().get("city"), "weather", "晴"),
                        null
                );
            };

            ToolContext explicitContext = new ToolContext("tenant-explicit-stop", Map.of("scope", "ops"));
            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "北京天气怎么样")),
                    0.2,
                    256,
                    Map.of(
                            OpenAiMetadataKeys.TOOL_EXECUTOR, executor,
                            OpenAiMetadataKeys.TOOL_CONTEXT, explicitContext,
                            OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, false,
                            OpenAiMetadataKeys.TENANT_ID, "tenant-fallback-stop"
                    )
            );

            CountDownLatch doneLatch = new CountDownLatch(1);
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onEvent(LlmEvent event) {
                    if (event.type() == LlmEventType.DONE) {
                        doneLatch.countDown();
                    }
                }
            });

            try {
                assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            assertEquals(1, server.getRequestCount());
            assertNotNull(capturedContext.get());
            assertEquals("tenant-explicit-stop", capturedContext.get().tenantId());
            assertEquals("ops", String.valueOf(capturedContext.get().attributes().get("scope")));
        }
    }
}
