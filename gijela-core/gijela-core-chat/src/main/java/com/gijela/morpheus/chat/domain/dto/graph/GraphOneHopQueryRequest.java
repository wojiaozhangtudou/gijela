package com.gijela.morpheus.chat.domain.dto.graph;

import jakarta.validation.constraints.NotBlank;

public record GraphOneHopQueryRequest(
        String graphSpace,
        @NotBlank(message = "centerEntity 不能为空") String centerEntity,
        Integer limitNodes,
        Integer limitEdges
) {
}
