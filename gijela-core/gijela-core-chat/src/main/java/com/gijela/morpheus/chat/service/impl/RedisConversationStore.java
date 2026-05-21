package com.gijela.morpheus.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.ChatMessageDTO;
import com.gijela.morpheus.chat.domain.entity.ChatConversation;
import com.gijela.morpheus.chat.domain.entity.ChatMessageEntity;
import com.gijela.morpheus.chat.mapper.ChatConversationMapper;
import com.gijela.morpheus.chat.mapper.ChatMessageMapper;
import com.gijela.morpheus.chat.service.ConversationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class RedisConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationStore.class);

    private final ChatModuleProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public RedisConversationStore(ChatModuleProperties properties,
                                  StringRedisTemplate stringRedisTemplate,
                                  ChatConversationMapper chatConversationMapper,
                                  ChatMessageMapper chatMessageMapper) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.chatConversationMapper = chatConversationMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    public String ensureSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public void ensureConversationModel(String tenantId, String sessionId, String model) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String normalizedTenant = normalizeTenant(tenantId);
        String normalizedModel = normalizeModel(model);
        ChatConversation conversation = ensureConversationRow(normalizedTenant, sessionId);
        if (normalizedModel.equals(conversation.getModel())) {
            return;
        }
        ChatConversation updateEntity = new ChatConversation();
        updateEntity.setModel(normalizedModel);
        updateEntity.setUpdatedAt(LocalDateTime.now());
        chatConversationMapper.update(
                updateEntity,
                new UpdateWrapper<ChatConversation>()
                        .eq("tenant_id", normalizedTenant)
                        .eq("session_id", sessionId)
        );
    }

    @Override
    public void appendMessages(String tenantId, String sessionId, List<ChatMessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String normalizedTenant = normalizeTenant(tenantId);
        ChatConversation conversation = ensureConversationRow(normalizedTenant, sessionId);

        List<String> serialized = new ArrayList<>();
        for (ChatMessageDTO message : messages) {
            ChatMessageEntity entity = new ChatMessageEntity();
            entity.setTenantId(normalizedTenant);
            entity.setSessionId(sessionId);
            entity.setRole(message.role());
            entity.setContent(message.content());
            entity.setTokenCount(null);
            entity.setCreatedAt(LocalDateTime.now());
            chatMessageMapper.insert(entity);
            serialized.add(serialize(message));
        }

        String key = buildRecentMessageKey(normalizedTenant, sessionId);
        int maxSize = Math.max(properties.getConversation().getRecentMessageRounds() * 2, 20);
        cacheRecentMessages(key, serialized, maxSize);

        int currentCount = conversation.getMessageCount() == null ? 0 : conversation.getMessageCount();
        int newCount = currentCount + messages.size();
        boolean needRefreshSummary = shouldRefreshSummary(currentCount, newCount, messages);
        String resolvedTitle = resolveConversationTitle(conversation.getTitle(), messages);

        ChatConversation updateEntity = new ChatConversation();
        updateEntity.setMessageCount(newCount);
        updateEntity.setUpdatedAt(LocalDateTime.now());
        if (needRefreshSummary) {
            updateEntity.setSummary(buildConversationSummary(normalizedTenant, sessionId));
        }
        if (resolvedTitle != null) {
            updateEntity.setTitle(resolvedTitle);
        }

        chatConversationMapper.update(
            updateEntity,
            new UpdateWrapper<ChatConversation>()
                        .eq("tenant_id", normalizedTenant)
                        .eq("session_id", sessionId)
        );
    }


    @Override
    public void appendAssistantMessage(String tenantId, String sessionId, String content, List<Map<String, Object>> references) {
        if (content == null || content.isBlank()) {
            return;
        }
        String normalizedTenant = normalizeTenant(tenantId);
        ChatConversation conversation = ensureConversationRow(normalizedTenant, sessionId);

        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setTenantId(normalizedTenant);
        entity.setSessionId(sessionId);
        entity.setRole("assistant");
        entity.setContent(content);
        entity.setReferencesJson(toJson(references));
        entity.setTokenCount(null);
        entity.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(entity);

        String key = buildRecentMessageKey(normalizedTenant, sessionId);
        int maxSize = Math.max(properties.getConversation().getRecentMessageRounds() * 2, 20);
        cacheRecentMessages(key, List.of(serialize(new ChatMessageDTO("assistant", content))), maxSize);

        int currentCount = conversation.getMessageCount() == null ? 0 : conversation.getMessageCount();
        int newCount = currentCount + 1;

        ChatConversation updateEntity = new ChatConversation();
        updateEntity.setMessageCount(newCount);
        updateEntity.setUpdatedAt(LocalDateTime.now());
        chatConversationMapper.update(
            updateEntity,
            new UpdateWrapper<ChatConversation>()
                .eq("tenant_id", normalizedTenant)
                .eq("session_id", sessionId)
        );
    }
    @Override
    public List<ChatMessageDTO> getRecentMessages(String tenantId, String sessionId) {
        String normalizedTenant = normalizeTenant(tenantId);
        String key = buildRecentMessageKey(normalizedTenant, sessionId);
        List<String> cached = safeReadCachedMessages(key);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(this::deserialize).toList();
        }

        int maxSize = Math.max(properties.getConversation().getRecentMessageRounds() * 2, 20);
        QueryWrapper<ChatMessageEntity> wrapper = new QueryWrapper<ChatMessageEntity>()
                .eq("tenant_id", normalizedTenant)
                .eq("session_id", sessionId)
                .orderByDesc("id")
                .last("limit " + maxSize);
        List<ChatMessageEntity> rows = chatMessageMapper.selectList(wrapper);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<ChatMessageEntity> orderedRows = new ArrayList<>(rows);
        Collections.reverse(orderedRows);
        List<ChatMessageDTO> result = orderedRows.stream()
                .map(item -> new ChatMessageDTO(item.getRole(), item.getContent()))
                .toList();

        List<String> serialized = result.stream().map(this::serialize).toList();
        cacheRecentMessages(key, serialized, maxSize);
        return result;
    }

    private List<String> safeReadCachedMessages(String key) {
        try {
            return stringRedisTemplate.opsForList().range(key, 0, -1);
        } catch (RuntimeException e) {
            log.warn("redis read recent messages failed, fallback mysql. key={}", key, e);
            return List.of();
        }
    }

    private void cacheRecentMessages(String key, List<String> serialized, int maxSize) {
        try {
            if (serialized != null && !serialized.isEmpty()) {
                stringRedisTemplate.opsForList().rightPushAll(key, serialized);
            }
            stringRedisTemplate.opsForList().trim(key, -maxSize, -1);
        } catch (RuntimeException e) {
            log.warn("redis write recent messages failed, keep mysql as source of truth. key={}", key, e);
        }
    }

    private ChatConversation ensureConversationRow(String tenantId, String sessionId) {
        QueryWrapper<ChatConversation> wrapper = new QueryWrapper<ChatConversation>()
                .eq("tenant_id", tenantId)
                .eq("session_id", sessionId)
                .last("limit 1");
        ChatConversation existing = chatConversationMapper.selectOne(wrapper);
        if (existing != null) {
            return existing;
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setTenantId(tenantId);
        conversation.setSessionId(sessionId);
        conversation.setTitle("新会话");
        conversation.setModel(null);
        conversation.setSummary(null);
        conversation.setMessageCount(0);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        chatConversationMapper.insert(conversation);
        return conversation;
    }

    private boolean shouldRefreshSummary(int oldMessageCount, int newMessageCount, List<ChatMessageDTO> messages) {
        int refreshRounds = properties.getConversation().getSummaryRefreshRounds();
        int oldRounds = Math.max(oldMessageCount, 0) / 2;
        int newRounds = Math.max(newMessageCount, 0) / 2;
        boolean hitRoundRefresh = refreshRounds > 0
                && newRounds > oldRounds
                && newRounds > 0
                && newRounds % refreshRounds == 0;

        int tokenThreshold = properties.getConversation().getSummaryRefreshTokenThreshold();
        int appendedTokenCount = estimateTokenCount(messages);
        boolean hitTokenRefresh = tokenThreshold > 0 && appendedTokenCount >= tokenThreshold;

        return hitRoundRefresh || hitTokenRefresh;
    }

    private int estimateTokenCount(List<ChatMessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int charCount = messages.stream()
                .map(ChatMessageDTO::content)
                .filter(Objects::nonNull)
                .mapToInt(String::length)
                .sum();
        if (charCount <= 0) {
            return 0;
        }
        return (charCount + 3) / 4;
    }

    private String buildConversationSummary(String tenantId, String sessionId) {
        List<ChatMessageDTO> recentMessages = getRecentMessages(tenantId, sessionId);
        if (recentMessages == null || recentMessages.isEmpty()) {
            return null;
        }
        int start = Math.max(recentMessages.size() - 6, 0);
        String summary = recentMessages.subList(start, recentMessages.size()).stream()
                .map(this::toSummarySnippet)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + " | " + right)
                .orElse(null);
        if (summary == null) {
            return null;
        }
        return summary.length() > 512 ? summary.substring(0, 512) : summary;
    }

    private String toSummarySnippet(ChatMessageDTO message) {
        if (message == null || message.content() == null || message.content().isBlank()) {
            return null;
        }
        String content = message.content().replace('\n', ' ').trim();
        String clipped = content.length() > 64 ? content.substring(0, 64) : content;
        return message.role() + ":" + clipped;
    }

    private String buildRecentMessageKey(String tenantId, String sessionId) {
        return properties.getConversation().getRedisKeyPrefix() + ":recent:" + tenantId + ":" + sessionId;
    }

    private String normalizeTenant(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

    private String normalizeModel(String model) {
        return (model == null || model.isBlank()) ? "qwen-plus" : model.trim();
    }

    private String resolveConversationTitle(String currentTitle, List<ChatMessageDTO> messages) {
        boolean needRename = currentTitle == null || currentTitle.isBlank() || "新会话".equals(currentTitle);
        if (!needRename || messages == null || messages.isEmpty()) {
            return null;
        }
        for (ChatMessageDTO message : messages) {
            if (message == null || message.role() == null || message.content() == null) {
                continue;
            }
            if (!"user".equalsIgnoreCase(message.role())) {
                continue;
            }
            String content = message.content().replace('\n', ' ').trim();
            if (content.isBlank()) {
                continue;
            }
            return content.length() > 24 ? content.substring(0, 24) : content;
        }
        return null;
    }

    private String serialize(ChatMessageDTO message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.warn("serialize chat message failed", e);
            return "{\"role\":\"" + message.role() + "\",\"content\":\"\"}";
        }
    }

    private ChatMessageDTO deserialize(String text) {
        try {
            return objectMapper.readValue(text, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("deserialize chat message failed", e);
            return new ChatMessageDTO("assistant", "");
        }
    }

    private String toJson(List<Map<String, Object>> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            log.warn("serialize references failed, ignore. size={}", references.size(), e);
            return null;
        }
    }

}
