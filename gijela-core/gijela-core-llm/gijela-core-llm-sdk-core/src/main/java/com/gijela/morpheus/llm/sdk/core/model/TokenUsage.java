package com.gijela.morpheus.llm.sdk.core.model;

/**
 * Token 使用统计。
 */
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
}
