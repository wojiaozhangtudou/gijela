package com.gijela.morpheus.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chat.domain.dto.ChatHistoryMessageResponse;
import com.gijela.morpheus.chat.domain.dto.ChatSessionItemResponse;
import com.gijela.morpheus.chat.domain.entity.ChatAttachment;
import com.gijela.morpheus.chat.domain.entity.ChatConversation;
import com.gijela.morpheus.chat.domain.entity.ChatMessageEntity;
import com.gijela.morpheus.chat.mapper.ChatAttachmentMapper;
import com.gijela.morpheus.chat.mapper.ChatConversationMapper;
import com.gijela.morpheus.chat.mapper.ChatMessageMapper;
import com.gijela.morpheus.chat.service.ChatHistoryQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ChatHistoryQueryServiceImpl implements ChatHistoryQueryService {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
        private final ChatAttachmentMapper chatAttachmentMapper;

    public ChatHistoryQueryServiceImpl(ChatConversationMapper chatConversationMapper,
                                                                           ChatMessageMapper chatMessageMapper,
                                                                           ChatAttachmentMapper chatAttachmentMapper) {
        this.chatConversationMapper = chatConversationMapper;
        this.chatMessageMapper = chatMessageMapper;
                this.chatAttachmentMapper = chatAttachmentMapper;
    }

        @Override
        public Map<String, Object> createSession(String tenantId, String title, String model) {
                String normalizedTenant = normalizeTenant(tenantId);
                if (title == null || title.isBlank()) {
                        throw new IllegalArgumentException("title 不能为空");
                }
                String normalizedModel = normalizeModel(model);
                String normalizedTitle = title.trim();
                if (normalizedTitle.length() > 255) {
                        normalizedTitle = normalizedTitle.substring(0, 255);
                }
                String sessionId = "session-" + UUID.randomUUID().toString().replace("-", "");
                LocalDateTime now = LocalDateTime.now();

                ChatConversation conversation = new ChatConversation();
                conversation.setTenantId(normalizedTenant);
                conversation.setSessionId(sessionId);
                conversation.setTitle(normalizedTitle);
                conversation.setModel(normalizedModel);
                conversation.setSummary(null);
                conversation.setMessageCount(0);
                conversation.setCreatedAt(now);
                conversation.setUpdatedAt(now);
                chatConversationMapper.insert(conversation);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("tenantId", normalizedTenant);
                result.put("sessionId", sessionId);
                result.put("title", normalizedTitle);
                result.put("model", normalizedModel);
                result.put("messageCount", 0);
                result.put("status", "created");
                result.put("createdAt", now);
                return result;
        }

    @Override
    public List<ChatSessionItemResponse> listSessions(String tenantId, int limit) {
        String normalizedTenant = normalizeTenant(tenantId);
        int actualLimit = Math.max(1, Math.min(limit, 100));
        QueryWrapper<ChatConversation> wrapper = new QueryWrapper<ChatConversation>()
                .eq("tenant_id", normalizedTenant)
                .orderByDesc("updated_at")
                .last("limit " + actualLimit);
        return chatConversationMapper.selectList(wrapper).stream()
                .map(item -> new ChatSessionItemResponse(
                        item.getSessionId(),
                        item.getTitle(),
                        item.getSummary(),
                        item.getModel(),
                        item.getMessageCount(),
                        item.getUpdatedAt()
                ))
                .toList();
    }

    @Override
    public List<ChatHistoryMessageResponse> listMessages(String tenantId, String sessionId, int limit) {
        String normalizedTenant = normalizeTenant(tenantId);
        int actualLimit = Math.max(1, Math.min(limit, 200));
        QueryWrapper<ChatMessageEntity> wrapper = new QueryWrapper<ChatMessageEntity>()
                .eq("tenant_id", normalizedTenant)
                .eq("session_id", sessionId)
                .orderByDesc("id")
                .last("limit " + actualLimit);

        List<ChatMessageEntity> rows = chatMessageMapper.selectList(wrapper);
        List<ChatMessageEntity> orderedRows = new java.util.ArrayList<>(rows);
        java.util.Collections.reverse(orderedRows);
        return orderedRows.stream()
                                .map(item -> new ChatHistoryMessageResponse(
                                                item.getRole(),
                                                item.getContent(),
                                                item.getCreatedAt(),
                                                parseReferences(item.getReferencesJson())
                                ))
                .toList();
    }

    @Override
    public Map<String, Object> deleteSession(String tenantId, String sessionId) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        QueryWrapper<ChatMessageEntity> msgWrapper = new QueryWrapper<ChatMessageEntity>()
                .eq("tenant_id", normalizedTenant)
                .eq("session_id", sessionId);
        int deletedMessages = chatMessageMapper.delete(msgWrapper);

        QueryWrapper<ChatAttachment> attachmentWrapper = new QueryWrapper<ChatAttachment>()
                .eq("tenant_id", normalizedTenant)
                .eq("session_id", sessionId);
        int deletedAttachments = chatAttachmentMapper.delete(attachmentWrapper);

        QueryWrapper<ChatConversation> convWrapper = new QueryWrapper<ChatConversation>()
                .eq("tenant_id", normalizedTenant)
                .eq("session_id", sessionId);
        int deletedSessions = chatConversationMapper.delete(convWrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", normalizedTenant);
        result.put("sessionId", sessionId);
        result.put("deletedSessions", deletedSessions);
        result.put("deletedMessages", deletedMessages);
        result.put("deletedAttachments", deletedAttachments);
        result.put("status", deletedSessions > 0 ? "deleted" : "not_found");
        return result;
    }

        @Override
        public Map<String, Object> updateSessionTitle(String tenantId, String sessionId, String title, String model) {
                String normalizedTenant = normalizeTenant(tenantId);
                if (sessionId == null || sessionId.isBlank()) {
                        throw new IllegalArgumentException("sessionId 不能为空");
                }
                if (title == null || title.isBlank()) {
                        throw new IllegalArgumentException("title 不能为空");
                }
                String normalizedTitle = title.trim();
                if (normalizedTitle.length() > 255) {
                        normalizedTitle = normalizedTitle.substring(0, 255);
                }
                String normalizedModel = normalizeModel(model);

                ChatConversation updateEntity = new ChatConversation();
                updateEntity.setTitle(normalizedTitle);
                updateEntity.setModel(normalizedModel);
                updateEntity.setUpdatedAt(LocalDateTime.now());

                int affected = chatConversationMapper.update(
                                updateEntity,
                                new QueryWrapper<ChatConversation>()
                                                .eq("tenant_id", normalizedTenant)
                                                .eq("session_id", sessionId)
                );

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("tenantId", normalizedTenant);
                result.put("sessionId", sessionId);
                result.put("title", normalizedTitle);
                result.put("model", normalizedModel);
                result.put("updated", affected);
                result.put("status", affected > 0 ? "updated" : "not_found");
                return result;
        }

        private List<Map<String, Object>> parseReferences(String referencesJson) {
                if (referencesJson == null || referencesJson.isBlank()) {
                        return null;
                }
                try {
                        return OBJECT_MAPPER.readValue(referencesJson, new TypeReference<>() {
                        });
                } catch (Exception e) {
                        return null;
                }
        }

    private String normalizeTenant(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

        private String normalizeModel(String model) {
                return (model == null || model.isBlank()) ? "qwen-plus" : model.trim();
        }
}
