package com.gijela.morpheus.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
        @NotBlank(message = "title 不能为空")
        @Size(max = 255, message = "title 长度不能超过255")
        String title,
        @Size(max = 128, message = "model 长度不能超过128")
        String model
) {
}