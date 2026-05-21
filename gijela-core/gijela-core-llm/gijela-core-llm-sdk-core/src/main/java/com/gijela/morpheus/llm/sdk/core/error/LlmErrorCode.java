package com.gijela.morpheus.llm.sdk.core.error;

/**
 * LLM 统一错误码。
 */
public enum LlmErrorCode {
    NETWORK_ERROR,
    TIMEOUT,
    RATE_LIMITED,
    AUTH_ERROR,
    MODEL_ERROR,
    TOOL_ERROR,
    UNKNOWN
}
