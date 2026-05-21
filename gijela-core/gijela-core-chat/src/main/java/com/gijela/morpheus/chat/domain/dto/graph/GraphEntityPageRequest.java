package com.gijela.morpheus.chat.domain.dto.graph;

import jakarta.validation.constraints.NotBlank;

public record GraphEntityPageRequest(
        String graphSpace,
        Integer page,
        Integer pageSize,
        String keyword,
        String entityType
) {
}