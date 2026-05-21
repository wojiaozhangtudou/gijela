package com.gijela.morpheus.chat.domain.dto.graph;

import jakarta.validation.constraints.NotBlank;

public record GraphImportRequest(
        @NotBlank(message = "previewId 不能为空") String previewId,
        String graphSpace,
        String importMode
) {
}