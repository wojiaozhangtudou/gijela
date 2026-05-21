package com.gijela.morpheus.chat.domain.vo;

import com.gijela.morpheus.chat.domain.dto.ChatUsageDTO;

import java.util.List;
import java.util.Map;

public record ChatEventVO(
        String type,
        String sessionId,
        String content,
        Map<String, Object> toolCall,
        Map<String, Object> toolResult,
        String error,
        List<Map<String, Object>> references,
        ChatUsageDTO usage
) {
}
