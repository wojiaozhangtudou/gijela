package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.service.ChatAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultChatAuditService implements ChatAuditService {

    private static final Logger log = LoggerFactory.getLogger(DefaultChatAuditService.class);

    @Override
    public void record(String tenantId, String requestId, String sessionId, String action, String result) {
        log.info("chat-audit tenantId={}, requestId={}, sessionId={}, action={}, result={}",
                tenantId, requestId, sessionId, action, result);
    }
}
