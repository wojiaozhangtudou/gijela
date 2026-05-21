package com.gijela.morpheus.chat.controller;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.ChatCompletionRequest;
import com.gijela.morpheus.chat.domain.dto.ChatCompletionResponse;
import com.gijela.morpheus.chat.domain.dto.ChatHistoryMessageResponse;
import com.gijela.morpheus.chat.domain.dto.ChatSessionItemResponse;
import com.gijela.morpheus.chat.domain.dto.CreateSessionRequest;
import com.gijela.morpheus.chat.domain.dto.DeleteVectorsRequest;
import com.gijela.morpheus.chat.domain.dto.DeleteAttachmentsRequest;
import com.gijela.morpheus.chat.domain.dto.KnowledgeIndexRequest;
import com.gijela.morpheus.chat.domain.dto.StreamChatRequest;
import com.gijela.morpheus.chat.domain.dto.UpdateSessionTitleRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphEntityPageRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphExtractTextRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphImportRequest;
import com.gijela.morpheus.chat.domain.dto.graph.CreateGraphSpaceRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphDeleteEntitiesRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphDeleteImportBatchRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphDeleteRelationshipsRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphRelationshipPageRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphOneHopQueryRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphEntityTypeListRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.ChatEventVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphEntityPageResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractPreviewResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphImportResultResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphMutationResultResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphOneHopQueryResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphRelationshipPageResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphEntityTypeListResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphSpaceItemVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphSpaceListResponse;
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
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatHistoryQueryService chatHistoryQueryService;
    private final AttachmentService attachmentService;
    private final SseSessionRegistry sseSessionRegistry;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final StorageAdminService storageAdminService;
    private final ChatAuditService chatAuditService;
    private final ChatModuleProperties properties;
    private final GraphExtractionService graphExtractionService;
    private final GraphImportService graphImportService;
    private final GraphCrudService graphCrudService;

    public ChatController(ChatOrchestratorService chatOrchestratorService,
                          ChatHistoryQueryService chatHistoryQueryService,
                          AttachmentService attachmentService,
                          KnowledgeIngestionService knowledgeIngestionService,
                          StorageAdminService storageAdminService,
                          ChatAuditService chatAuditService,
                          SseSessionRegistry sseSessionRegistry,
                          ChatModuleProperties properties,
                          GraphExtractionService graphExtractionService,
                          GraphImportService graphImportService,
                          GraphCrudService graphCrudService) {
        this.chatOrchestratorService = chatOrchestratorService;
        this.chatHistoryQueryService = chatHistoryQueryService;
        this.attachmentService = attachmentService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.storageAdminService = storageAdminService;
        this.chatAuditService = chatAuditService;
        this.sseSessionRegistry = sseSessionRegistry;
        this.properties = properties;
        this.graphExtractionService = graphExtractionService;
        this.graphImportService = graphImportService;
        this.graphCrudService = graphCrudService;
    }

    @GetMapping("/skills")
    public ApiResponse<List<String>> listSkills() {
        return ApiResponse.ok(chatOrchestratorService.listSkills());
    }

    @PostMapping("/completions")
    public ApiResponse<ChatCompletionResponse> complete(@Valid @RequestBody ChatCompletionRequest request,
                                                        HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, request.sessionId());
        return ApiResponse.ok(chatOrchestratorService.complete(context, request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody StreamChatRequest request,
                             HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, request.sessionId());
        SseEmitter emitter = new SseEmitter(0L);
        final String[] registeredSessionId = {null};
        chatOrchestratorService.stream(context, request, event -> {
            registerEmitterIfNecessary(emitter, event, registeredSessionId);
            sendEvent(emitter, event);
        });
        return emitter;
    }

    private void registerEmitterIfNecessary(SseEmitter emitter, ChatEventVO event, String[] registeredSessionId) {
        if (registeredSessionId[0] != null || event == null || event.sessionId() == null || event.sessionId().isBlank()) {
            return;
        }
        String sessionId = event.sessionId();
        registeredSessionId[0] = sessionId;
        sseSessionRegistry.register(sessionId, emitter);
        emitter.onCompletion(() -> sseSessionRegistry.remove(sessionId));
        emitter.onTimeout(() -> sseSessionRegistry.remove(sessionId));
        emitter.onError(ex -> sseSessionRegistry.remove(sessionId));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "sessionId", required = false) String sessionId,
                                                   @RequestParam(value = "summaryModel", required = false) String summaryModel,
                                                   HttpServletRequest httpServletRequest) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new com.gijela.morpheus.common.BizException(
                    com.gijela.morpheus.common.enums.ErrorCode.INVALID_ARGUMENT, "上传附件必须携带有效的 sessionId");
        }
        ChatContext context = buildContext(httpServletRequest, sessionId);
        return ApiResponse.ok(attachmentService.upload(context, file, summaryModel));
    }

    @GetMapping("/attachments")
    public ApiResponse<Map<String, Object>> listAttachments(@RequestParam("sessionId") String sessionId,
                                                            @RequestParam(value = "page", required = false) Integer page,
                                                            @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                            @RequestParam(value = "status", required = false) Integer status,
                                                            @RequestParam(value = "fileNameLike", required = false) String fileNameLike,
                                                            @RequestParam(value = "startTime", required = false) String startTime,
                                                            @RequestParam(value = "endTime", required = false) String endTime,
                                                            HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, sessionId);
        return ApiResponse.ok(attachmentService.listBySession(context, sessionId, page, pageSize, status, fileNameLike, startTime, endTime));
    }

    @GetMapping("/attachments/{attachmentId}")
    public ApiResponse<Map<String, Object>> getAttachmentDetail(@PathVariable Long attachmentId,
                                                                HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(attachmentService.getDetail(context, attachmentId));
    }

    @PostMapping("/attachments/{attachmentId}/rename")
    public ApiResponse<Map<String, Object>> renameAttachment(@PathVariable Long attachmentId,
                                                             @RequestParam("fileName") String fileName,
                                                             HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(attachmentService.rename(context, attachmentId, fileName));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ApiResponse<Map<String, Object>> deleteAttachment(@PathVariable Long attachmentId,
                                                             HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(attachmentService.delete(context, attachmentId));
    }

    @PostMapping("/attachments/delete-batch")
    public ApiResponse<Map<String, Object>> deleteAttachmentsBatch(@Valid @RequestBody DeleteAttachmentsRequest request,
                                                                    HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(attachmentService.deleteBatch(context, request.ids()));
    }

    @GetMapping("/attachments/{attachmentId}/status")
    public ApiResponse<Map<String, Object>> getAttachmentStatus(@PathVariable Long attachmentId) {
        return ApiResponse.ok(attachmentService.getStatus(attachmentId));
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<ByteArrayResource> downloadAttachment(@PathVariable Long attachmentId,
                                                                HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        AttachmentService.AttachmentDownload download = attachmentService.download(context, attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(new ByteArrayResource(download.bytes()));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionItemResponse>> listSessions(@RequestParam(value = "limit", defaultValue = "20") Integer limit,
                                                                   HttpServletRequest httpServletRequest) {
        String tenantId = httpServletRequest.getHeader(properties.getServer().getTenantHeader());
        return ApiResponse.ok(chatHistoryQueryService.listSessions(tenantId, limit == null ? 20 : limit));
    }

    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> createSession(@Valid @RequestBody CreateSessionRequest request,
                                                          HttpServletRequest httpServletRequest) {
        String tenantId = httpServletRequest.getHeader(properties.getServer().getTenantHeader());
        return ApiResponse.ok(chatHistoryQueryService.createSession(tenantId, request.title(), request.model()));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatHistoryMessageResponse>> listSessionMessages(@PathVariable("sessionId") String sessionId,
                                                                             @RequestParam(value = "limit", defaultValue = "100") Integer limit,
                                                                             HttpServletRequest httpServletRequest) {
        String tenantId = httpServletRequest.getHeader(properties.getServer().getTenantHeader());
        return ApiResponse.ok(chatHistoryQueryService.listMessages(tenantId, sessionId, limit == null ? 100 : limit));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> deleteSession(@PathVariable("sessionId") String sessionId,
                                                          HttpServletRequest httpServletRequest) {
        String tenantId = httpServletRequest.getHeader(properties.getServer().getTenantHeader());
        return ApiResponse.ok(chatHistoryQueryService.deleteSession(tenantId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/title")
    public ApiResponse<Map<String, Object>> updateSessionTitle(@PathVariable("sessionId") String sessionId,
                                                                @Valid @RequestBody UpdateSessionTitleRequest request,
                                                                HttpServletRequest httpServletRequest) {
        String tenantId = httpServletRequest.getHeader(properties.getServer().getTenantHeader());
        return ApiResponse.ok(chatHistoryQueryService.updateSessionTitle(tenantId, sessionId, request.title(), request.model()));
    }

    @PostMapping("/knowledge/index")
    public ApiResponse<Map<String, Object>> indexKnowledge(@Valid @RequestBody KnowledgeIndexRequest request,
                                                           HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.indexText(context, request));
    }

    @PostMapping("/graph/extract/text")
    public ApiResponse<GraphExtractPreviewResponse> extractGraphText(@Valid @RequestBody GraphExtractTextRequest request,
                                                                     HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        ensurePromptOverrideAllowed(httpServletRequest, request.promptOverride());
        GraphExtractPreviewResponse response = graphExtractionService.extractText(context, request);
        auditGraph(context, "GRAPH_EXTRACT_TEXT", "graphSpace=" + response.graphSpace() + ";previewId=" + response.previewId());
        return ApiResponse.ok(response);
    }

    @GetMapping("/graph/spaces")
    public ApiResponse<GraphSpaceListResponse> listGraphSpaces(HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(graphCrudService.listSpaces(context));
    }

    @PostMapping("/graph/spaces")
    public ApiResponse<GraphSpaceItemVO> createGraphSpace(@Valid @RequestBody CreateGraphSpaceRequest request,
                                                          HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        GraphSpaceItemVO response = graphCrudService.createSpace(context, request);
        auditGraph(context, "GRAPH_CREATE_SPACE", "graphSpace=" + response.graphSpace());
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/graph/spaces/{space}")
    public ApiResponse<GraphMutationResultResponse> deleteGraphSpace(@PathVariable("space") String space,
                                                                     HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        GraphMutationResultResponse response = graphCrudService.deleteSpace(context, space);
        auditGraph(context, "GRAPH_DELETE_SPACE", "graphSpace=" + response.graphSpace() + ";affected=" + response.affected());
        return ApiResponse.ok(response);
    }

    @PostMapping(value = "/graph/extract/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<GraphExtractPreviewResponse> extractGraphFile(@RequestParam("file") MultipartFile file,
                                                                     @RequestParam(value = "graphSpace", required = false) String graphSpace,
                                                                     @RequestParam(value = "title", required = false) String title,
                                                                     @RequestParam(value = "llmModel", required = false) String llmModel,
                                                                     @RequestParam(value = "extractMode", required = false) String extractMode,
                                                                     @RequestParam(value = "importMode", required = false) String importMode,
                                                                     @RequestParam(value = "promptOverride", required = false) String promptOverride,
                                                                     @RequestParam(value = "maxTokens", required = false) Integer maxTokens,
                                                                     HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        ensurePromptOverrideAllowed(httpServletRequest, promptOverride);
        GraphExtractPreviewResponse response = graphExtractionService.extractFile(context, file, graphSpace, title, llmModel, extractMode, importMode, promptOverride, maxTokens);
        auditGraph(context, "GRAPH_EXTRACT_FILE", "graphSpace=" + response.graphSpace() + ";previewId=" + response.previewId());
        return ApiResponse.ok(response);
    }

    @PostMapping("/graph/import")
    public ApiResponse<GraphImportResultResponse> importGraphPreview(@Valid @RequestBody GraphImportRequest request,
                                                                     HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        String idempotencyKey = httpServletRequest.getHeader("Idempotency-Key");
        GraphImportResultResponse response = graphImportService.importPreview(context, request, idempotencyKey);
        auditGraph(context, "GRAPH_IMPORT", "graphSpace=" + response.graphSpace() + ";importBatchId=" + response.importBatchId() + ";status=" + response.status());
        return ApiResponse.ok(response);
    }

    @PostMapping("/graph/entities/page")
    public ApiResponse<GraphEntityPageResponse> pageGraphEntities(@Valid @RequestBody GraphEntityPageRequest request,
                                                                  HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(graphCrudService.pageEntities(context, request));
    }

    @PostMapping("/graph/entities/types")
    public ApiResponse<GraphEntityTypeListResponse> listGraphEntityTypes(@RequestBody(required = false) GraphEntityTypeListRequest request,
                                                                          HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        GraphEntityTypeListRequest payload = request == null ? new GraphEntityTypeListRequest(null) : request;
        GraphEntityTypeListResponse response = graphCrudService.listEntityTypes(context, payload);
        auditGraph(context, "GRAPH_LIST_ENTITY_TYPES", "graphSpace=" + response.graphSpace() + ";count=" + response.count());
        return ApiResponse.ok(response);
    }

    @PostMapping("/graph/relationships/page")
    public ApiResponse<GraphRelationshipPageResponse> pageGraphRelationships(@Valid @RequestBody GraphRelationshipPageRequest request,
                                                                             HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(graphCrudService.pageRelationships(context, request));
    }

    @PostMapping("/graph/view/one-hop")
    public ApiResponse<GraphOneHopQueryResponse> queryGraphOneHop(@Valid @RequestBody GraphOneHopQueryRequest request,
                                                                  HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        GraphOneHopQueryResponse response = graphCrudService.queryOneHop(context, request);
        auditGraph(context, "GRAPH_VIEW_ONE_HOP", "graphSpace=" + response.graphSpace() + ";centerEntity=" + response.centerEntity() + ";nodeCount=" + response.nodeCount() + ";edgeCount=" + response.edgeCount());
        return ApiResponse.ok(response);
    }

    @PostMapping("/graph/entities/delete")
    public ApiResponse<GraphMutationResultResponse> deleteGraphEntities(@Valid @RequestBody GraphDeleteEntitiesRequest request,
                                                                        HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        GraphMutationResultResponse response = graphCrudService.deleteEntities(context, request);
        auditGraph(context, "GRAPH_DELETE_ENTITIES", "graphSpace=" + response.graphSpace() + ";affected=" + response.affected());
        return ApiResponse.ok(response);
    }

    @PostMapping("/graph/relationships/delete")
    public ApiResponse<GraphMutationResultResponse> deleteGraphRelationships(@Valid @RequestBody GraphDeleteRelationshipsRequest request,
                                                                             HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        GraphMutationResultResponse response = graphCrudService.deleteRelationships(context, request);
        auditGraph(context, "GRAPH_DELETE_RELATIONSHIPS", "graphSpace=" + response.graphSpace() + ";affected=" + response.affected());
        return ApiResponse.ok(response);
    }

    @PostMapping("/graph/import-batches/delete")
    public ApiResponse<GraphMutationResultResponse> deleteGraphImportBatch(@Valid @RequestBody GraphDeleteImportBatchRequest request,
                                                                           HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        GraphMutationResultResponse response = graphCrudService.deleteImportBatch(context, request);
        auditGraph(context, "GRAPH_DELETE_IMPORT_BATCH", "graphSpace=" + response.graphSpace() + ";importBatchId=" + request.importBatchId() + ";affected=" + response.affected());
        return ApiResponse.ok(response);
    }

    @GetMapping("/knowledge/collections")
    public ApiResponse<Map<String, Object>> listKnowledgeCollections(HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.listCollections(context));
    }

    @DeleteMapping("/knowledge/collections/{collection}")
    public ApiResponse<Map<String, Object>> deleteKnowledgeCollection(@PathVariable("collection") String collection,
                                                                      HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.deleteCollection(context, collection));
    }

    @PostMapping("/knowledge/vectors/delete-one")
    public ApiResponse<Map<String, Object>> deleteKnowledgeVector(@RequestParam("collection") String collection,
                                                                  @RequestParam("id") String id,
                                                                  HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.deleteVector(context, collection, id));
    }

    @PostMapping("/knowledge/vectors/delete-batch")
    public ApiResponse<Map<String, Object>> deleteKnowledgeVectors(@Valid @RequestBody DeleteVectorsRequest request,
                                                                    HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.deleteVectors(context, request.collection(), request.ids()));
    }

    @PostMapping("/knowledge/vectors/clear")
    public ApiResponse<Map<String, Object>> clearKnowledgeVectors(@RequestParam("collection") String collection,
                                                                  HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.clearVectors(context, collection));
    }

    @PostMapping(value = "/knowledge/index/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> indexKnowledgeFile(@RequestParam("file") MultipartFile file,
                                                                @RequestParam(value = "title", required = false) String title,
                                                                @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
                                                                @RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap,
                                                                @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
                                                                HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.indexFile(context, file, title, chunkSize, chunkOverlap, embeddingModel));
    }

    @PostMapping("/knowledge/collection/init")
    public ApiResponse<Map<String, Object>> initKnowledgeCollection(@RequestParam(value = "vectorSize", required = false) Integer vectorSize,
                                                                    @RequestParam(value = "distance", required = false) String distance,
                                                                    @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
                                                                    HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.initCollection(context, vectorSize, distance, embeddingModel));
    }

    @GetMapping("/knowledge/embedding/dimension")
    public ApiResponse<Map<String, Object>> detectEmbeddingDimension(@RequestParam(value = "probeText", required = false) String probeText,
                                                                     @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
                                                                     HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.detectEmbeddingDimension(context, probeText, embeddingModel));
    }

    @GetMapping("/knowledge/search")
    public ApiResponse<Map<String, Object>> searchKnowledge(@RequestParam("query") String query,
                                                            HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(knowledgeIngestionService.search(context, query));
    }

    @GetMapping("/storage/buckets")
    public ApiResponse<Map<String, Object>> listStorageBuckets(HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(storageAdminService.listBuckets(context));
    }

    @PostMapping("/storage/buckets")
    public ApiResponse<Map<String, Object>> createStorageBucket(@RequestParam("bucket") String bucket,
                                                                 HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(storageAdminService.createBucket(context, bucket));
    }

    @DeleteMapping("/storage/buckets/{bucket}")
    public ApiResponse<Map<String, Object>> deleteStorageBucket(@PathVariable("bucket") String bucket,
                                                                 HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(storageAdminService.deleteBucket(context, bucket));
    }

    @GetMapping("/storage/objects")
    public ApiResponse<Map<String, Object>> listStorageObjects(@RequestParam("bucket") String bucket,
                                                                @RequestParam(value = "prefix", required = false) String prefix,
                                                                @RequestParam(value = "maxKeys", required = false) Integer maxKeys,
                                                                HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(storageAdminService.listObjects(context, bucket, prefix, maxKeys));
    }

    @DeleteMapping("/storage/objects")
    public ApiResponse<Map<String, Object>> deleteStorageObject(@RequestParam("bucket") String bucket,
                                                                 @RequestParam("objectKey") String objectKey,
                                                                 HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(storageAdminService.deleteObject(context, bucket, objectKey));
    }

    @PostMapping(value = "/storage/objects/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadStorageObject(@RequestParam("bucket") String bucket,
                                                                 @RequestParam("objectKey") String objectKey,
                                                                 @RequestParam("file") MultipartFile file,
                                                                 HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        return ApiResponse.ok(storageAdminService.uploadObject(context, bucket, objectKey, file));
    }

    @GetMapping("/storage/objects/download")
    public ResponseEntity<ByteArrayResource> downloadStorageObject(@RequestParam("bucket") String bucket,
                                                                   @RequestParam("objectKey") String objectKey,
                                                                   HttpServletRequest httpServletRequest) {
        ChatContext context = buildContext(httpServletRequest, null);
        StorageAdminService.StorageObjectDownload download = storageAdminService.downloadObject(context, bucket, objectKey);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(new ByteArrayResource(download.bytes()));
    }

    private ChatContext buildContext(HttpServletRequest request, String sessionId) {
        String tenantId = request.getHeader(properties.getServer().getTenantHeader());
        String requestId = request.getHeader(properties.getServer().getRequestHeader());
        return new ChatContext(tenantId, requestId, sessionId);
    }

    private void ensurePromptOverrideAllowed(HttpServletRequest request, String promptOverride) {
        if (promptOverride == null || promptOverride.isBlank()) {
            return;
        }
        String operator = operatorOf(request);
        boolean allowed = properties.getGraph().getDebugPromptOperators().stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .anyMatch(item -> item.equalsIgnoreCase(operator));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前操作人无权使用 promptOverride");
        }
    }

    private String operatorOf(HttpServletRequest request) {
        String operator = request.getHeader("X-Operator");
        if (operator == null || operator.isBlank()) {
            return "system";
        }
        return operator.trim();
    }

    private void auditGraph(ChatContext context, String action, String result) {
        chatAuditService.record(context.tenantId(), context.requestId(), context.sessionId(), action, result);
    }

    private void sendEvent(SseEmitter emitter, ChatEventVO event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event));
            if ("done".equals(event.type()) || "error".equals(event.type())) {
                emitter.complete();
            }
        } catch (IllegalStateException alreadyCompleted) {
            // 连接已结束时，流式线程可能仍有尾事件回调，忽略即可
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
