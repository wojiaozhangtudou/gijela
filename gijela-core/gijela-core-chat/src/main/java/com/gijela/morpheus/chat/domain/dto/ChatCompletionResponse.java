package com.gijela.morpheus.chat.domain.dto;

import java.util.List;
import java.util.Map;

public record ChatCompletionResponse(
        String sessionId,
        String content,
        String finishReason,
        ChatUsageDTO usage,
        List<Map<String, Object>> references
) {
}
