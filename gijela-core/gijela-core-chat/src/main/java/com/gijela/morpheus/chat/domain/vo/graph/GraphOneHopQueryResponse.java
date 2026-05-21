package com.gijela.morpheus.chat.domain.vo.graph;

import java.util.List;

public record GraphOneHopQueryResponse(
        String graphSpace,
        String centerEntity,
        Integer nodeCount,
        Integer edgeCount,
        List<GraphEntityItemVO> nodes,
        List<GraphRelationshipItemVO> edges
) {
}
