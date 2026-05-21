package com.gijela.morpheus.llm.sdk.core.error;

/**
 * LLM 统一异常。
 */
public class LlmException extends RuntimeException {

    private final LlmErrorCode errorCode;

    public LlmException(LlmErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LlmException(LlmErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public LlmErrorCode getErrorCode() {
        return errorCode;
    }
}
