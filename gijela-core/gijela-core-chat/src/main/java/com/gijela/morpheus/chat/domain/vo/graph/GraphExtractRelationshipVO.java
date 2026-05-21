package com.gijela.morpheus.chat.domain.vo.graph;

public record GraphExtractRelationshipVO(
        String sourceEntity,
        String targetEntity,
        String relationshipDescription,
        Integer relationshipStrength,
        String relationshipId
) {
}