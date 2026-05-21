package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptItemCreateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptItemUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptItemCreateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptItemUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptDraftUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptPublishRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptRollbackRequest;
import com.gijela.morpheus.chat.domain.vo.prompt.PromptItemResponse;
import com.gijela.morpheus.chat.domain.vo.prompt.SessionPromptResponse;
import com.gijela.morpheus.chat.domain.vo.prompt.SystemPromptCurrentResponse;

import java.util.List;

public interface PromptConfigService {

    SystemPromptCurrentResponse getSystemCurrent(String tenantId, String appCode, String modelRoute);

    SessionPromptResponse getSessionPrompt(String tenantId, String sessionId);

    SystemPromptCurrentResponse saveSystemDraft(String tenantId, String operator, SystemPromptDraftUpdateRequest request);

    SystemPromptCurrentResponse publishSystemPrompt(String tenantId, String operator, SystemPromptPublishRequest request);

    SystemPromptCurrentResponse rollbackSystemPrompt(String tenantId, String operator, SystemPromptRollbackRequest request);

    SessionPromptResponse updateSessionPrompt(String tenantId, String sessionId, String operator, SessionPromptUpdateRequest request);

    List<PromptItemResponse> listSystemPromptItems(String tenantId, String appCode, String modelRoute);

    PromptItemResponse createSystemPromptItem(String tenantId, String operator, SystemPromptItemCreateRequest request);

    PromptItemResponse updateSystemPromptItem(String tenantId, Long id, String operator, SystemPromptItemUpdateRequest request);

    void deleteSystemPromptItem(String tenantId, Long id);

    List<PromptItemResponse> listSessionPromptItems(String tenantId, String sessionId);

    PromptItemResponse createSessionPromptItem(String tenantId, String sessionId, String operator, SessionPromptItemCreateRequest request);

    PromptItemResponse updateSessionPromptItem(String tenantId, String sessionId, Long id, String operator, SessionPromptItemUpdateRequest request);

    void deleteSessionPromptItem(String tenantId, String sessionId, Long id);

    String resolveEffectivePrompt(String tenantId, String sessionId, String appCode, String modelRoute);

    String resolveEffectivePromptHash(String tenantId, String sessionId, String appCode, String modelRoute);
}
