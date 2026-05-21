package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.client.ToolExecutor;
import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.core.model.ChatResponse;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientFunctionCallingTest {

    @Test
    void shouldNotContinueSecondCompletionWhenAutoToolContinueDisabled() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              \"id\": \"chatcmpl-1\",
                              \"choices\": [
                                {
                                  \"index\": 0,
                                  \"message\": {
                                    \"role\": \"assistant\",
                                    \"content\": null,
                                    \"tool_calls\": [
                                      {
                                        \"id\": \"call_1\",
                                        \"type\": \"function\",
                                        \"function\": {
                                          \"name\": \"weather\",
                                          \"arguments\": \"{\\\"city\\\":\\\"北京\\\"}\"
                                        }
                                      }
                                    ]
                                  },
                                  \"finish_reason\": \"tool_calls\"
                                }
                              ],
                              \"usage\": {\"prompt_tokens\": 10, \"completion_tokens\": 5, \"total_tokens\": 15}
                            }
                            """));

            String baseUrl = server.url("/v1").toString();
            OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties(
                    baseUrl,
                    "sk-test",
                    "gpt-test",
                    3,
                    60,
                    65
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

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(new OkHttpClient(), properties);
            ChatResponse response = client.chat(request);

            assertNotNull(response);
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void shouldExecuteToolAndContinueSecondCompletion() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              \"id\": \"chatcmpl-1\",
                              \"choices\": [
                                {
                                  \"index\": 0,
                                  \"message\": {
                                    \"role\": \"assistant\",
                                    \"content\": null,
                                    \"tool_calls\": [
                                      {
                                        \"id\": \"call_1\",
                                        \"type\": \"function\",
                                        \"function\": {
                                          \"name\": \"weather\",
                                          \"arguments\": \"{\\\"city\\\":\\\"北京\\\"}\"
                                        }
                                      }
                                    ]
                                  },
                                  \"finish_reason\": \"tool_calls\"
                                }
                              ],
                              \"usage\": {\"prompt_tokens\": 10, \"completion_tokens\": 5, \"total_tokens\": 15}
                            }
                            """));

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              \"id\": \"chatcmpl-2\",
                              \"choices\": [
                                {
                                  \"index\": 0,
                                  \"message\": {
                                    \"role\": \"assistant\",
                                    \"content\": \"今天北京晴天\"
                                  },
                                  \"finish_reason\": \"stop\"
                                }
                              ],
                              \"usage\": {\"prompt_tokens\": 20, \"completion_tokens\": 8, \"total_tokens\": 28}
                            }
                            """));

            String baseUrl = server.url("/v1").toString();
            OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties(
                    baseUrl,
                    "sk-test",
                    "gpt-test",
                    3,
                    60,
                    65
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

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(new OkHttpClient(), properties);
            ChatResponse response = client.chat(request);

            assertNotNull(response);
            assertEquals("今天北京晴天", response.content());

            assertEquals(2, server.getRequestCount());
            RecordedRequest secondRequest = server.takeRequest();
            secondRequest = server.takeRequest();
            String secondBody = safeBody(secondRequest);
            assertTrue(secondBody.contains("\"role\":\"tool\""));
            assertTrue(secondBody.contains("\"tool_call_id\":\"call_1\""));
        }
    }

    @Test
    void shouldBuildDefaultToolContextFromMetadataWhenNoExplicitContext() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id": "chatcmpl-1",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "content": null,
                                    "tool_calls": [
                                      {
                                        "id": "call_1",
                                        "type": "function",
                                        "function": {
                                          "name": "weather",
                                          "arguments": "{\\\"city\\\":\\\"北京\\\"}"
                                        }
                                      }
                                    ]
                                  },
                                  "finish_reason": "tool_calls"
                                }
                              ],
                              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                            }
                            """));

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id": "chatcmpl-2",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "content": "今天北京晴天"
                                  },
                                  "finish_reason": "stop"
                                }
                              ],
                              "usage": {"prompt_tokens": 20, "completion_tokens": 8, "total_tokens": 28}
                            }
                            """));

            String baseUrl = server.url("/v1").toString();
            OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties(
                    baseUrl,
                    "sk-test",
                    "gpt-test",
                    3,
                    60,
                    65
            );

            AtomicReference<ToolContext> capturedContext = new AtomicReference<>();
            ToolExecutor executor = (call, context) -> {
                capturedContext.set(context);
                return new ToolResult(call.id(), true, Map.of("ok", true), null);
            };

            Map<String, Object> metadata = Map.of(
                    OpenAiMetadataKeys.TOOL_EXECUTOR, executor,
                    OpenAiMetadataKeys.TENANT_ID, "tenant-001",
                    OpenAiMetadataKeys.REQUEST_ID, "req-001"
            );
            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "北京天气怎么样")),
                    0.2,
                    256,
                    metadata
            );

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(new OkHttpClient(), properties);
            ChatResponse response = client.chat(request);

            assertNotNull(response);
            assertNotNull(capturedContext.get());
            assertEquals("tenant-001", capturedContext.get().tenantId());
            assertEquals("req-001", String.valueOf(capturedContext.get().attributes().get(OpenAiMetadataKeys.REQUEST_ID)));
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test
    void shouldPreferExplicitToolContextOverMetadataFallback() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id": "chatcmpl-1",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "content": null,
                                    "tool_calls": [
                                      {
                                        "id": "call_1",
                                        "type": "function",
                                        "function": {
                                          "name": "weather",
                                          "arguments": "{\\\"city\\\":\\\"北京\\\"}"
                                        }
                                      }
                                    ]
                                  },
                                  "finish_reason": "tool_calls"
                                }
                              ],
                              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                            }
                            """));

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id": "chatcmpl-2",
                              "choices": [
                                {
                                  "index": 0,
                                  "message": {
                                    "role": "assistant",
                                    "content": "今天北京晴天"
                                  },
                                  "finish_reason": "stop"
                                }
                              ],
                              "usage": {"prompt_tokens": 20, "completion_tokens": 8, "total_tokens": 28}
                            }
                            """));

            String baseUrl = server.url("/v1").toString();
            OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties(
                    baseUrl,
                    "sk-test",
                    "gpt-test",
                    3,
                    60,
                    65
            );

            AtomicReference<ToolContext> capturedContext = new AtomicReference<>();
            ToolExecutor executor = (call, context) -> {
                capturedContext.set(context);
                return new ToolResult(call.id(), true, Map.of("ok", true), null);
            };

            ToolContext explicitContext = new ToolContext("tenant-explicit", Map.of("scope", "finance"));
            Map<String, Object> metadata = Map.of(
                    OpenAiMetadataKeys.TOOL_EXECUTOR, executor,
                    OpenAiMetadataKeys.TOOL_CONTEXT, explicitContext,
                    OpenAiMetadataKeys.TENANT_ID, "tenant-fallback",
                    OpenAiMetadataKeys.REQUEST_ID, "req-002"
            );
            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "北京天气怎么样")),
                    0.2,
                    256,
                    metadata
            );

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(new OkHttpClient(), properties);
            ChatResponse response = client.chat(request);

            assertNotNull(response);
            assertNotNull(capturedContext.get());
            assertEquals("tenant-explicit", capturedContext.get().tenantId());
            assertEquals("finance", String.valueOf(capturedContext.get().attributes().get("scope")));
            assertEquals(2, server.getRequestCount());
        }
    }

    private static String safeBody(RecordedRequest request) throws IOException {
        return request.getBody().readUtf8();
    }
}
