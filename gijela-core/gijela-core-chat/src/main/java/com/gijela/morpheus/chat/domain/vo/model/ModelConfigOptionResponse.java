package com.gijela.morpheus.chat.domain.vo.model;

public record ModelConfigOptionResponse(
        String configType,
        String providerKey,
        String model,
        String label
) {
}
