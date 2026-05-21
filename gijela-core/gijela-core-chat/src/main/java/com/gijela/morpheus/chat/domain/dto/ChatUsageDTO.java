package com.gijela.morpheus.chat.domain.dto;

public record ChatUsageDTO(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
