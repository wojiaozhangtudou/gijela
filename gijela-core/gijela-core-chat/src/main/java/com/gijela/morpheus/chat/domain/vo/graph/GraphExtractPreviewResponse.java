package com.gijela.morpheus.chat.domain.vo.graph;

import java.util.List;

public record GraphExtractPreviewResponse(
        String previewId,
        String graphSpace,
        String title,
        String sourceType,
        String extractMode,
        String importMode,
        String status,
        List<GraphExtractEntityVO> entities,
        List<GraphExtractRelationshipVO> relationships,
        List<String> warnings,
        GraphExtractStatsVO stats
) {
}