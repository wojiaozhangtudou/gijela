package com.gijela.morpheus.chat.domain.dto.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SystemPromptDraftUpdateRequest(
        @NotBlank(message = "appCode 不能为空")
        String appCode,
        @NotBlank(message = "modelRoute 不能为空")
        String modelRoute,
        @NotBlank(message = "draftContent 不能为空")
        @Size(max = 20000, message = "draftContent 长度不能超过20000")
        String draftContent,
        @NotNull(message = "draftVersion 不能为空")
        Long draftVersion
) {
}
