package com.gijela.morpheus.chat.service;

public interface ChatAuditService {

    void record(String tenantId, String requestId, String sessionId, String action, String result);
}
