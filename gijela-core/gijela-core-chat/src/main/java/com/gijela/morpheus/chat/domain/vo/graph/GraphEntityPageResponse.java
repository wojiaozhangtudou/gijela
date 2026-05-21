package com.gijela.morpheus.chat.domain.vo.graph;

import java.util.List;

public record GraphEntityPageResponse(
        String graphSpace,
        Integer page,
        Integer pageSize,
        Long total,
        List<GraphEntityItemVO> items
) {
}