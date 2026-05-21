package com.gijela.morpheus.chat.domain.vo.graph;

public record GraphImportResultResponse(
        String previewId,
        String graphSpace,
        String importBatchId,
        String importMode,
        Integer importedEntities,
        Integer importedRelationships,
        String status
) {
}