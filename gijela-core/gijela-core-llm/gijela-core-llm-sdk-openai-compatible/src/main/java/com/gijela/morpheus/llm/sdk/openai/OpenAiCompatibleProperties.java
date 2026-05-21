package com.gijela.morpheus.llm.sdk.openai;

/**
 * OpenAI 兼容配置。
 */
public record OpenAiCompatibleProperties(
        String baseUrl,
        String apiKey,
        String model,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int callTimeoutSeconds
) {
}
