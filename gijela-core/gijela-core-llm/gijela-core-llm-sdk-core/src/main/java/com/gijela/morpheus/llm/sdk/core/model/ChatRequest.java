package com.gijela.morpheus.llm.sdk.core.model;

import java.util.List;
import java.util.Map;

/**
 * 统一聊天请求。
 */
public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        Integer maxTokens,
        Map<String, Object> metadata
) {
}
