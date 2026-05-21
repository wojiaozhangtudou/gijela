package com.gijela.morpheus.chat.domain.dto.graph;

public record GraphRelationshipPageRequest(
        String graphSpace,
        Integer page,
        Integer pageSize,
        String keyword,
        String entityType
) {
}