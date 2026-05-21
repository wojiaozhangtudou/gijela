package com.gijela.morpheus.chat.domain.vo.prompt;

import java.time.LocalDateTime;

public record SessionPromptResponse(
        String sessionId,
        String content,
        Long version,
        LocalDateTime updatedAt
) {
}
