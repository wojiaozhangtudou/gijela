package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.ChatMessageDTO;
import com.gijela.morpheus.chat.service.ConversationStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConversationStore implements ConversationStore {

    private final ChatModuleProperties properties;
    private final Map<String, ConversationState> store = new ConcurrentHashMap<>();

    public InMemoryConversationStore(ChatModuleProperties properties) {
        this.properties = properties;
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
        // 内存实现仅用于测试/兜底，模型信息不参与历史回放逻辑。
    }

    @Override
    public void appendMessages(String tenantId, String sessionId, List<ChatMessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String key = buildKey(tenantId, sessionId);
        ConversationState state = store.computeIfAbsent(key, ignored -> new ConversationState());
        List<ChatMessageDTO> history = state.history;
        int oldMessageCount = state.messageCount;
        history.addAll(messages);
        int newMessageCount = oldMessageCount + messages.size();
        state.messageCount = newMessageCount;

        if (shouldRefreshSummary(oldMessageCount, newMessageCount, messages)) {
            state.summary = buildConversationSummary(history);
        }

        int maxSize = Math.max(properties.getConversation().getRecentMessageRounds() * 2, 20);
        if (history.size() > maxSize) {
            int fromIndex = Math.max(history.size() - maxSize, 0);
            state.history = new ArrayList<>(history.subList(fromIndex, history.size()));
        }
    }

    @Override
    public List<ChatMessageDTO> getRecentMessages(String tenantId, String sessionId) {
        ConversationState state = store.get(buildKey(tenantId, sessionId));
        if (state == null) {
            return List.of();
        }
        return new ArrayList<>(state.history);
    }

    String getSummaryForTest(String tenantId, String sessionId) {
        ConversationState state = store.get(buildKey(tenantId, sessionId));
        return state == null ? null : state.summary;
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

    private String buildConversationSummary(List<ChatMessageDTO> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        int start = Math.max(history.size() - 6, 0);
        String summary = history.subList(start, history.size()).stream()
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

    private String buildKey(String tenantId, String sessionId) {
        return (tenantId == null || tenantId.isBlank() ? "default" : tenantId) + ":" + sessionId;
    }

    private static class ConversationState {
        private List<ChatMessageDTO> history = new ArrayList<>();
        private int messageCount = 0;
        private String summary;
    }
}
