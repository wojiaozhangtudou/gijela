package com.gijela.morpheus.chat.controller;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.ChatHistoryMessageResponse;
import com.gijela.morpheus.chat.domain.dto.ChatMessageDTO;
import com.gijela.morpheus.chat.domain.dto.ChatSessionItemResponse;
import com.gijela.morpheus.chat.domain.dto.CreateSessionRequest;
import com.gijela.morpheus.chat.domain.dto.DeleteVectorsRequest;
import com.gijela.morpheus.chat.domain.dto.KnowledgeIndexRequest;
import com.gijela.morpheus.chat.domain.dto.StreamChatRequest;
import com.gijela.morpheus.chat.domain.dto.UpdateSessionTitleRequest;
import com.gijela.morpheus.chat.domain.vo.ChatEventVO;
import com.gijela.morpheus.chat.service.AttachmentService;
import com.gijela.morpheus.chat.service.ChatAuditService;
import com.gijela.morpheus.chat.service.ChatHistoryQueryService;
import com.gijela.morpheus.chat.service.ChatOrchestratorService;
import com.gijela.morpheus.chat.service.KnowledgeIngestionService;
import com.gijela.morpheus.chat.service.SseSessionRegistry;
import com.gijela.morpheus.chat.service.StorageAdminService;
import com.gijela.morpheus.chat.service.graph.GraphCrudService;
import com.gijela.morpheus.chat.service.graph.GraphExtractionService;
import com.gijela.morpheus.chat.service.graph.GraphImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatOrchestratorService chatOrchestratorService;

    @Mock
    private ChatHistoryQueryService chatHistoryQueryService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private SseSessionRegistry sseSessionRegistry;

    @Mock
    private KnowledgeIngestionService knowledgeIngestionService;

    @Mock
    private StorageAdminService storageAdminService;

    @Mock
    private ChatAuditService chatAuditService;

    @Mock
    private GraphExtractionService graphExtractionService;

    @Mock
    private GraphImportService graphImportService;

    @Mock
    private GraphCrudService graphCrudService;

    @Mock
    private HttpServletRequest request;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        ChatModuleProperties properties = new ChatModuleProperties();
        controller = new ChatController(chatOrchestratorService, chatHistoryQueryService, attachmentService, knowledgeIngestionService, storageAdminService, chatAuditService, sseSessionRegistry, properties, graphExtractionService, graphImportService, graphCrudService);
    }

    @Test
    void listSessions_shouldUseTenantHeaderAndReturnApiResponse() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(chatHistoryQueryService.listSessions("tenant-a", 20)).thenReturn(List.of(
                new ChatSessionItemResponse("s-1", "会话1", "摘要", 2, LocalDateTime.now())
        ));

        var response = controller.listSessions(20, request);

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("s-1", response.getData().get(0).sessionId());
        verify(chatHistoryQueryService).listSessions("tenant-a", 20);
    }

    @Test
    void createSession_shouldDelegateToHistoryService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(chatHistoryQueryService.createSession("tenant-a", "我的新会话"))
                .thenReturn(Map.of("sessionId", "session-1", "title", "我的新会话", "status", "created"));

        var response = controller.createSession(new CreateSessionRequest("我的新会话"), request);

        assertEquals(0, response.getCode());
        assertEquals("created", response.getData().get("status"));
        verify(chatHistoryQueryService).createSession("tenant-a", "我的新会话");
    }

    @Test
    void listSessionMessages_shouldUseTenantHeaderAndSessionId() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(chatHistoryQueryService.listMessages("tenant-a", "s-1", 100)).thenReturn(List.of(
                new ChatHistoryMessageResponse("user", "hello", LocalDateTime.now(), null)
        ));

        var response = controller.listSessionMessages("s-1", 100, request);

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("hello", response.getData().get(0).content());
        verify(chatHistoryQueryService).listMessages("tenant-a", "s-1", 100);
    }

    @Test
    void deleteSession_shouldDelegateToHistoryService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(chatHistoryQueryService.deleteSession("tenant-a", "s-1"))
                .thenReturn(Map.of("status", "deleted", "deletedSessions", 1));

        var response = controller.deleteSession("s-1", request);

        assertEquals(0, response.getCode());
        assertEquals("deleted", response.getData().get("status"));
        verify(chatHistoryQueryService).deleteSession("tenant-a", "s-1");
    }

    @Test
    void updateSessionTitle_shouldDelegateToHistoryService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(chatHistoryQueryService.updateSessionTitle("tenant-a", "s-1", "我的会话"))
                .thenReturn(Map.of("status", "updated", "title", "我的会话"));

        var response = controller.updateSessionTitle("s-1", new UpdateSessionTitleRequest("我的会话"), request);

        assertEquals(0, response.getCode());
        assertEquals("updated", response.getData().get("status"));
        verify(chatHistoryQueryService).updateSessionTitle("tenant-a", "s-1", "我的会话");
    }

    @Test
    void stream_shouldRegisterEmitterByEventSessionId() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        StreamChatRequest streamRequest = new StreamChatRequest(
                null,
                List.of(new ChatMessageDTO("user", "hello")),
                null,
                null,
                null,
                null
        );

        doAnswer(invocation -> {
            java.util.function.Consumer<ChatEventVO> consumer = invocation.getArgument(2);
            consumer.accept(new ChatEventVO("start", "session-xyz", null, null, null, null, null, null));
            return null;
        }).when(chatOrchestratorService).stream(any(), any(), any());

        controller.stream(streamRequest, request);

        verify(sseSessionRegistry).register(any(), any());
    }

    @Test
    void indexKnowledge_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        KnowledgeIndexRequest indexRequest = new KnowledgeIndexRequest("测试文档", "这是用于入库的文本内容", 300, 50, null);
        when(knowledgeIngestionService.indexText(any(), any())).thenReturn(Map.of("status", "indexed", "chunks", 1));

        var response = controller.indexKnowledge(indexRequest, request);

        assertEquals(0, response.getCode());
        assertEquals("indexed", response.getData().get("status"));
        verify(knowledgeIngestionService).indexText(any(), any());
    }

    @Test
    void searchKnowledge_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(knowledgeIngestionService.search(any(), any())).thenReturn(Map.of("hits", List.of()));

        var response = controller.searchKnowledge("权限模型", request);

        assertEquals(0, response.getCode());
        verify(knowledgeIngestionService).search(any(), any());
    }

    @Test
    void indexKnowledgeFile_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello world".getBytes());
        when(knowledgeIngestionService.indexFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("status", "indexed", "sourceType", "file"));

        var response = controller.indexKnowledgeFile(file, "demo", 300, 50, null, request);

        assertEquals(0, response.getCode());
        assertEquals("file", response.getData().get("sourceType"));
        verify(knowledgeIngestionService).indexFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    void initKnowledgeCollection_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(knowledgeIngestionService.initCollection(any(), any(), any(), any()))
                .thenReturn(Map.of("status", "created", "vectorSize", 1024));

        var response = controller.initKnowledgeCollection(1024, "Cosine", null, request);

        assertEquals(0, response.getCode());
        assertEquals("created", response.getData().get("status"));
        verify(knowledgeIngestionService).initCollection(any(), any(), any(), any());
    }

    @Test
    void initKnowledgeCollection_shouldSupportAutoDetectWhenVectorSizeMissing() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(knowledgeIngestionService.initCollection(any(), any(), any(), any()))
                .thenReturn(Map.of("status", "created", "vectorSize", 1024, "autoDetected", true));

        var response = controller.initKnowledgeCollection(null, "Cosine", null, request);

        assertEquals(0, response.getCode());
        assertEquals(true, response.getData().get("autoDetected"));
        verify(knowledgeIngestionService).initCollection(any(), any(), any(), any());
    }

    @Test
    void detectEmbeddingDimension_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(knowledgeIngestionService.detectEmbeddingDimension(any(), any(), any()))
                .thenReturn(Map.of("dimension", 1024, "model", "text-embedding-3-large"));

        var response = controller.detectEmbeddingDimension("测试探针", null, request);

        assertEquals(0, response.getCode());
        assertEquals(1024, response.getData().get("dimension"));
        verify(knowledgeIngestionService).detectEmbeddingDimension(any(), any(), any());
    }

    @Test
    void listKnowledgeCollections_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(knowledgeIngestionService.listCollections(any()))
                .thenReturn(Map.of("collections", List.of("chat_knowledge"), "count", 1));

        var response = controller.listKnowledgeCollections(request);

        assertEquals(0, response.getCode());
        verify(knowledgeIngestionService).listCollections(any());
    }

    @Test
    void deleteKnowledgeCollection_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(knowledgeIngestionService.deleteCollection(any(), any()))
                .thenReturn(Map.of("collection", "chat_knowledge", "status", "deleted"));

        var response = controller.deleteKnowledgeCollection("chat_knowledge", request);

        assertEquals(0, response.getCode());
        assertEquals("deleted", response.getData().get("status"));
        verify(knowledgeIngestionService).deleteCollection(any(), any());
    }

    @Test
    void deleteKnowledgeVector_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(knowledgeIngestionService.deleteVector(any(), any(), any()))
                .thenReturn(Map.of("deleted", 1, "status", "deleted"));

        var response = controller.deleteKnowledgeVector("chat_knowledge", "1001", request);

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().get("deleted"));
        verify(knowledgeIngestionService).deleteVector(any(), any(), any());
    }

    @Test
    void deleteKnowledgeVectors_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(knowledgeIngestionService.deleteVectors(any(), any(), any()))
                .thenReturn(Map.of("deleted", 2, "status", "deleted"));

        var response = controller.deleteKnowledgeVectors(new DeleteVectorsRequest("chat_knowledge", List.of("1001", "1002")), request);

        assertEquals(0, response.getCode());
        assertEquals(2, response.getData().get("deleted"));
        verify(knowledgeIngestionService).deleteVectors(any(), any(), any());
    }

    @Test
    void clearKnowledgeVectors_shouldDelegateToKnowledgeService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(knowledgeIngestionService.clearVectors(any(), any()))
                .thenReturn(Map.of("deleted", 12, "status", "cleared"));

        var response = controller.clearKnowledgeVectors("chat_knowledge", request);

        assertEquals(0, response.getCode());
        assertEquals("cleared", response.getData().get("status"));
        verify(knowledgeIngestionService).clearVectors(any(), any());
    }

    @Test
    void listStorageBuckets_shouldDelegateToStorageAdminService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(storageAdminService.listBuckets(any()))
                .thenReturn(Map.of("buckets", List.of(Map.of("name", "resume-rag")), "count", 1));

        var response = controller.listStorageBuckets(request);

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().get("count"));
        verify(storageAdminService).listBuckets(any());
    }

    @Test
    void uploadStorageObject_shouldDelegateToStorageAdminService() {
        when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-a");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes());
        when(storageAdminService.uploadObject(any(), any(), any(), any()))
                .thenReturn(Map.of("bucket", "resume-rag", "objectKey", "demo.txt", "status", "uploaded"));

        var response = controller.uploadStorageObject("resume-rag", "demo.txt", file, request);

        assertEquals(0, response.getCode());
        assertEquals("resume-rag", response.getData().get("bucket"));
        verify(storageAdminService).uploadObject(any(), any(), any(), any());
    }
}
