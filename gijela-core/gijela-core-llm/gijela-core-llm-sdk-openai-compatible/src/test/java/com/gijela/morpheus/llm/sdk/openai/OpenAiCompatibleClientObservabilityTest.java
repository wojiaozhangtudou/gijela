package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.event.LlmEvent;
import com.gijela.morpheus.llm.sdk.core.event.LlmEventListener;
import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.observability.BudgetEvent;
import com.gijela.morpheus.llm.sdk.observability.BudgetEventPublisher;
import com.gijela.morpheus.llm.sdk.observability.LlmAuditLogger;
import com.gijela.morpheus.llm.sdk.observability.LlmMetricsCollector;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientObservabilityTest {

    @Test
    void shouldRecordSuccessMetricsAndAuditOnChatSuccess() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              \"id\": \"chatcmpl-obs-1\",
                              \"choices\": [
                                {
                                  \"index\": 0,
                                  \"message\": {\"role\": \"assistant\", \"content\": \"ok\"},
                                  \"finish_reason\": \"stop\"
                                }
                              ],
                              \"usage\": {\"prompt_tokens\": 1, \"completion_tokens\": 1, \"total_tokens\": 2}
                            }
                            """));

            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();
            AtomicReference<String> auditResult = new AtomicReference<>();

            LlmMetricsCollector collector = new LlmMetricsCollector() {
                @Override
                public void recordSuccess(String model, long latencyMs) {
                    successCount.incrementAndGet();
                }

                @Override
                public void recordFailure(String model, String errorCode) {
                    failureCount.incrementAndGet();
                }
            };

            LlmAuditLogger logger = (tenantId, requestId, action, result) -> auditResult.set(result);

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 3, 60, 65)
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "hello")),
                    0.2,
                    32,
                    Map.of(
                            OpenAiMetadataKeys.METRICS_COLLECTOR, collector,
                            OpenAiMetadataKeys.AUDIT_LOGGER, logger,
                            OpenAiMetadataKeys.TENANT_ID, "t-1",
                            OpenAiMetadataKeys.REQUEST_ID, "r-1"
                    )
            );

            client.chat(request);

            assertEquals(1, successCount.get());
            assertEquals(0, failureCount.get());
            assertEquals("SUCCESS", auditResult.get());
        }
    }

    @Test
    void shouldRecordFailureMetricsAndAuditOnStreamFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"rate limit\"}}"));

            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();
            AtomicReference<String> failureCode = new AtomicReference<>();
            AtomicReference<String> auditResult = new AtomicReference<>();
            AtomicReference<BudgetEvent> budgetEventRef = new AtomicReference<>();

            LlmMetricsCollector collector = new LlmMetricsCollector() {
                @Override
                public void recordSuccess(String model, long latencyMs) {
                    successCount.incrementAndGet();
                }

                @Override
                public void recordFailure(String model, String errorCode) {
                    failureCount.incrementAndGet();
                    failureCode.set(errorCode);
                }
            };

            LlmAuditLogger logger = (tenantId, requestId, action, result) -> auditResult.set(result);
            BudgetEventPublisher budgetPublisher = budgetEventRef::set;

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new OkHttpClient(),
                    new OpenAiCompatibleProperties(server.url("/v1").toString(), "sk-test", "gpt-test", 3, 60, 65)
            );

            ChatRequest request = new ChatRequest(
                    null,
                    List.of(new ChatMessage("user", "hello")),
                    0.2,
                    32,
                    Map.of(
                            OpenAiMetadataKeys.METRICS_COLLECTOR, collector,
                            OpenAiMetadataKeys.AUDIT_LOGGER, logger,
                            OpenAiMetadataKeys.BUDGET_EVENT_PUBLISHER, budgetPublisher,
                            OpenAiMetadataKeys.BUDGET_EVENT_LEVEL, "ALERT",
                            OpenAiMetadataKeys.TENANT_ID, "t-1",
                            OpenAiMetadataKeys.REQUEST_ID, "r-2"
                    )
            );

            CountDownLatch errorLatch = new CountDownLatch(1);
            AutoCloseable closeable = client.stream(request, new LlmEventListener() {
                @Override
                public void onEvent(LlmEvent event) {
                    // no-op
                }

                @Override
                public void onError(Throwable throwable) {
                    errorLatch.countDown();
                }
            });

            try {
                assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
            } finally {
                closeable.close();
            }

            assertEquals(0, successCount.get());
            assertEquals(1, failureCount.get());
            assertEquals("RATE_LIMITED", failureCode.get());
            assertEquals("FAILED:RATE_LIMITED", auditResult.get());
            assertEquals("t-1", budgetEventRef.get().tenantId());
            assertEquals("ALERT", budgetEventRef.get().level());
            assertTrue(budgetEventRef.get().message().contains("RATE_LIMITED"));
        }
    }
}
