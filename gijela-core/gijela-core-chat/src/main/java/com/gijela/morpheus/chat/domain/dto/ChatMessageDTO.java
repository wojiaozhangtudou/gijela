package com.gijela.morpheus.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageDTO(
        @NotBlank(message = "role 不能为空") String role,
        @NotBlank(message = "content 不能为空") String content
) {
}
