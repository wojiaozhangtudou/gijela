package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.domain.dto.ChatHistoryMessageResponse;
import com.gijela.morpheus.chat.domain.dto.ChatSessionItemResponse;
import com.gijela.morpheus.chat.domain.entity.ChatAttachment;
import com.gijela.morpheus.chat.domain.entity.ChatConversation;
import com.gijela.morpheus.chat.domain.entity.ChatMessageEntity;
import com.gijela.morpheus.chat.mapper.ChatAttachmentMapper;
import com.gijela.morpheus.chat.mapper.ChatConversationMapper;
import com.gijela.morpheus.chat.mapper.ChatMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryQueryServiceImplTest {

    @Mock
    private ChatConversationMapper chatConversationMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatAttachmentMapper chatAttachmentMapper;

    @InjectMocks
    private ChatHistoryQueryServiceImpl service;

    @Test
    void listSessions_shouldMapConversationRows() {
        ChatConversation c1 = new ChatConversation();
        c1.setSessionId("s-1");
        c1.setTitle("会话1");
        c1.setSummary("摘要1");
        c1.setMessageCount(5);
        c1.setUpdatedAt(LocalDateTime.of(2026, 4, 28, 10, 0));

        when(chatConversationMapper.selectList(any())).thenReturn(List.of(c1));

        List<ChatSessionItemResponse> result = service.listSessions("tenant-a", 20);

        assertEquals(1, result.size());
        assertEquals("s-1", result.get(0).sessionId());
        assertEquals("会话1", result.get(0).title());
        assertEquals("摘要1", result.get(0).summary());
        assertEquals(5, result.get(0).messageCount());
    }

    @Test
    void listMessages_shouldReverseMysqlDescRowsToChronologicalOrder() {
        ChatMessageEntity latest = new ChatMessageEntity();
        latest.setRole("assistant");
        latest.setContent("第二条");
        latest.setCreatedAt(LocalDateTime.of(2026, 4, 28, 10, 2));

        ChatMessageEntity older = new ChatMessageEntity();
        older.setRole("user");
        older.setContent("第一条");
        older.setCreatedAt(LocalDateTime.of(2026, 4, 28, 10, 1));

        when(chatMessageMapper.selectList(any())).thenReturn(List.of(latest, older));

        List<ChatHistoryMessageResponse> result = service.listMessages("tenant-a", "s-1", 100);

        assertEquals(2, result.size());
        assertEquals("第一条", result.get(0).content());
        assertEquals("第二条", result.get(1).content());
    }

    @Test
    void deleteSession_shouldDeleteConversationMessagesAndAttachments() {
        when(chatMessageMapper.delete(any())).thenReturn(3);
        when(chatAttachmentMapper.delete(any())).thenReturn(1);
        when(chatConversationMapper.delete(any())).thenReturn(1);

        Map<String, Object> result = service.deleteSession("tenant-a", "s-1");

        assertEquals("deleted", result.get("status"));
        assertEquals(1, result.get("deletedSessions"));
        assertEquals(3, result.get("deletedMessages"));
        assertEquals(1, result.get("deletedAttachments"));
    }

    @Test
    void updateSessionTitle_shouldUpdateConversationTitle() {
        when(chatConversationMapper.update(any(), any())).thenReturn(1);

        Map<String, Object> result = service.updateSessionTitle("tenant-a", "s-1", "重命名会话");

        assertEquals("updated", result.get("status"));
        assertEquals("重命名会话", result.get("title"));
        assertEquals(1, result.get("updated"));
    }
}
