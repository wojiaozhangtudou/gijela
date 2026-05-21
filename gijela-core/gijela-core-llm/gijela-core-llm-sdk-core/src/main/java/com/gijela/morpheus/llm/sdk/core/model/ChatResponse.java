package com.gijela.morpheus.llm.sdk.core.model;

/**
 * 统一聊天响应。
 */
public record ChatResponse(
        String id,
        String content,
        String finishReason,
        TokenUsage usage
) {
}
