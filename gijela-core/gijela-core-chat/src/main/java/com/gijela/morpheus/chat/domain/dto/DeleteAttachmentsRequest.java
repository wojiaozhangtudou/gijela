package com.gijela.morpheus.chat.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DeleteAttachmentsRequest(
        @NotEmpty(message = "ids 不能为空")
        List<@NotNull(message = "id 不能为空") Long> ids
) {
}
