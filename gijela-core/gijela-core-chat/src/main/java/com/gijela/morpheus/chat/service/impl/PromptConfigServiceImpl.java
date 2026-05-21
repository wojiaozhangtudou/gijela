package com.gijela.morpheus.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptItemCreateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptItemUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptItemCreateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptItemUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptDraftUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptPublishRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptRollbackRequest;
import com.gijela.morpheus.chat.domain.entity.ChatPromptSystem;
import com.gijela.morpheus.chat.domain.entity.ChatPromptSystemAudit;
import com.gijela.morpheus.chat.domain.entity.ChatPromptSystemItem;
import com.gijela.morpheus.chat.domain.entity.ChatSessionPromptItem;
import com.gijela.morpheus.chat.domain.entity.ChatSessionPrompt;
import com.gijela.morpheus.chat.domain.vo.prompt.PromptItemResponse;
import com.gijela.morpheus.chat.domain.vo.prompt.SessionPromptResponse;
import com.gijela.morpheus.chat.domain.vo.prompt.SystemPromptCurrentResponse;
import com.gijela.morpheus.chat.mapper.ChatPromptSystemAuditMapper;
import com.gijela.morpheus.chat.mapper.ChatPromptSystemItemMapper;
import com.gijela.morpheus.chat.mapper.ChatPromptSystemMapper;
import com.gijela.morpheus.chat.mapper.ChatSessionPromptItemMapper;
import com.gijela.morpheus.chat.mapper.ChatSessionPromptMapper;
import com.gijela.morpheus.chat.service.PromptConfigService;
import com.gijela.morpheus.chat.web.PromptConflictException;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromptConfigServiceImpl implements PromptConfigService {

    private final ChatPromptSystemMapper systemMapper;
    private final ChatPromptSystemAuditMapper systemAuditMapper;
    private final ChatSessionPromptMapper sessionPromptMapper;
    private final ChatPromptSystemItemMapper systemItemMapper;
    private final ChatSessionPromptItemMapper sessionItemMapper;

    public PromptConfigServiceImpl(ChatPromptSystemMapper systemMapper,
                                   ChatPromptSystemAuditMapper systemAuditMapper,
                                   ChatSessionPromptMapper sessionPromptMapper,
                                   ChatPromptSystemItemMapper systemItemMapper,
                                   ChatSessionPromptItemMapper sessionItemMapper) {
        this.systemMapper = systemMapper;
        this.systemAuditMapper = systemAuditMapper;
        this.sessionPromptMapper = sessionPromptMapper;
        this.systemItemMapper = systemItemMapper;
        this.sessionItemMapper = sessionItemMapper;
    }

    @Override
    public SystemPromptCurrentResponse getSystemCurrent(String tenantId, String appCode, String modelRoute) {
        String resolvedTenant = normalize(tenantId);
        ChatPromptSystem current = selectSystem(resolvedTenant, appCode, modelRoute);
        if (current == null) {
            return emptySystemCurrent(resolvedTenant, appCode, modelRoute);
        }
        return toCurrentResponse(current);
    }

    @Override
    public SessionPromptResponse getSessionPrompt(String tenantId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "sessionId 不能为空");
        }
        String resolvedTenant = normalize(tenantId);
        ChatSessionPrompt current = selectSessionPrompt(resolvedTenant, sessionId);
        if (current == null) {
            return new SessionPromptResponse(sessionId, "", 0L, null);
        }
        return new SessionPromptResponse(sessionId, nullToEmpty(current.getContent()), safeLong(current.getVersion()), current.getUpdatedAt());
    }

    @Override
    public SystemPromptCurrentResponse saveSystemDraft(String tenantId, String operator, SystemPromptDraftUpdateRequest request) {
        String resolvedTenant = normalize(tenantId);
        String resolvedOperator = normalizeOperator(operator);
        LocalDateTime now = LocalDateTime.now();

        ChatPromptSystem current = selectSystem(resolvedTenant, request.appCode(), request.modelRoute());
        if (current == null) {
            if (safeLong(request.draftVersion()) != 0L) {
                throwSystemConflict(0L, request.draftVersion(), "", null);
            }

            ChatPromptSystem entity = new ChatPromptSystem();
            entity.setTenantId(resolvedTenant);
            entity.setAppCode(request.appCode());
            entity.setModelRoute(request.modelRoute());
            entity.setDraftContent(request.draftContent());
            entity.setDraftVersion(1L);
            entity.setPublishedContent("");
            entity.setPublishedVersion(0L);
            entity.setDeleted(0);
            entity.setCreatedAt(now);
            entity.setCreatedBy(resolvedOperator);
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(resolvedOperator);
            systemMapper.insert(entity);

            insertAudit(resolvedTenant, request.appCode(), request.modelRoute(), "SAVE_DRAFT",
                    0L, 1L, "", request.draftContent(), resolvedOperator, now);
            return toCurrentResponse(entity);
        }

        long expected = safeLong(request.draftVersion());
        long actual = safeLong(current.getDraftVersion());
        if (expected != actual) {
            throwSystemConflict(actual, expected, current.getDraftContent(), current.getUpdatedAt());
        }

        current.setDraftContent(request.draftContent());
        current.setDraftVersion(actual + 1);
        current.setUpdatedAt(now);
        current.setUpdatedBy(resolvedOperator);
        systemMapper.updateById(current);

        insertAudit(resolvedTenant, request.appCode(), request.modelRoute(), "SAVE_DRAFT",
                actual, current.getDraftVersion(), nullToEmpty(current.getPublishedContent()), current.getDraftContent(), resolvedOperator, now);
        return toCurrentResponse(current);
    }

    @Override
    public SystemPromptCurrentResponse publishSystemPrompt(String tenantId, String operator, SystemPromptPublishRequest request) {
        String resolvedTenant = normalize(tenantId);
        String resolvedOperator = normalizeOperator(operator);
        LocalDateTime now = LocalDateTime.now();

        ChatPromptSystem current = selectSystem(resolvedTenant, request.appCode(), request.modelRoute());
        if (current == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "系统级提示词不存在");
        }

        long expectedDraftVersion = safeLong(request.draftVersion());
        long actualDraftVersion = safeLong(current.getDraftVersion());
        if (expectedDraftVersion != actualDraftVersion) {
            throwSystemConflict(actualDraftVersion, expectedDraftVersion, current.getDraftContent(), current.getUpdatedAt());
        }
        if (current.getDraftContent() == null || current.getDraftContent().isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "draftContent 不能为空，不能发布空提示词");
        }

        long beforeVersion = safeLong(current.getPublishedVersion());
        String beforeContent = nullToEmpty(current.getPublishedContent());

        current.setPublishedContent(current.getDraftContent());
        current.setPublishedVersion(beforeVersion + 1);
        current.setPublishedAt(now);
        current.setPublishedBy(resolvedOperator);
        current.setUpdatedAt(now);
        current.setUpdatedBy(resolvedOperator);
        systemMapper.updateById(current);

        insertAudit(resolvedTenant, request.appCode(), request.modelRoute(), "PUBLISH",
                beforeVersion, current.getPublishedVersion(), beforeContent, current.getPublishedContent(), resolvedOperator, now);
        return toCurrentResponse(current);
    }

    @Override
    public SystemPromptCurrentResponse rollbackSystemPrompt(String tenantId, String operator, SystemPromptRollbackRequest request) {
        String resolvedTenant = normalize(tenantId);
        String resolvedOperator = normalizeOperator(operator);
        LocalDateTime now = LocalDateTime.now();

        ChatPromptSystem current = selectSystem(resolvedTenant, request.appCode(), request.modelRoute());
        if (current == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "系统级提示词不存在");
        }

        long targetVersion = safeLong(request.targetPublishedVersion());
        if (targetVersion <= 0) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "targetPublishedVersion 必须大于0");
        }

        ChatPromptSystemAudit targetAudit = systemAuditMapper.selectOne(new QueryWrapper<ChatPromptSystemAudit>()
                .eq("tenant_id", resolvedTenant)
                .eq("app_code", request.appCode())
                .eq("model_route", request.modelRoute())
                .eq("after_version", targetVersion)
                .orderByDesc("id")
                .last("limit 1"));
        if (targetAudit == null || targetAudit.getAfterContent() == null || targetAudit.getAfterContent().isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "目标回滚版本不存在或无有效内容");
        }

        long beforeVersion = safeLong(current.getPublishedVersion());
        String beforeContent = nullToEmpty(current.getPublishedContent());

        current.setPublishedContent(targetAudit.getAfterContent());
        current.setPublishedVersion(targetVersion);
        current.setPublishedAt(now);
        current.setPublishedBy(resolvedOperator);
        current.setUpdatedAt(now);
        current.setUpdatedBy(resolvedOperator);
        systemMapper.updateById(current);

        insertAudit(resolvedTenant, request.appCode(), request.modelRoute(), "ROLLBACK",
                beforeVersion, targetVersion, beforeContent, current.getPublishedContent(), resolvedOperator, now);
        return toCurrentResponse(current);
    }

    @Override
    public SessionPromptResponse updateSessionPrompt(String tenantId, String sessionId, String operator, SessionPromptUpdateRequest request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "sessionId 不能为空");
        }

        String resolvedTenant = normalize(tenantId);
        String resolvedOperator = normalizeOperator(operator);
        LocalDateTime now = LocalDateTime.now();

        ChatSessionPrompt current = selectSessionPrompt(resolvedTenant, sessionId);

        if (current == null) {
            if (safeLong(request.version()) != 0L) {
                throwSessionConflict(0L, request.version(), "", null);
            }

            ChatSessionPrompt entity = new ChatSessionPrompt();
            entity.setTenantId(resolvedTenant);
            entity.setSessionId(sessionId);
            entity.setContent(request.content());
            entity.setVersion(1L);
            entity.setDeleted(0);
            entity.setCreatedAt(now);
            entity.setCreatedBy(resolvedOperator);
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(resolvedOperator);
            sessionPromptMapper.insert(entity);
            return new SessionPromptResponse(sessionId, entity.getContent(), entity.getVersion(), entity.getUpdatedAt());
        }

        long expected = safeLong(request.version());
        long actual = safeLong(current.getVersion());
        if (expected != actual) {
            throwSessionConflict(actual, expected, current.getContent(), current.getUpdatedAt());
        }

        current.setContent(request.content());
        current.setVersion(actual + 1);
        current.setUpdatedAt(now);
        current.setUpdatedBy(resolvedOperator);
        sessionPromptMapper.updateById(current);
        return new SessionPromptResponse(sessionId, current.getContent(), current.getVersion(), current.getUpdatedAt());
    }

    @Override
    public List<PromptItemResponse> listSystemPromptItems(String tenantId, String appCode, String modelRoute) {
        String resolvedTenant = normalize(tenantId);
        List<ChatPromptSystemItem> items = systemItemMapper.selectList(new QueryWrapper<ChatPromptSystemItem>()
                .eq("tenant_id", resolvedTenant)
                .eq("app_code", appCode)
                .eq("model_route", modelRoute)
                .eq("deleted", 0)
                .orderByAsc("priority")
                .orderByAsc("id"));
        return items.stream().map(this::toSystemItemResponse).collect(Collectors.toList());
    }

    @Override
    public PromptItemResponse createSystemPromptItem(String tenantId, String operator, SystemPromptItemCreateRequest request) {
        String resolvedTenant = normalize(tenantId);
        String resolvedOperator = normalizeOperator(operator);
        LocalDateTime now = LocalDateTime.now();

        ChatPromptSystemItem exists = systemItemMapper.selectOne(new QueryWrapper<ChatPromptSystemItem>()
                .eq("tenant_id", resolvedTenant)
                .eq("app_code", request.appCode())
                .eq("model_route", request.modelRoute())
                .eq("prompt_name", request.promptName())
                .eq("deleted", 0)
                .last("limit 1"));
        if (exists != null) {
            throw new BizException(ErrorCode.CONFLICT, "系统提示词名称已存在");
        }

        ChatPromptSystemItem entity = new ChatPromptSystemItem();
        entity.setTenantId(resolvedTenant);
        entity.setAppCode(request.appCode());
        entity.setModelRoute(request.modelRoute());
        entity.setPromptName(request.promptName());
        entity.setContent(request.content());
        entity.setPriority(request.priority());
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        entity.setVersion(1L);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setCreatedBy(resolvedOperator);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(resolvedOperator);
        systemItemMapper.insert(entity);
        return toSystemItemResponse(entity);
    }

    @Override
    public PromptItemResponse updateSystemPromptItem(String tenantId, Long id, String operator, SystemPromptItemUpdateRequest request) {
        String resolvedTenant = normalize(tenantId);
        String resolvedOperator = normalizeOperator(operator);
        ChatPromptSystemItem entity = systemItemMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1 || !resolvedTenant.equals(entity.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "系统提示词不存在");
        }
        entity.setContent(request.content());
        entity.setPriority(request.priority());
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        entity.setVersion(safeLong(entity.getVersion()) + 1);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(resolvedOperator);
        systemItemMapper.updateById(entity);
        return toSystemItemResponse(entity);
    }

    @Override
    public void deleteSystemPromptItem(String tenantId, Long id) {
        String resolvedTenant = normalize(tenantId);
        ChatPromptSystemItem entity = systemItemMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1 || !resolvedTenant.equals(entity.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "系统提示词不存在");
        }
        entity.setDeleted(1);
        entity.setUpdatedAt(LocalDateTime.now());
        systemItemMapper.updateById(entity);
    }

    @Override
    public List<PromptItemResponse> listSessionPromptItems(String tenantId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "sessionId 不能为空");
        }
        String resolvedTenant = normalize(tenantId);
        List<ChatSessionPromptItem> items = sessionItemMapper.selectList(new QueryWrapper<ChatSessionPromptItem>()
                .eq("tenant_id", resolvedTenant)
                .eq("session_id", sessionId)
                .eq("deleted", 0)
                .orderByAsc("priority")
                .orderByAsc("id"));
        return items.stream().map(this::toSessionItemResponse).collect(Collectors.toList());
    }

    @Override
    public PromptItemResponse createSessionPromptItem(String tenantId, String sessionId, String operator, SessionPromptItemCreateRequest request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "sessionId 不能为空");
        }
        String resolvedTenant = normalize(tenantId);
        String resolvedOperator = normalizeOperator(operator);
        LocalDateTime now = LocalDateTime.now();

        ChatSessionPromptItem exists = sessionItemMapper.selectOne(new QueryWrapper<ChatSessionPromptItem>()
                .eq("tenant_id", resolvedTenant)
                .eq("session_id", sessionId)
                .eq("prompt_name", request.promptName())
                .eq("deleted", 0)
                .last("limit 1"));
        if (exists != null) {
            throw new BizException(ErrorCode.CONFLICT, "会话提示词名称已存在");
        }

        ChatSessionPromptItem entity = new ChatSessionPromptItem();
        entity.setTenantId(resolvedTenant);
        entity.setSessionId(sessionId);
        entity.setPromptName(request.promptName());
        entity.setContent(request.content());
        entity.setPriority(request.priority());
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        entity.setVersion(1L);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setCreatedBy(resolvedOperator);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(resolvedOperator);
        sessionItemMapper.insert(entity);
        return toSessionItemResponse(entity);
    }

    @Override
    public PromptItemResponse updateSessionPromptItem(String tenantId, String sessionId, Long id, String operator, SessionPromptItemUpdateRequest request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "sessionId 不能为空");
        }
        String resolvedTenant = normalize(tenantId);
        String resolvedOperator = normalizeOperator(operator);
        ChatSessionPromptItem entity = sessionItemMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1 || !resolvedTenant.equals(entity.getTenantId()) || !sessionId.equals(entity.getSessionId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话提示词不存在");
        }
        entity.setContent(request.content());
        entity.setPriority(request.priority());
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        entity.setVersion(safeLong(entity.getVersion()) + 1);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(resolvedOperator);
        sessionItemMapper.updateById(entity);
        return toSessionItemResponse(entity);
    }

    @Override
    public void deleteSessionPromptItem(String tenantId, String sessionId, Long id) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "sessionId 不能为空");
        }
        String resolvedTenant = normalize(tenantId);
        ChatSessionPromptItem entity = sessionItemMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1 || !resolvedTenant.equals(entity.getTenantId()) || !sessionId.equals(entity.getSessionId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话提示词不存在");
        }
        entity.setDeleted(1);
        entity.setUpdatedAt(LocalDateTime.now());
        sessionItemMapper.updateById(entity);
    }

    @Override
    public String resolveEffectivePrompt(String tenantId, String sessionId, String appCode, String modelRoute) {
        String resolvedTenant = normalize(tenantId);
        String resolvedModelRoute = normalizeModelRoute(modelRoute);
        String systemPrompt = listEnabledSystemPromptContent(resolvedTenant, appCode, resolvedModelRoute);

        String sessionPrompt = listEnabledSessionPromptContent(resolvedTenant, sessionId);

        // 兼容旧单提示词配置
        if (systemPrompt.isBlank()) {
            ChatPromptSystem system = selectSystem(resolvedTenant, appCode, resolvedModelRoute);
            systemPrompt = system == null ? "" : nullToEmpty(system.getPublishedContent());
        }
        // 兼容默认路由：当指定模型路由无配置时，回退到 default
        if (systemPrompt.isBlank() && !"default".equals(resolvedModelRoute)) {
            systemPrompt = listEnabledSystemPromptContent(resolvedTenant, appCode, "default");
            if (systemPrompt.isBlank()) {
                ChatPromptSystem fallbackSystem = selectSystem(resolvedTenant, appCode, "default");
                systemPrompt = fallbackSystem == null ? "" : nullToEmpty(fallbackSystem.getPublishedContent());
            }
        }
        if (sessionPrompt.isBlank() && sessionId != null && !sessionId.isBlank()) {
            ChatSessionPrompt session = selectSessionPrompt(resolvedTenant, sessionId);
            if (session != null) {
                sessionPrompt = nullToEmpty(session.getContent());
            }
        }

        if (systemPrompt.isBlank()) {
            return sessionPrompt;
        }
        if (sessionPrompt.isBlank()) {
            return systemPrompt;
        }
        return systemPrompt + "\n\n" + sessionPrompt;
    }

    @Override
    public String resolveEffectivePromptHash(String tenantId, String sessionId, String appCode, String modelRoute) {
        String effectivePrompt = resolveEffectivePrompt(tenantId, sessionId, appCode, modelRoute);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(effectivePrompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "effectivePromptHash 计算失败");
        }
    }

    private String normalize(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

    private String normalizeOperator(String operator) {
        return (operator == null || operator.isBlank()) ? "unknown" : operator;
    }

    private String normalizeModelRoute(String modelRoute) {
        return (modelRoute == null || modelRoute.isBlank()) ? "default" : modelRoute.trim();
    }

    private ChatPromptSystem selectSystem(String tenantId, String appCode, String modelRoute) {
        return systemMapper.selectOne(new QueryWrapper<ChatPromptSystem>()
                .eq("tenant_id", tenantId)
                .eq("app_code", appCode)
                .eq("model_route", modelRoute)
                .eq("deleted", 0)
                .orderByDesc("id")
                .last("limit 1"));
    }

    private ChatSessionPrompt selectSessionPrompt(String tenantId, String sessionId) {
        return sessionPromptMapper.selectOne(new QueryWrapper<ChatSessionPrompt>()
                .eq("tenant_id", tenantId)
                .eq("session_id", sessionId)
                .eq("deleted", 0)
                .orderByDesc("id")
                .last("limit 1"));
    }

        private String listEnabledSystemPromptContent(String tenantId, String appCode, String modelRoute) {
        List<ChatPromptSystemItem> enabledItems = systemItemMapper.selectList(new QueryWrapper<ChatPromptSystemItem>()
            .eq("tenant_id", tenantId)
            .eq("app_code", appCode)
            .eq("model_route", modelRoute)
            .eq("enabled", 1)
            .eq("deleted", 0)
            .orderByAsc("priority")
            .orderByAsc("id"));
        return enabledItems.stream()
            .map(ChatPromptSystemItem::getContent)
            .map(this::nullToEmpty)
            .filter(item -> !item.isBlank())
            .collect(Collectors.joining("\n\n"));
        }

        private String listEnabledSessionPromptContent(String tenantId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }
        List<ChatSessionPromptItem> enabledItems = sessionItemMapper.selectList(new QueryWrapper<ChatSessionPromptItem>()
            .eq("tenant_id", tenantId)
            .eq("session_id", sessionId)
            .eq("enabled", 1)
            .eq("deleted", 0)
            .orderByAsc("priority")
            .orderByAsc("id"));
        return enabledItems.stream()
            .map(ChatSessionPromptItem::getContent)
            .map(this::nullToEmpty)
            .filter(item -> !item.isBlank())
            .collect(Collectors.joining("\n\n"));
        }

        private PromptItemResponse toSystemItemResponse(ChatPromptSystemItem item) {
        return new PromptItemResponse(
            item.getId(),
            "system",
            item.getAppCode(),
            item.getModelRoute(),
            null,
            item.getPromptName(),
            nullToEmpty(item.getContent()),
            item.getPriority(),
            item.getEnabled() != null && item.getEnabled() == 1,
            safeLong(item.getVersion()),
            item.getUpdatedAt()
        );
        }

        private PromptItemResponse toSessionItemResponse(ChatSessionPromptItem item) {
        return new PromptItemResponse(
            item.getId(),
            "session",
            null,
            null,
            item.getSessionId(),
            item.getPromptName(),
            nullToEmpty(item.getContent()),
            item.getPriority(),
            item.getEnabled() != null && item.getEnabled() == 1,
            safeLong(item.getVersion()),
            item.getUpdatedAt()
        );
        }

    private void insertAudit(String tenantId,
                             String appCode,
                             String modelRoute,
                             String action,
                             Long beforeVersion,
                             Long afterVersion,
                             String beforeContent,
                             String afterContent,
                             String operator,
                             LocalDateTime now) {
        ChatPromptSystemAudit audit = new ChatPromptSystemAudit();
        audit.setTenantId(tenantId);
        audit.setAppCode(appCode);
        audit.setModelRoute(modelRoute);
        audit.setAction(action);
        audit.setBeforeVersion(beforeVersion);
        audit.setAfterVersion(afterVersion);
        audit.setBeforeContent(beforeContent);
        audit.setAfterContent(afterContent);
        audit.setOperatorId(operator);
        audit.setOperatorName(operator);
        audit.setDiffSummary(buildDiffSummary(beforeContent, afterContent));
        audit.setCreatedAt(now);
        systemAuditMapper.insert(audit);
    }

    private String buildDiffSummary(String beforeContent, String afterContent) {
        String before = nullToEmpty(beforeContent);
        String after = nullToEmpty(afterContent);
        int beforeLen = before.length();
        int afterLen = after.length();
        int delta = afterLen - beforeLen;
        return "beforeLen=" + beforeLen + ",afterLen=" + afterLen + ",delta=" + delta;
    }

    private SystemPromptCurrentResponse toCurrentResponse(ChatPromptSystem current) {
        return new SystemPromptCurrentResponse(
                current.getTenantId(),
                current.getAppCode(),
                current.getModelRoute(),
                nullToEmpty(current.getDraftContent()),
                safeLong(current.getDraftVersion()),
                nullToEmpty(current.getPublishedContent()),
                safeLong(current.getPublishedVersion()),
                current.getPublishedAt()
        );
    }

    private SystemPromptCurrentResponse emptySystemCurrent(String tenantId, String appCode, String modelRoute) {
        return new SystemPromptCurrentResponse(tenantId, appCode, modelRoute, "", 0L, "", 0L, null);
    }

    private void throwVersionConflict(Long actual, Long expected) {
        throw new BizException(ErrorCode.CONFLICT, "PROMPT_VERSION_CONFLICT: expected=" + expected + ", actual=" + actual);
    }

    private void throwSystemConflict(Long actual, Long expected, String latestContent, LocalDateTime latestUpdatedAt) {
        throw new PromptConflictException(
                "PROMPT_VERSION_CONFLICT: expected=" + expected + ", actual=" + actual,
                buildConflictPayload("system", actual, latestContent, latestUpdatedAt)
        );
    }

    private void throwSessionConflict(Long actual, Long expected, String latestContent, LocalDateTime latestUpdatedAt) {
        throw new PromptConflictException(
                "PROMPT_VERSION_CONFLICT: expected=" + expected + ", actual=" + actual,
                buildConflictPayload("session", actual, latestContent, latestUpdatedAt)
        );
    }

    private Map<String, Object> buildConflictPayload(String scopeType, Long latestVersion, String latestContent, LocalDateTime latestUpdatedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scopeType", scopeType);
        payload.put("latestVersion", safeLong(latestVersion));
        payload.put("latestContent", nullToEmpty(latestContent));
        payload.put("latestUpdatedAt", latestUpdatedAt);
        return payload;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
