package com.gijela.morpheus.chat.domain.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ChatHistoryMessageResponse(
        String role,
        String content,
        LocalDateTime createdAt,
        List<Map<String, Object>> references
) {
}
