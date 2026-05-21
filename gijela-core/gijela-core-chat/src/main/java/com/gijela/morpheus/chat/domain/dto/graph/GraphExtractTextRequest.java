package com.gijela.morpheus.chat.domain.dto.graph;

import jakarta.validation.constraints.NotBlank;

public record GraphExtractTextRequest(
        String graphSpace,
        String title,
        @NotBlank(message = "inputText 不能为空") String inputText,
        String llmModel,
        String extractMode,
        String importMode,
        String promptOverride,
        Integer maxTokens
) {
}