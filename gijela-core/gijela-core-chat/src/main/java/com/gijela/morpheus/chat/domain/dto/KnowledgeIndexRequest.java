package com.gijela.morpheus.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeIndexRequest(
        String title,
        @NotBlank(message = "content 不能为空") String content,
        Integer chunkSize,
        Integer chunkOverlap,
        String embeddingModel
) {
}
