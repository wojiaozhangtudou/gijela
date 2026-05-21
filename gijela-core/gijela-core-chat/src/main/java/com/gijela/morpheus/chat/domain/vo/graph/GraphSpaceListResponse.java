package com.gijela.morpheus.chat.domain.vo.graph;

import java.util.List;

public record GraphSpaceListResponse(
        String defaultSpace,
        Integer count,
        List<GraphSpaceItemVO> items
) {
}