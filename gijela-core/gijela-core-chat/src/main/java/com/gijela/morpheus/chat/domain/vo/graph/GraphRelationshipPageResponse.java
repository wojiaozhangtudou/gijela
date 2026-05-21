package com.gijela.morpheus.chat.domain.vo.graph;

import java.util.List;

public record GraphRelationshipPageResponse(
        String graphSpace,
        Integer page,
        Integer pageSize,
        Long total,
        List<GraphRelationshipItemVO> items
) {
}