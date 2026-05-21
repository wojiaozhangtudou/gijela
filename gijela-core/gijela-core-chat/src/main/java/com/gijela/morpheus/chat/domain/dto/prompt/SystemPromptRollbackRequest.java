package com.gijela.morpheus.chat.domain.dto.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SystemPromptRollbackRequest(
        @NotBlank(message = "appCode 不能为空")
        String appCode,
        @NotBlank(message = "modelRoute 不能为空")
        String modelRoute,
        @NotNull(message = "targetPublishedVersion 不能为空")
        Long targetPublishedVersion
) {
}
