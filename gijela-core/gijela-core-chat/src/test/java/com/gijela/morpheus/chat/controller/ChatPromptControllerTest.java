package com.gijela.morpheus.chat.controller;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.prompt.SessionPromptUpdateRequest;
import com.gijela.morpheus.chat.domain.dto.prompt.SystemPromptDraftUpdateRequest;
import com.gijela.morpheus.chat.domain.vo.prompt.SessionPromptResponse;
import com.gijela.morpheus.chat.domain.vo.prompt.SystemPromptCurrentResponse;
import com.gijela.morpheus.chat.service.PromptConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPromptControllerTest {

    @Mock
    private PromptConfigService promptConfigService;

    @Mock
    private HttpServletRequest request;

    private ChatPromptController controller;

    @BeforeEach
    void setUp() {
        ChatModuleProperties properties = new ChatModuleProperties();
        controller = new ChatPromptController(promptConfigService, properties);
    }

    @Test
    void getSystemCurrent_shouldUseTenantHeader() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(promptConfigService.getSystemCurrent("tenant-a", "chat", "default"))
                .thenReturn(new SystemPromptCurrentResponse("tenant-a", "chat", "default", "draft", 2L, "published", 1L, LocalDateTime.now()));

        var response = controller.getSystemCurrent("chat", "default", request);

        assertEquals(0, response.getCode());
        assertEquals("draft", response.getData().draftContent());
        verify(promptConfigService).getSystemCurrent("tenant-a", "chat", "default");
    }

    @Test
    void getSessionPrompt_shouldUseTenantHeader() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(promptConfigService.getSessionPrompt("tenant-a", "session-1"))
                .thenReturn(new SessionPromptResponse("session-1", "会话提示词", 3L, LocalDateTime.now()));

        var response = controller.getSessionPrompt("session-1", request);

        assertEquals(0, response.getCode());
        assertEquals("会话提示词", response.getData().content());
        verify(promptConfigService).getSessionPrompt("tenant-a", "session-1");
    }

    @Test
    void saveSystemDraft_shouldUseOperatorHeader() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Operator")).thenReturn("tester");
        var body = new SystemPromptDraftUpdateRequest("chat", "default", "new draft", 2L);
        when(promptConfigService.saveSystemDraft("tenant-a", "tester", body))
                .thenReturn(new SystemPromptCurrentResponse("tenant-a", "chat", "default", "new draft", 3L, "published", 1L, LocalDateTime.now()));

        var response = controller.saveSystemDraft(body, request);

        assertEquals(0, response.getCode());
        assertEquals(3L, response.getData().draftVersion());
        verify(promptConfigService).saveSystemDraft("tenant-a", "tester", body);
    }

    @Test
    void updateSessionPrompt_shouldFallbackOperatorToUnknown() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Operator")).thenReturn(null);
        var body = new SessionPromptUpdateRequest("会话草稿", 0L);
        when(promptConfigService.updateSessionPrompt("tenant-a", "session-1", "unknown", body))
                .thenReturn(new SessionPromptResponse("session-1", "会话草稿", 1L, LocalDateTime.now()));

        var response = controller.updateSessionPrompt("session-1", body, request);

        assertEquals(0, response.getCode());
        assertEquals(1L, response.getData().version());
        verify(promptConfigService).updateSessionPrompt("tenant-a", "session-1", "unknown", body);
    }
}
