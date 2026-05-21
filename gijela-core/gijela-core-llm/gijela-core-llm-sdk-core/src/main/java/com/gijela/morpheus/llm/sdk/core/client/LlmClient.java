package com.gijela.morpheus.llm.sdk.core.client;

import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.core.model.ChatResponse;

/**
 * 同步聊天客户端。
 */
public interface LlmClient {

    ChatResponse chat(ChatRequest request);
}
