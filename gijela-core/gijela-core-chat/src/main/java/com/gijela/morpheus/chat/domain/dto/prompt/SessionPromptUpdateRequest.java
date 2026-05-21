package com.gijela.morpheus.chat.domain.dto.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SessionPromptUpdateRequest(
        @NotBlank(message = "content 不能为空")
        @Size(max = 2000, message = "content 长度不能超过2000")
        String content,
        @NotNull(message = "version 不能为空")
        Long version
) {
}
