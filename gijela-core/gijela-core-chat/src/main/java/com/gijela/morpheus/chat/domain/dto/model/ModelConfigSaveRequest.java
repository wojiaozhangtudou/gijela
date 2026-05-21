package com.gijela.morpheus.chat.domain.dto.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ModelConfigSaveRequest(
        @NotBlank(message = "configType 不能为空")
        @Pattern(regexp = "(?i)CHAT|EMBEDDING", message = "configType 仅支持 CHAT 或 EMBEDDING")
        String configType,

        @Size(max = 64, message = "providerKey 长度不能超过64")
        String providerKey,

        @NotBlank(message = "model 不能为空")
        @Size(max = 128, message = "model 长度不能超过128")
        String model,

        @NotBlank(message = "baseUrl 不能为空")
        @Size(max = 512, message = "baseUrl 长度不能超过512")
        String baseUrl,

        @Size(max = 512, message = "apiKey 长度不能超过512")
        String apiKey,

        Boolean clearApiKey,

        @Min(value = 1, message = "connectTimeoutSeconds 不能小于1")
        @Max(value = 600, message = "connectTimeoutSeconds 不能大于600")
        Integer connectTimeoutSeconds,

        @Min(value = 1, message = "readTimeoutSeconds 不能小于1")
        @Max(value = 600, message = "readTimeoutSeconds 不能大于600")
        Integer readTimeoutSeconds,

        @Min(value = 1, message = "callTimeoutSeconds 不能小于1")
        @Max(value = 1200, message = "callTimeoutSeconds 不能大于1200")
        Integer callTimeoutSeconds,

        Boolean enabled
) {
}
