package com.gijela.morpheus.llm.sdk.openai;

import com.gijela.morpheus.llm.sdk.core.model.ChatResponse;
import com.gijela.morpheus.llm.sdk.core.model.TokenUsage;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容响应映射。
 */
public class OpenAiResponseMapper {

    @SuppressWarnings("unchecked")
    public ChatResponse toChatResponse(Map<String, Object> raw) {
        String id = stringValue(raw.get("id"));

        String content = null;
        String finishReason = null;
        Object choicesObj = raw.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> firstChoice) {
                finishReason = stringValue(firstChoice.get("finish_reason"));
                Object messageObj = firstChoice.get("message");
                if (messageObj instanceof Map<?, ?> message) {
                    content = stringValue(message.get("content"));
                }
            }
        }

        TokenUsage usage = new TokenUsage(0, 0, 0);
        Object usageObj = raw.get("usage");
        if (usageObj instanceof Map<?, ?> usageMap) {
            int prompt = intValue(usageMap.get("prompt_tokens"));
            int completion = intValue(usageMap.get("completion_tokens"));
            int total = intValue(usageMap.get("total_tokens"));
            usage = new TokenUsage(prompt, completion, total);
        }

        return new ChatResponse(id, content, finishReason, usage);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
