package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.ChatMessageDTO;
import com.gijela.morpheus.chat.domain.entity.ChatConversation;
import com.gijela.morpheus.chat.domain.entity.ChatMessageEntity;
import com.gijela.morpheus.chat.mapper.ChatConversationMapper;
import com.gijela.morpheus.chat.mapper.ChatMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisConversationStoreTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private ChatConversationMapper chatConversationMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    private RedisConversationStore store;

    @BeforeEach
    void setUp() {
        ChatModuleProperties properties = new ChatModuleProperties();
        properties.getConversation().setRedisKeyPrefix("chat:session");
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        store = new RedisConversationStore(properties, stringRedisTemplate, chatConversationMapper, chatMessageMapper);
    }

    @Test
    void appendMessages_shouldWriteMysqlAndRedis() {
        when(chatConversationMapper.selectOne(any())).thenReturn(null);

        store.appendMessages("tenant-a", "session-1", List.of(new ChatMessageDTO("user", "你好")));

        verify(chatConversationMapper).insert(any(ChatConversation.class));
        verify(chatMessageMapper).insert(any(ChatMessageEntity.class));
        verify(listOperations).rightPushAll(eq("chat:session:recent:tenant-a:session-1"), any(List.class));
        verify(listOperations).trim("chat:session:recent:tenant-a:session-1", -40, -1);
        ArgumentCaptor<ChatConversation> updateCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationMapper).update(updateCaptor.capture(), any());
        assertEquals(1, updateCaptor.getValue().getMessageCount());
    }

    @Test
    void getRecentMessages_shouldReadMysqlWhenRedisCacheEmpty() {
        when(listOperations.range("chat:session:recent:tenant-a:session-1", 0, -1)).thenReturn(List.of());

        ChatMessageEntity latest = new ChatMessageEntity();
        latest.setRole("assistant");
        latest.setContent("第二条");
        ChatMessageEntity older = new ChatMessageEntity();
        older.setRole("user");
        older.setContent("第一条");
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(latest, older));

        List<ChatMessageDTO> result = store.getRecentMessages("tenant-a", "session-1");

        assertEquals(2, result.size());
        assertEquals("第一条", result.get(0).content());
        assertEquals("第二条", result.get(1).content());

        ArgumentCaptor<List<String>> cachePayloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(listOperations).rightPushAll(eq("chat:session:recent:tenant-a:session-1"), cachePayloadCaptor.capture());
        assertFalse(cachePayloadCaptor.getValue().isEmpty());
    }

    @Test
    void appendMessages_shouldNotFailWhenRedisWriteThrows() {
        when(chatConversationMapper.selectOne(any())).thenReturn(null);
        doThrow(new RuntimeException("redis down")).when(listOperations).rightPushAll(eq("chat:session:recent:tenant-a:session-1"), any(List.class));

        assertDoesNotThrow(() -> store.appendMessages("tenant-a", "session-1", List.of(new ChatMessageDTO("user", "hello"))));

        verify(chatMessageMapper).insert(any(ChatMessageEntity.class));
        verify(chatConversationMapper).update(any(ChatConversation.class), any());
    }

    @Test
    void getRecentMessages_shouldFallbackMysqlWhenRedisReadThrows() {
        doThrow(new RuntimeException("redis timeout")).when(listOperations).range("chat:session:recent:tenant-a:session-1", 0, -1);

        ChatMessageEntity only = new ChatMessageEntity();
        only.setRole("assistant");
        only.setContent("fallback-ok");
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(only));

        List<ChatMessageDTO> result = store.getRecentMessages("tenant-a", "session-1");

        assertEquals(1, result.size());
        assertEquals("fallback-ok", result.get(0).content());
    }

    @Test
    void appendMessages_shouldRefreshSummaryWhenRoundThresholdReached() {
        ChatModuleProperties properties = new ChatModuleProperties();
        properties.getConversation().setRedisKeyPrefix("chat:session");
        properties.getConversation().setSummaryRefreshRounds(2);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        store = new RedisConversationStore(properties, stringRedisTemplate, chatConversationMapper, chatMessageMapper);

        ChatConversation existing = new ChatConversation();
        existing.setTenantId("tenant-a");
        existing.setSessionId("session-1");
        existing.setMessageCount(3);
        when(chatConversationMapper.selectOne(any())).thenReturn(existing);
        when(listOperations.range("chat:session:recent:tenant-a:session-1", 0, -1))
                .thenReturn(List.of("{\"role\":\"user\",\"content\":\"hello summary\"}"));

        store.appendMessages("tenant-a", "session-1", List.of(new ChatMessageDTO("assistant", "ok")));

        ArgumentCaptor<ChatConversation> updateCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationMapper).update(updateCaptor.capture(), any());
        ChatConversation updated = updateCaptor.getValue();
        assertEquals(4, updated.getMessageCount());
        assertEquals("user:hello summary", updated.getSummary());
    }

    @Test
    void appendMessages_shouldRenameDefaultTitleWithFirstUserMessage() {
        ChatConversation existing = new ChatConversation();
        existing.setTenantId("tenant-a");
        existing.setSessionId("session-1");
        existing.setTitle("新会话");
        existing.setMessageCount(0);
        when(chatConversationMapper.selectOne(any())).thenReturn(existing);

        store.appendMessages("tenant-a", "session-1", List.of(new ChatMessageDTO("user", "设计模式分类与实践")));

        ArgumentCaptor<ChatConversation> updateCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationMapper).update(updateCaptor.capture(), any());
        assertEquals("设计模式分类与实践", updateCaptor.getValue().getTitle());
    }
}
