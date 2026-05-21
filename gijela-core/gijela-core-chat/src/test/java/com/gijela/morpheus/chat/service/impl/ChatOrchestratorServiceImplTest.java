package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.adapter.llm.OpenAiChatAdapter;
import com.gijela.morpheus.chat.domain.dto.ChatCompletionRequest;
import com.gijela.morpheus.chat.domain.dto.ChatCompletionResponse;
import com.gijela.morpheus.chat.domain.dto.ChatMessageDTO;
import com.gijela.morpheus.chat.domain.dto.StreamChatRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.ChatEventVO;
import com.gijela.morpheus.chat.service.ChatAuditService;
import com.gijela.morpheus.chat.service.ConversationStore;
import com.gijela.morpheus.chat.service.KnowledgeIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatOrchestratorServiceImplTest {

    @Mock
    private OpenAiChatAdapter openAiChatAdapter;

    @Mock
    private ConversationStore conversationStore;

    @Mock
    private ChatAuditService chatAuditService;

    @Mock
    private KnowledgeIngestionService knowledgeIngestionService;

    @Test
    void stream_shouldAppendAssistantAndAuditSuccessOnDone() {
        ChatOrchestratorServiceImpl service = new ChatOrchestratorServiceImpl(openAiChatAdapter, conversationStore, chatAuditService, knowledgeIngestionService);
        ChatContext context = new ChatContext("tenant-a", "req-1", null);
        StreamChatRequest request = new StreamChatRequest(
                "session-1",
                List.of(new ChatMessageDTO("user", "你好")),
                "gpt-4o-mini",
                0.2,
                1024,
                List.of("time.now")
        );

        when(conversationStore.ensureSessionId("session-1")).thenReturn("session-1");
        when(conversationStore.getRecentMessages("tenant-a", "session-1")).thenReturn(List.of());

        doAnswer(invocation -> {
            Consumer<ChatEventVO> consumer = invocation.getArgument(4);
            consumer.accept(new ChatEventVO("delta", "session-1", "片段", null, null, null, null, null));
            consumer.accept(new ChatEventVO("done", "session-1", "完整回答", null, null, null, null, null));
            return null;
        }).when(openAiChatAdapter).stream(eq(context), eq("session-1"), eq(request), any(), any());

        List<ChatEventVO> collected = new ArrayList<>();
        service.stream(context, request, collected::add);

        verify(conversationStore).appendMessages("tenant-a", "session-1", request.messages());
        verify(conversationStore).appendAssistantMessage("tenant-a", "session-1", "完整回答", null);
        verify(chatAuditService).record(eq("tenant-a"), eq("req-1"), eq("session-1"), eq("chat.stream"),
            argThat(result -> result != null && result.startsWith("SUCCESS;latencyMs=")));
        verify(knowledgeIngestionService, never()).search(any(), any());
        assertEquals(2, collected.size());
    }

    @Test
    void stream_shouldAuditErrorWhenReceiveErrorEvent() {
        ChatOrchestratorServiceImpl service = new ChatOrchestratorServiceImpl(openAiChatAdapter, conversationStore, chatAuditService, knowledgeIngestionService);
        ChatContext context = new ChatContext("tenant-a", "req-2", null);
        StreamChatRequest request = new StreamChatRequest(
                "session-2",
                List.of(new ChatMessageDTO("user", "hello")),
                null,
                null,
                null,
                null
        );

        when(conversationStore.ensureSessionId("session-2")).thenReturn("session-2");
        when(conversationStore.getRecentMessages("tenant-a", "session-2")).thenReturn(List.of());

        doAnswer(invocation -> {
            Consumer<ChatEventVO> consumer = invocation.getArgument(4);
            consumer.accept(new ChatEventVO("error", "session-2", null, null, null, "upstream timeout", null, null));
            return null;
        }).when(openAiChatAdapter).stream(eq(context), eq("session-2"), eq(request), any(), any());

        service.stream(context, request, event -> {
        });

        verify(chatAuditService).record(eq("tenant-a"), eq("req-2"), eq("session-2"), eq("chat.stream"),
            argThat(result -> result != null && result.startsWith("ERROR:upstream timeout;latencyMs=")));
        verify(conversationStore, never()).appendAssistantMessage(eq("tenant-a"), eq("session-2"), any(), any());
    }

    @Test
    void stream_shouldUseToolResultHitsAsReferences() {
        ChatOrchestratorServiceImpl service = new ChatOrchestratorServiceImpl(openAiChatAdapter, conversationStore, chatAuditService, knowledgeIngestionService);
        ChatContext context = new ChatContext("tenant-a", "req-5", null);
        StreamChatRequest request = new StreamChatRequest(
                "session-5",
                List.of(new ChatMessageDTO("user", "讲讲设计模式")),
                "gpt-4o-mini",
                0.2,
                1024,
                List.of("knowledge.search")
        );

        when(conversationStore.ensureSessionId("session-5")).thenReturn("session-5");
        when(conversationStore.getRecentMessages("tenant-a", "session-5")).thenReturn(List.of());

        List<Map<String, Object>> hits = List.of(Map.of("id", "k1", "title", "23种设计模式", "score", 0.9));
        Map<String, Object> toolResult = Map.of("result", Map.of("hits", hits));

        doAnswer(invocation -> {
            Consumer<ChatEventVO> consumer = invocation.getArgument(4);
            consumer.accept(new ChatEventVO("tool_result", "session-5", null, null, toolResult, null, null, null));
            consumer.accept(new ChatEventVO("delta", "session-5", "片段", null, null, null, null, null));
            consumer.accept(new ChatEventVO("done", "session-5", "完整回答", null, null, null, null, null));
            return null;
        }).when(openAiChatAdapter).stream(eq(context), eq("session-5"), eq(request), any(), any());

        List<ChatEventVO> collected = new ArrayList<>();
        service.stream(context, request, collected::add);

        ChatEventVO doneEvent = collected.stream().filter(e -> "done".equals(e.type())).findFirst().orElseThrow();
        assertEquals(1, doneEvent.references().size());
        assertEquals("23种设计模式", doneEvent.references().get(0).get("title"));
        verify(conversationStore).appendAssistantMessage("tenant-a", "session-5", "完整回答", hits);
        verify(knowledgeIngestionService, never()).search(any(), any());
    }

    @Test
    void stream_shouldUseToolResultDataHitsAsReferences() {
        ChatOrchestratorServiceImpl service = new ChatOrchestratorServiceImpl(openAiChatAdapter, conversationStore, chatAuditService, knowledgeIngestionService);
        ChatContext context = new ChatContext("tenant-a", "req-6", null);
        StreamChatRequest request = new StreamChatRequest(
                "session-6",
                List.of(new ChatMessageDTO("user", "知识库里面有提到设计模式么")),
                "gpt-4o-mini",
                0.2,
                1024,
                List.of("knowledge.search")
        );

        when(conversationStore.ensureSessionId("session-6")).thenReturn("session-6");
        when(conversationStore.getRecentMessages("tenant-a", "session-6")).thenReturn(List.of());

        List<Map<String, Object>> hits = List.of(Map.of("id", "k2", "title", "设计模式速览", "score", 0.88));
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("success", true);
        wrapped.put("data", Map.of("hits", hits));
        wrapped.put("error", null);
        wrapped.put("meta", Map.of("skillId", "knowledge.search", "version", "1.0.0", "latencyMs", 20));
        Map<String, Object> toolResult = Map.of("result", wrapped);

        doAnswer(invocation -> {
            Consumer<ChatEventVO> consumer = invocation.getArgument(4);
            consumer.accept(new ChatEventVO("tool_result", "session-6", null, null, toolResult, null, null, null));
            consumer.accept(new ChatEventVO("done", "session-6", "完整回答", null, null, null, null, null));
            return null;
        }).when(openAiChatAdapter).stream(eq(context), eq("session-6"), eq(request), any(), any());

        List<ChatEventVO> collected = new ArrayList<>();
        service.stream(context, request, collected::add);

        ChatEventVO doneEvent = collected.stream().filter(e -> "done".equals(e.type())).findFirst().orElseThrow();
        assertEquals(1, doneEvent.references().size());
        assertEquals("设计模式速览", doneEvent.references().get(0).get("title"));
        verify(conversationStore).appendAssistantMessage("tenant-a", "session-6", "完整回答", hits);
    }

    @Test
    void stream_shouldUseAttachmentItemsAsReferences() {
        ChatOrchestratorServiceImpl service = new ChatOrchestratorServiceImpl(openAiChatAdapter, conversationStore, chatAuditService, knowledgeIngestionService);
        ChatContext context = new ChatContext("tenant-a", "req-7", null);
        StreamChatRequest request = new StreamChatRequest(
                "session-7",
                List.of(new ChatMessageDTO("user", "调用 attachment.context 看看大模型如何部署")),
                "gpt-4o-mini",
                0.2,
                1024,
                List.of("attachment.context")
        );

        when(conversationStore.ensureSessionId("session-7")).thenReturn("session-7");
        when(conversationStore.getRecentMessages("tenant-a", "session-7")).thenReturn(List.of());

        List<Map<String, Object>> items = List.of(
                Map.of("id", 101L, "fileName", "大模型本地化部署", "summary", "部署摘要")
        );
        Map<String, Object> toolResult = Map.of("result", Map.of("items", items, "count", 1));

        doAnswer(invocation -> {
            Consumer<ChatEventVO> consumer = invocation.getArgument(4);
            consumer.accept(new ChatEventVO("tool_result", "session-7", null, null, toolResult, null, null, null));
            consumer.accept(new ChatEventVO("done", "session-7", "完整回答", null, null, null, null, null));
            return null;
        }).when(openAiChatAdapter).stream(eq(context), eq("session-7"), eq(request), any(), any());

        List<ChatEventVO> collected = new ArrayList<>();
        service.stream(context, request, collected::add);

        ChatEventVO doneEvent = collected.stream().filter(e -> "done".equals(e.type())).findFirst().orElseThrow();
        assertEquals(1, doneEvent.references().size());
        assertEquals("大模型本地化部署", doneEvent.references().get(0).get("title"));
        verify(conversationStore).appendAssistantMessage(eq("tenant-a"), eq("session-7"), eq("完整回答"), any());
    }

        @Test
        void complete_shouldAuditSuccessWithLatency() {
        ChatOrchestratorServiceImpl service = new ChatOrchestratorServiceImpl(openAiChatAdapter, conversationStore, chatAuditService, knowledgeIngestionService);
        ChatContext context = new ChatContext("tenant-a", "req-3", null);
        ChatCompletionRequest request = new ChatCompletionRequest(
            "session-3",
            List.of(new ChatMessageDTO("user", "hi")),
            null,
            null,
            null,
            null
        );
        ChatCompletionResponse response = new ChatCompletionResponse("session-3", "ok", "stop", null, null);

        when(conversationStore.ensureSessionId("session-3")).thenReturn("session-3");
        when(conversationStore.getRecentMessages("tenant-a", "session-3")).thenReturn(List.of());
        when(openAiChatAdapter.complete(context, "session-3", request, request.messages())).thenReturn(response);

        ChatCompletionResponse actual = service.complete(context, request);

        assertEquals("ok", actual.content());
        verify(conversationStore).appendMessages("tenant-a", "session-3", request.messages());
        verify(conversationStore).appendAssistantMessage("tenant-a", "session-3", "ok", null);
        verify(chatAuditService).record(eq("tenant-a"), eq("req-3"), eq("session-3"), eq("chat.complete"),
            argThat(result -> result != null && result.startsWith("SUCCESS;latencyMs=")));
        verify(knowledgeIngestionService, never()).search(any(), any());
        }

        @Test
        void complete_shouldAuditErrorWithLatencyWhenAdapterThrows() {
        ChatOrchestratorServiceImpl service = new ChatOrchestratorServiceImpl(openAiChatAdapter, conversationStore, chatAuditService, knowledgeIngestionService);
        ChatContext context = new ChatContext("tenant-a", "req-4", null);
        ChatCompletionRequest request = new ChatCompletionRequest(
            "session-4",
            List.of(new ChatMessageDTO("user", "hi")),
            null,
            null,
            null,
            null
        );

        when(conversationStore.ensureSessionId("session-4")).thenReturn("session-4");
        when(conversationStore.getRecentMessages("tenant-a", "session-4")).thenReturn(List.of());
        doThrow(new RuntimeException("llm failed")).when(openAiChatAdapter)
            .complete(context, "session-4", request, request.messages());

        assertThrows(RuntimeException.class, () -> service.complete(context, request));

        verify(conversationStore).appendMessages("tenant-a", "session-4", request.messages());
        verify(conversationStore, never()).appendAssistantMessage(eq("tenant-a"), eq("session-4"), any(), any());
        verify(chatAuditService).record(eq("tenant-a"), eq("req-4"), eq("session-4"), eq("chat.complete"),
            argThat(result -> result != null && result.startsWith("ERROR:llm failed;latencyMs=")));
        }
}
