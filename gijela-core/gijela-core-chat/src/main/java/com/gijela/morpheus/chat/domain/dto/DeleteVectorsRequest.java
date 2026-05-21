package com.gijela.morpheus.chat.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record DeleteVectorsRequest(
        @NotBlank(message = "collection 不能为空") String collection,
        @NotEmpty(message = "ids 不能为空") List<String> ids
) {
}
