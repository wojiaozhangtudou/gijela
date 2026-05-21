package com.gijela.morpheus.chat.domain.vo.graph;

public record GraphEntityItemVO(
        String entityName,
        String normalizedName,
        String entityType,
        String entityDescription,
        String graphSpace,
        String updatedAt
) {
}