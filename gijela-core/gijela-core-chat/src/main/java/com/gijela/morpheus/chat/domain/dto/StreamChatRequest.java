package com.gijela.morpheus.chat.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StreamChatRequest(
        String sessionId,
        @Valid @NotEmpty(message = "messages 不能为空") List<ChatMessageDTO> messages,
        String model,
        Double temperature,
        Integer maxTokens,
        List<String> skills
) {
}
