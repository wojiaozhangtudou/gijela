package com.gijela.morpheus.chat.domain.vo.graph;

import java.util.List;

public record GraphEntityTypeListResponse(
        String graphSpace,
        Integer count,
        List<String> items
) {
}
