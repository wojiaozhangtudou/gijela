package com.gijela.morpheus.chat.domain.vo.graph;

public record GraphExtractEntityVO(
        String entityName,
        String normalizedName,
        String entityType,
        String entityDescription
) {
}