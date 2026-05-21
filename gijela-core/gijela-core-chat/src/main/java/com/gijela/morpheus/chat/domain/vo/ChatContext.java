package com.gijela.morpheus.chat.domain.vo;

public record ChatContext(
        String tenantId,
        String requestId,
        String sessionId
) {
}
