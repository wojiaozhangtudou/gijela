package com.gijela.morpheus.chat.domain.dto.prompt;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SystemPromptItemUpdateRequest(
        @NotBlank(message = "content 不能为空")
        @Size(max = 20000, message = "content 长度不能超过20000")
        String content,
        @NotNull(message = "priority 不能为空")
        @Min(value = 0, message = "priority 最小为0")
        @Max(value = 10000, message = "priority 最大为10000")
        Integer priority,
        @NotNull(message = "enabled 不能为空")
        Boolean enabled
) {
}
