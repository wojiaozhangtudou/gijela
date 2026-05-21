package com.gijela.morpheus.llm.sdk.core.client;

import com.gijela.morpheus.llm.sdk.core.event.LlmEventListener;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;

/**
 * 流式聊天客户端。
 */
public interface StreamingLlmClient {

    AutoCloseable stream(ChatRequest request, LlmEventListener listener);
}
