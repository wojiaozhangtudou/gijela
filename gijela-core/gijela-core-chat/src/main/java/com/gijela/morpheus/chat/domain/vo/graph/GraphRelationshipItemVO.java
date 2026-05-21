package com.gijela.morpheus.chat.domain.vo.graph;

public record GraphRelationshipItemVO(
        String relationshipId,
        String sourceEntity,
        String targetEntity,
        String relationshipDescription,
        Integer relationshipStrength,
        String graphSpace,
        String updatedAt
) {
}