package com.gijela.morpheus.chat.domain.dto;

import java.time.LocalDateTime;

public record ChatSessionItemResponse(
        String sessionId,
        String title,
        String summary,
        String model,
        Integer messageCount,
        LocalDateTime updatedAt
) {
}
