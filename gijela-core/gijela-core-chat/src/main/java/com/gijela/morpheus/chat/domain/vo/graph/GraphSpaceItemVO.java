package com.gijela.morpheus.chat.domain.vo.graph;

public record GraphSpaceItemVO(
        String graphSpace,
        Long entityCount,
        Long relationshipCount
) {
}