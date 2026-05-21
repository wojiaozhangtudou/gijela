package com.gijela.morpheus.chat.domain.dto.graph;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GraphDeleteRelationshipsRequest(
        String graphSpace,
        @NotEmpty(message = "relationshipIds 不能为空") List<String> relationshipIds
) {
}