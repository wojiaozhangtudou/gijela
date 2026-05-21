package com.gijela.morpheus.chat.domain.vo.prompt;

import java.time.LocalDateTime;

public record PromptItemResponse(
        Long id,
        String scopeType,
        String appCode,
        String modelRoute,
        String sessionId,
        String promptName,
        String content,
        Integer priority,
        Boolean enabled,
        Long version,
        LocalDateTime updatedAt
) {
}
