package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.ChatMessageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryConversationStoreTest {

    private InMemoryConversationStore store;

    @BeforeEach
    void setUp() {
        ChatModuleProperties properties = new ChatModuleProperties();
        properties.getConversation().setRecentMessageRounds(2);
        properties.getConversation().setSummaryRefreshRounds(2);
        properties.getConversation().setSummaryRefreshTokenThreshold(6000);
        store = new InMemoryConversationStore(properties);
    }

    @Test
    void appendMessages_shouldKeepRecentWindow() {
        List<ChatMessageDTO> batch = new java.util.ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            batch.add(new ChatMessageDTO(i % 2 == 0 ? "assistant" : "user", "m" + i));
        }
        store.appendMessages("tenant-a", "session-1", batch);

        List<ChatMessageDTO> recent = store.getRecentMessages("tenant-a", "session-1");
        assertEquals(20, recent.size());
        assertEquals("m6", recent.get(0).content());
        assertEquals("m25", recent.get(19).content());
    }

    @Test
    void appendMessages_shouldRefreshSummaryWhenRoundThresholdReached() {
        store.appendMessages("tenant-a", "session-1", List.of(
                new ChatMessageDTO("user", "hello"),
                new ChatMessageDTO("assistant", "world"),
                new ChatMessageDTO("user", "next"),
                new ChatMessageDTO("assistant", "round")
        ));

        String summary = store.getSummaryForTest("tenant-a", "session-1");
        assertNotNull(summary);
        assertEquals("user:hello | assistant:world | user:next | assistant:round", summary);
    }

    @Test
    void appendMessages_shouldNotRefreshSummaryBeforeThreshold() {
        store.appendMessages("tenant-a", "session-1", List.of(
                new ChatMessageDTO("user", "only"),
                new ChatMessageDTO("assistant", "one-round")
        ));

        assertNull(store.getSummaryForTest("tenant-a", "session-1"));
    }
}
