package com.gijela.morpheus.chat.service.impl.graph;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.graph.GraphImportRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractPreviewResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphImportResultResponse;
import com.gijela.morpheus.chat.repository.graph.Neo4jGraphRepository;
import com.gijela.morpheus.chat.service.graph.GraphImportService;
import com.gijela.morpheus.chat.support.graph.GraphIdempotencyStore;
import com.gijela.morpheus.chat.support.graph.GraphPreviewStore;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;

import java.util.UUID;

public class DefaultGraphImportService implements GraphImportService {

    private final ChatModuleProperties properties;
    private final GraphPreviewStore graphPreviewStore;
    private final GraphIdempotencyStore graphIdempotencyStore;
    private final Neo4jGraphRepository neo4jGraphRepository;

    public DefaultGraphImportService(ChatModuleProperties properties,
                                     GraphPreviewStore graphPreviewStore,
                                     GraphIdempotencyStore graphIdempotencyStore,
                                     Neo4jGraphRepository neo4jGraphRepository) {
        this.properties = properties;
        this.graphPreviewStore = graphPreviewStore;
        this.graphIdempotencyStore = graphIdempotencyStore;
        this.neo4jGraphRepository = neo4jGraphRepository;
    }

    @Override
    public GraphImportResultResponse importPreview(ChatContext context, GraphImportRequest request, String idempotencyKey) {
        ensureGraphEnabled();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.GRAPH_MISSING_IDEMPOTENCY_KEY, "导入必须携带 Idempotency-Key");
        }
        GraphImportResultResponse cached = graphIdempotencyStore.find(idempotencyKey).orElse(null);
        if (cached != null) {
            return cached;
        }
        GraphExtractPreviewResponse preview = graphPreviewStore.find(request.previewId())
                .orElseThrow(() -> new BizException(ErrorCode.GRAPH_PREVIEW_NOT_FOUND, "图谱预览不存在或已失效"));
        String graphSpace = request.graphSpace() == null || request.graphSpace().isBlank() ? preview.graphSpace() : request.graphSpace().trim();
        if (!preview.graphSpace().equals(graphSpace)) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "graphSpace 与预览不一致");
        }
        String importMode = request.importMode() == null || request.importMode().isBlank() ? preview.importMode() : request.importMode().trim();
        String importBatchId = "gib_" + UUID.randomUUID().toString().replace("-", "");
        neo4jGraphRepository.importPreview(context, preview, importBatchId, importMode);
        GraphImportResultResponse response = new GraphImportResultResponse(
                preview.previewId(),
                graphSpace,
                importBatchId,
                importMode,
                preview.stats().entityCount(),
                preview.stats().relationshipCount(),
                "SUCCESS"
        );
        graphIdempotencyStore.save(idempotencyKey, response);
        return response;
    }

    private void ensureGraphEnabled() {
        if (!properties.getGraph().isEnabled()) {
            throw new BizException(ErrorCode.GRAPH_MODULE_DISABLED, "图谱模块未启用");
        }
    }
}