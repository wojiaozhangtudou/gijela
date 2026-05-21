package com.gijela.morpheus.chat.domain.vo.prompt;

import java.time.LocalDateTime;

public record SystemPromptCurrentResponse(
        String tenantId,
        String appCode,
        String modelRoute,
        String draftContent,
        Long draftVersion,
        String publishedContent,
        Long publishedVersion,
        LocalDateTime publishedAt
) {
}
