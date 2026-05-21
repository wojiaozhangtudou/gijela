package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容请求映射。
 */
public class OpenAiRequestMapper {

    public Map<String, Object> toOpenAiRequest(ChatRequest request, OpenAiCompatibleProperties properties) {
        return toOpenAiRequest(request, properties, false);
    }

    public Map<String, Object> toOpenAiRequest(ChatRequest request, OpenAiCompatibleProperties properties, boolean stream) {
        if (request.messages() == null || request.messages().isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("model", resolveModel(request.model(), properties.model()));
        result.put("messages", mapMessages(request.messages()));
        if (request.temperature() != null) {
            result.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            result.put("max_tokens", request.maxTokens());
        }
        Map<String, Object> metadata = request.metadata();
        if (metadata != null) {
            Object tools = metadata.get("tools");
            if (tools != null) {
                result.put("tools", tools);
            }
            Object toolChoice = metadata.get("tool_choice");
            if (toolChoice != null) {
                result.put("tool_choice", toolChoice);
            }
        }
        result.put("stream", stream);
        if (stream) {
            result.put("stream_options", Map.of("include_usage", true));
        }
        return result;
    }

    private static String resolveModel(String requestModel, String defaultModel) {
        if (requestModel != null && !requestModel.isBlank()) {
            return requestModel;
        }
        if (defaultModel != null && !defaultModel.isBlank()) {
            return defaultModel;
        }
        throw new IllegalArgumentException("model 未配置");
    }

    private static List<Map<String, Object>> mapMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("role", message.role());
                    m.put("content", message.content());
                    return m;
                })
                .toList();
    }
}
