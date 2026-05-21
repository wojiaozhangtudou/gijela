package com.gijela.morpheus.llm.sdk.core.event;

/**
 * 流式事件监听器。
 */
public interface LlmEventListener {

    default void onEvent(LlmEvent event) {
        // 默认 no-op
    }

    default void onError(Throwable throwable) {
        // 默认 no-op
    }
}
