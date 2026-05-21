package com.gijela.morpheus.chat.domain.dto.graph;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GraphDeleteEntitiesRequest(
        String graphSpace,
        @NotEmpty(message = "normalizedNames 不能为空") List<String> normalizedNames
) {
}