package com.gijela.morpheus.chat.domain.vo.graph;

public record GraphMutationResultResponse(
        String graphSpace,
        String action,
        Integer affected
) {
}