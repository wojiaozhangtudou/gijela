package com.gijela.morpheus.chat.domain.dto.graph;

import jakarta.validation.constraints.NotBlank;

public record CreateGraphSpaceRequest(
        @NotBlank(message = "graphSpace 不能为空") String graphSpace
) {
}