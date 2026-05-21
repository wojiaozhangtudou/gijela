package com.gijela.morpheus.chat.domain.vo.model;

import java.time.LocalDateTime;

public record ModelConfigItemResponse(
        Long id,
        String configType,
        String providerKey,
        String model,
        String baseUrl,
        String apiKey,
        Integer connectTimeoutSeconds,
        Integer readTimeoutSeconds,
        Integer callTimeoutSeconds,
        Boolean enabled,
        LocalDateTime updatedAt,
        String updatedBy
) {
}
