package com.gijela.morpheus.chat.domain.vo.graph;

public record GraphExtractStatsVO(
        Integer entityCount,
        Integer relationshipCount
) {
}