package com.gijela.morpheus.llm.sdk.core.event;

/**
 * 统一流式事件类型。
 */
public enum LlmEventType {
    START,
    DELTA,
    TOOL_CALL,
    TOOL_RESULT,
    ERROR,
    DONE
}
