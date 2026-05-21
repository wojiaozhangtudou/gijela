package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.chat.domain.dto.ChatMessageDTO;

import java.util.List;
import java.util.Map;

public interface ConversationStore {

    String ensureSessionId(String sessionId);

    void ensureConversationModel(String tenantId, String sessionId, String model);

    void appendMessages(String tenantId, String sessionId, List<ChatMessageDTO> messages);

    default void appendAssistantMessage(String tenantId, String sessionId, String content, List<Map<String, Object>> references) {
        appendMessages(tenantId, sessionId, List.of(new ChatMessageDTO("assistant", content)));
    }

    List<ChatMessageDTO> getRecentMessages(String tenantId, String sessionId);
}
