package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.domain.entity.ChatAuditLog;
import com.gijela.morpheus.chat.mapper.ChatAuditLogMapper;
import com.gijela.morpheus.chat.service.ChatAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class MySqlChatAuditService implements ChatAuditService {

    private static final Logger log = LoggerFactory.getLogger(MySqlChatAuditService.class);

    private final ChatAuditLogMapper chatAuditLogMapper;

    public MySqlChatAuditService(ChatAuditLogMapper chatAuditLogMapper) {
        this.chatAuditLogMapper = chatAuditLogMapper;
    }

    @Override
    public void record(String tenantId, String requestId, String sessionId, String action, String result) {
        ChatAuditLog entity = new ChatAuditLog();
        entity.setTenantId(normalizeTenant(tenantId));
        entity.setRequestId(requestId);
        entity.setSessionId(sessionId);
        entity.setAction(action);
        entity.setResult(result);
        entity.setCreatedAt(LocalDateTime.now());
        try {
            chatAuditLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("chat audit persist failed tenantId={}, requestId={}, sessionId={}", tenantId, requestId, sessionId, e);
        }
    }

    private String normalizeTenant(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }
}
