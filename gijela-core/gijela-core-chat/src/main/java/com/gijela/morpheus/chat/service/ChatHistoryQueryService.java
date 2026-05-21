package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.chat.domain.dto.ChatHistoryMessageResponse;
import com.gijela.morpheus.chat.domain.dto.ChatSessionItemResponse;

import java.util.List;
import java.util.Map;

public interface ChatHistoryQueryService {

    Map<String, Object> createSession(String tenantId, String title, String model);

    List<ChatSessionItemResponse> listSessions(String tenantId, int limit);

    List<ChatHistoryMessageResponse> listMessages(String tenantId, String sessionId, int limit);

    Map<String, Object> deleteSession(String tenantId, String sessionId);

    Map<String, Object> updateSessionTitle(String tenantId, String sessionId, String title, String model);
}
