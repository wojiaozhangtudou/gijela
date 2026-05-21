package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.client.LlmClient;
import com.gijela.morpheus.llm.sdk.core.client.StreamingLlmClient;
import com.gijela.morpheus.llm.sdk.core.error.LlmErrorCode;
import com.gijela.morpheus.llm.sdk.core.error.LlmException;
import com.gijela.morpheus.llm.sdk.core.event.LlmEvent;
import com.gijela.morpheus.llm.sdk.core.event.LlmEventListener;
import com.gijela.morpheus.llm.sdk.core.event.LlmEventType;
import com.gijela.morpheus.llm.sdk.core.client.ToolExecutor;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.core.model.ChatResponse;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import com.gijela.morpheus.llm.sdk.observability.BudgetEvent;
import com.gijela.morpheus.llm.sdk.observability.BudgetEventPublisher;
import com.gijela.morpheus.llm.sdk.observability.LlmAuditLogger;
import com.gijela.morpheus.llm.sdk.observability.LlmMetricsCollector;
import com.gijela.morpheus.llm.sdk.observability.LlmObservationInterceptor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

/**
 * OpenAI 兼容客户端（占位骨架，HTTP 统一使用 OkHttp）。
 */
public class OpenAiCompatibleClient implements LlmClient, StreamingLlmClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final OpenAiCompatibleProperties properties;
    private final ObjectMapper objectMapper;
    private final OpenAiRequestMapper requestMapper;
    private final OpenAiResponseMapper responseMapper;

    public OpenAiCompatibleClient(OkHttpClient okHttpClient, OpenAiCompatibleProperties properties) {
        this.okHttpClient = applyTimeouts(okHttpClient, properties);
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.requestMapper = new OpenAiRequestMapper();
        this.responseMapper = new OpenAiResponseMapper();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (request == null) {
            throw new LlmException(LlmErrorCode.UNKNOWN, "chat request 不能为空");
        }
        OpenAiRuntimeOptions runtimeOptions = OpenAiRuntimeOptions.fromMetadata(request.metadata());
        String model = resolveModel(request);
        LlmObservationInterceptor observation = runtimeOptions.observationOrDefault();
        long start = observation.before(model);
        try {
            Map<String, Object> payload = requestMapper.toOpenAiRequest(request, properties);
            Map<String, Object> firstRaw = executeChatCompletion(payload);
            Map<String, Object> finalRaw = maybeContinueWithToolResults(payload, firstRaw, runtimeOptions);
            ChatResponse response = responseMapper.toChatResponse(finalRaw);
            long latencyMs = observation.after(start);
            recordSuccess(runtimeOptions, model, latencyMs);
            audit(runtimeOptions, "chat", "SUCCESS");
            return response;
        } catch (LlmException e) {
            recordFailure(runtimeOptions, model, e.getErrorCode().name());
            audit(runtimeOptions, "chat", "FAILED:" + e.getErrorCode().name());
            throw e;
        } catch (SocketTimeoutException e) {
            LlmException ex = new LlmException(LlmErrorCode.TIMEOUT, "模型调用超时", e);
            recordFailure(runtimeOptions, model, ex.getErrorCode().name());
            audit(runtimeOptions, "chat", "FAILED:" + ex.getErrorCode().name());
            throw ex;
        } catch (IOException e) {
            LlmException ex = new LlmException(LlmErrorCode.NETWORK_ERROR, "模型调用网络异常", e);
            recordFailure(runtimeOptions, model, ex.getErrorCode().name());
            audit(runtimeOptions, "chat", "FAILED:" + ex.getErrorCode().name());
            throw ex;
        } catch (RuntimeException e) {
            LlmException ex = new LlmException(LlmErrorCode.UNKNOWN, "模型调用未知异常", e);
            recordFailure(runtimeOptions, model, ex.getErrorCode().name());
            audit(runtimeOptions, "chat", "FAILED:" + ex.getErrorCode().name());
            throw ex;
        }
    }

    @Override
    public AutoCloseable stream(ChatRequest request, LlmEventListener listener) {
        if (request == null) {
            throw new LlmException(LlmErrorCode.UNKNOWN, "stream request 不能为空");
        }
        if (listener == null) {
            throw new LlmException(LlmErrorCode.UNKNOWN, "listener 不能为空");
        }

        Map<String, Object> payload = requestMapper.toOpenAiRequest(request, properties, true);
        OpenAiRuntimeOptions runtimeOptions = OpenAiRuntimeOptions.fromMetadata(request.metadata());
        String model = resolveModel(request);
        LlmObservationInterceptor observation = runtimeOptions.observationOrDefault();
        long start = observation.before(model);
        StreamSession session = new StreamSession();
        Thread worker = new Thread(() -> {
            try {
                Map<String, Object> currentPayload = payload;
                while (!session.cancelled()) {
                    StreamCycleResult cycleResult = streamOnce(currentPayload, runtimeOptions, listener, session);
                    Map<String, Object> nextPayload = buildNextStreamPayloadIfNeeded(currentPayload, runtimeOptions, cycleResult);
                    if (nextPayload == null) {
                        break;
                    }
                    currentPayload = nextPayload;
                }
                if (!session.cancelled()) {
                    long latencyMs = observation.after(start);
                    recordSuccess(runtimeOptions, model, latencyMs);
                    audit(runtimeOptions, "stream", "SUCCESS");
                }
            } catch (SocketTimeoutException e) {
                if (!session.cancelled()) {
                    recordFailure(runtimeOptions, model, LlmErrorCode.TIMEOUT.name());
                    audit(runtimeOptions, "stream", "FAILED:" + LlmErrorCode.TIMEOUT.name());
                    listener.onError(new LlmException(LlmErrorCode.TIMEOUT, "流式调用超时", e));
                }
            } catch (IOException e) {
                if (!session.cancelled()) {
                    recordFailure(runtimeOptions, model, LlmErrorCode.NETWORK_ERROR.name());
                    audit(runtimeOptions, "stream", "FAILED:" + LlmErrorCode.NETWORK_ERROR.name());
                    listener.onError(new LlmException(LlmErrorCode.NETWORK_ERROR, "流式读取失败", e));
                }
            } catch (LlmException e) {
                if (!session.cancelled()) {
                    recordFailure(runtimeOptions, model, e.getErrorCode().name());
                    audit(runtimeOptions, "stream", "FAILED:" + e.getErrorCode().name());
                    listener.onError(e);
                }
            } catch (Exception e) {
                if (!session.cancelled()) {
                    recordFailure(runtimeOptions, model, LlmErrorCode.UNKNOWN.name());
                    audit(runtimeOptions, "stream", "FAILED:" + LlmErrorCode.UNKNOWN.name());
                    listener.onError(new LlmException(LlmErrorCode.UNKNOWN, "流式处理异常", e));
                }
            }
        }, "llm-openai-stream");
        worker.setDaemon(true);
        worker.start();

        return () -> {
            try {
                session.cancel();
                worker.interrupt();
            } finally {
                session.closeCurrent();
            }
        };
    }

    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    public OpenAiCompatibleProperties getProperties() {
        return properties;
    }

    private static OkHttpClient applyTimeouts(OkHttpClient base, OpenAiCompatibleProperties properties) {
        OkHttpClient.Builder builder = base.newBuilder();
        if (properties.connectTimeoutSeconds() > 0) {
            builder.connectTimeout(properties.connectTimeoutSeconds(), TimeUnit.SECONDS);
        }
        if (properties.readTimeoutSeconds() > 0) {
            builder.readTimeout(properties.readTimeoutSeconds(), TimeUnit.SECONDS);
        }
        if (properties.callTimeoutSeconds() > 0) {
            builder.callTimeout(properties.callTimeoutSeconds(), TimeUnit.SECONDS);
        }
        return builder.build();
    }

    private static String buildChatCompletionUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + "/chat/completions";
    }

    private static LlmErrorCode mapHttpStatusToErrorCode(int code) {
        if (code == 401 || code == 403) {
            return LlmErrorCode.AUTH_ERROR;
        }
        if (code == 429) {
            return LlmErrorCode.RATE_LIMITED;
        }
        if (code >= 500) {
            return LlmErrorCode.MODEL_ERROR;
        }
        return LlmErrorCode.UNKNOWN;
    }

    private Map<String, Object> executeChatCompletion(Map<String, Object> payload) throws IOException {
        String payloadJson = objectMapper.writeValueAsString(payload);
        Request httpRequest = new Request.Builder()
                .url(buildChatCompletionUrl(properties.baseUrl()))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(payloadJson, JSON))
                .build();

        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            ResponseBody body = response.body();
            String bodyText = body == null ? "" : body.string();

            if (!response.isSuccessful()) {
                LlmErrorCode code = mapHttpStatusToErrorCode(response.code());
                throw new LlmException(code, "上游模型调用失败, status=" + response.code() + ", body=" + bodyText);
            }

            try {
                return objectMapper.readValue(bodyText, new TypeReference<>() {
                });
            } catch (IOException e) {
                throw new LlmException(LlmErrorCode.MODEL_ERROR, "上游模型响应体解析失败", e);
            }
        }
    }

    private Map<String, Object> maybeContinueWithToolResults(
            Map<String, Object> originalPayload,
            Map<String, Object> firstRaw,
            OpenAiRuntimeOptions runtimeOptions
    ) throws IOException {
        if (!runtimeOptions.autoToolContinue()) {
            return firstRaw;
        }

        ToolExecutor executor = runtimeOptions.toolExecutor();
        if (executor == null) {
            return firstRaw;
        }

        Map<String, Object> assistantMessage = extractAssistantMessage(firstRaw);
        if (assistantMessage == null) {
            return firstRaw;
        }

        List<Map<String, Object>> toolCalls = extractToolCalls(assistantMessage);
        if (toolCalls.isEmpty()) {
            return firstRaw;
        }

        List<Map<String, Object>> nextMessages = new ArrayList<>(extractPayloadMessages(originalPayload));
        nextMessages.add(assistantMessage);

        ToolContext context = resolveToolContext(runtimeOptions);
        for (Map<String, Object> toolCallMap : toolCalls) {
            ToolResult toolResult = executeToolCall(toolCallMap, executor, context);
            nextMessages.add(buildToolResultMessage(toolResult));
        }

        Map<String, Object> secondPayload = new HashMap<>(originalPayload);
        secondPayload.put("messages", nextMessages);
        secondPayload.put("stream", false);
        return executeChatCompletion(secondPayload);
    }

    private ToolResult executeToolCall(Map<String, Object> toolCallMap, ToolExecutor executor, ToolContext context) {
        String toolCallId = stringValue(toolCallMap.get("id"));
        try {
            Object functionObj = toolCallMap.get("function");
            if (!(functionObj instanceof Map<?, ?> functionMap)) {
                return new ToolResult(toolCallId, false, Map.of(), "tool_call.function 缺失");
            }

            String name = stringValue(functionMap.get("name"));
            if (name == null || name.isBlank()) {
                return new ToolResult(toolCallId, false, Map.of(), "tool_call.function.name 缺失");
            }

            Map<String, Object> arguments = Map.of();
            String argumentText = stringValue(functionMap.get("arguments"));
            if (argumentText != null && !argumentText.isBlank()) {
                try {
                    arguments = objectMapper.readValue(argumentText, new TypeReference<>() {
                    });
                } catch (Exception e) {
                    arguments = Map.of("_raw", argumentText);
                }
            }

            return executor.execute(new com.gijela.morpheus.llm.sdk.core.tool.ToolCall(toolCallId, name, arguments), context);
        } catch (Exception e) {
            return new ToolResult(toolCallId, false, Map.of(), "工具执行异常: " + e.getMessage());
        }
    }

    private Map<String, Object> buildToolResultMessage(ToolResult toolResult) throws IOException {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolResult.toolCallId());

        Map<String, Object> content = new HashMap<>();
        content.put("success", toolResult.success());
        if (toolResult.result() != null && !toolResult.result().isEmpty()) {
            content.put("data", toolResult.result());
        }
        if (!toolResult.success()) {
            content.put("error", toolResult.errorMessage());
        }
        message.put("content", objectMapper.writeValueAsString(content));
        return message;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractPayloadMessages(Map<String, Object> payload) {
        Object messagesObj = payload.get("messages");
        if (messagesObj instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> messageMap) {
                    result.add(new HashMap<>((Map<String, Object>) messageMap));
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAssistantMessage(Map<String, Object> raw) {
        Object choicesObj = raw.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }

        Object firstChoiceObj = choices.get(0);
        if (!(firstChoiceObj instanceof Map<?, ?> firstChoice)) {
            return null;
        }

        Object messageObj = firstChoice.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            return null;
        }
        return new HashMap<>((Map<String, Object>) message);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> assistantMessage) {
        Object toolCallsObj = assistantMessage.get("tool_calls");
        if (!(toolCallsObj instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> toolCall) {
                result.add(new HashMap<>((Map<String, Object>) toolCall));
            }
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String resolveModel(ChatRequest request) {
        if (request.model() != null && !request.model().isBlank()) {
            return request.model();
        }
        return properties.model();
    }

    private void recordSuccess(OpenAiRuntimeOptions runtimeOptions, String model, long latencyMs) {
        LlmMetricsCollector collector = runtimeOptions.metricsCollector();
        if (collector != null) {
            collector.recordSuccess(model, latencyMs);
        }
    }

    private void recordFailure(OpenAiRuntimeOptions runtimeOptions, String model, String errorCode) {
        LlmMetricsCollector collector = runtimeOptions.metricsCollector();
        if (collector != null) {
            collector.recordFailure(model, errorCode);
        }
        publishBudgetFailureEvent(runtimeOptions, model, errorCode);
    }

    private void audit(OpenAiRuntimeOptions runtimeOptions, String action, String result) {
        LlmAuditLogger logger = runtimeOptions.auditLogger();
        if (logger == null) {
            return;
        }
        logger.record(runtimeOptions.tenantId(), runtimeOptions.requestId(), action, result);
    }

    private void publishBudgetFailureEvent(OpenAiRuntimeOptions runtimeOptions, String model, String errorCode) {
        BudgetEventPublisher publisher = runtimeOptions.budgetEventPublisher();
        if (publisher == null) {
            return;
        }
        String tenantId = runtimeOptions.tenantId();
        String level = runtimeOptions.budgetLevelOrDefault();
        String message = "model=" + model + ", errorCode=" + errorCode;
        publisher.publish(new BudgetEvent(tenantId, level, message));
    }

    private Optional<ToolResult> emitToolResult(LlmEvent event, OpenAiRuntimeOptions runtimeOptions) {
        if (event.type() != LlmEventType.TOOL_CALL || event.toolCall() == null) {
            return Optional.empty();
        }

        ToolExecutor executor = runtimeOptions.toolExecutor();
        if (executor == null) {
            return Optional.empty();
        }

        ToolContext context = resolveToolContext(runtimeOptions);
        try {
            return Optional.of(executor.execute(event.toolCall(), context));
        } catch (Exception e) {
            throw new LlmException(LlmErrorCode.TOOL_ERROR, "工具调用执行失败: " + event.toolCall().name(), e);
        }
    }

    private StreamCycleResult streamOnce(
            Map<String, Object> payload,
            OpenAiRuntimeOptions runtimeOptions,
            LlmEventListener listener,
            StreamSession session
    ) throws IOException {
        String payloadJson = objectMapper.writeValueAsString(payload);
        Request httpRequest = new Request.Builder()
                .url(buildChatCompletionUrl(properties.baseUrl()))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(payloadJson, JSON))
                .build();

        Call call = okHttpClient.newCall(httpRequest);
        session.bindCall(call);

        Response response = call.execute();
        session.bindResponse(response);
        if (!response.isSuccessful()) {
            String bodyText = response.body() == null ? "" : response.body().string();
            LlmErrorCode code = mapHttpStatusToErrorCode(response.code());
            response.close();
            throw new LlmException(code, "流式调用失败, status=" + response.code() + ", body=" + bodyText);
        }

        ResponseBody body = response.body();
        if (body == null) {
            response.close();
            throw new LlmException(LlmErrorCode.MODEL_ERROR, "流式调用无响应体");
        }

        SseEventParser parser = new SseEventParser(objectMapper);
        Map<String, com.gijela.morpheus.llm.sdk.core.tool.ToolCall> toolCallMap = new LinkedHashMap<>();
        List<ToolResult> toolResults = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!session.cancelled() && !Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                for (LlmEvent event : parser.parseLine(line)) {
                    boolean suppressIntermediateDone = event.type() == LlmEventType.DONE
                            && runtimeOptions.autoToolContinue()
                            && !toolCallMap.isEmpty();
                    if (!suppressIntermediateDone) {
                        listener.onEvent(event);
                    }
                    if (event.type() == LlmEventType.TOOL_CALL && event.toolCall() != null) {
                        toolCallMap.put(event.toolCall().id(), event.toolCall());
                    }
                    Optional<ToolResult> toolResult = emitToolResult(event, runtimeOptions);
                    if (toolResult.isPresent()) {
                        toolResults.add(toolResult.get());
                        listener.onEvent(new LlmEvent(LlmEventType.TOOL_RESULT, null, null, toolResult.get(), null, null));
                    }
                }
            }
            return new StreamCycleResult(new ArrayList<>(toolCallMap.values()), toolResults);
        } finally {
            session.clearResponse(response);
            response.close();
            session.clearCall(call);
        }
    }

    private Map<String, Object> buildNextStreamPayloadIfNeeded(
            Map<String, Object> currentPayload,
            OpenAiRuntimeOptions runtimeOptions,
            StreamCycleResult cycleResult
    ) throws IOException {
        if (!runtimeOptions.autoToolContinue()) {
            return null;
        }
        if (cycleResult.toolCalls().isEmpty() || cycleResult.toolResults().isEmpty()) {
            return null;
        }

        List<Map<String, Object>> nextMessages = extractPayloadMessages(currentPayload);
        nextMessages.add(buildAssistantToolCallMessage(cycleResult.toolCalls()));
        for (ToolResult toolResult : cycleResult.toolResults()) {
            nextMessages.add(buildToolResultMessage(toolResult));
        }

        Map<String, Object> nextPayload = new HashMap<>(currentPayload);
        nextPayload.put("messages", nextMessages);
        nextPayload.put("stream", true);
        return nextPayload;
    }

    private Map<String, Object> buildAssistantToolCallMessage(List<com.gijela.morpheus.llm.sdk.core.tool.ToolCall> toolCalls) throws IOException {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", null);

        List<Map<String, Object>> openAiToolCalls = new ArrayList<>();
        for (com.gijela.morpheus.llm.sdk.core.tool.ToolCall toolCall : toolCalls) {
            Map<String, Object> function = new HashMap<>();
            function.put("name", toolCall.name());
            function.put("arguments", objectMapper.writeValueAsString(toolCall.arguments() == null ? Map.of() : toolCall.arguments()));

            Map<String, Object> item = new HashMap<>();
            item.put("id", toolCall.id());
            item.put("type", "function");
            item.put("function", function);
            openAiToolCalls.add(item);
        }
        message.put("tool_calls", openAiToolCalls);
        return message;
    }

    private record StreamCycleResult(
            List<com.gijela.morpheus.llm.sdk.core.tool.ToolCall> toolCalls,
            List<ToolResult> toolResults
    ) {
    }

    private static final class StreamSession {

        private volatile boolean cancelled;
        private volatile Call currentCall;
        private volatile Response currentResponse;

        boolean cancelled() {
            return cancelled;
        }

        void cancel() {
            this.cancelled = true;
        }

        void bindCall(Call call) {
            this.currentCall = call;
        }

        void clearCall(Call call) {
            if (Objects.equals(this.currentCall, call)) {
                this.currentCall = null;
            }
        }

        void bindResponse(Response response) {
            this.currentResponse = response;
        }

        void clearResponse(Response response) {
            if (Objects.equals(this.currentResponse, response)) {
                this.currentResponse = null;
            }
        }

        void closeCurrent() {
            Call call = this.currentCall;
            if (call != null) {
                call.cancel();
            }
            Response response = this.currentResponse;
            if (response != null) {
                response.close();
            }
        }
    }

    private ToolContext resolveToolContext(OpenAiRuntimeOptions runtimeOptions) {
        if (runtimeOptions.toolContext() != null) {
            return runtimeOptions.toolContext();
        }
        Map<String, Object> metadata = runtimeOptions.metadata();
        return new ToolContext(runtimeOptions.tenantId(), metadata == null ? Map.of() : metadata);
    }
}
