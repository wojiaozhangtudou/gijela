package com.gijela.morpheus.chat.domain.dto.graph;

import jakarta.validation.constraints.NotBlank;

public record GraphDeleteImportBatchRequest(
        String graphSpace,
        @NotBlank(message = "importBatchId 不能为空") String importBatchId
) {
}
