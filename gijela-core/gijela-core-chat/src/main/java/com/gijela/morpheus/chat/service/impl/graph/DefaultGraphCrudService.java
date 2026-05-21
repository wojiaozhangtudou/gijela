package com.gijela.morpheus.chat.service.impl.graph;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.graph.CreateGraphSpaceRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphDeleteEntitiesRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphDeleteImportBatchRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphDeleteRelationshipsRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphEntityPageRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphEntityTypeListRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphOneHopQueryRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphRelationshipPageRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.graph.GraphEntityPageResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphEntityTypeListResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphMutationResultResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphOneHopQueryResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphRelationshipPageResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphSpaceItemVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphSpaceListResponse;
import com.gijela.morpheus.chat.repository.graph.Neo4jGraphRepository;
import com.gijela.morpheus.chat.service.graph.GraphCrudService;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;

public class DefaultGraphCrudService implements GraphCrudService {

    private final ChatModuleProperties properties;
    private final Neo4jGraphRepository neo4jGraphRepository;

    public DefaultGraphCrudService(ChatModuleProperties properties,
                                   Neo4jGraphRepository neo4jGraphRepository) {
        this.properties = properties;
        this.neo4jGraphRepository = neo4jGraphRepository;
    }

    @Override
    public GraphSpaceListResponse listSpaces(ChatContext context) {
        ensureEnabled();
        return neo4jGraphRepository.listSpaces(context);
    }

    @Override
    public GraphSpaceItemVO createSpace(ChatContext context, CreateGraphSpaceRequest request) {
        ensureEnabled();
        String graphSpace = resolveGraphSpace(request.graphSpace());
        return neo4jGraphRepository.createSpace(context, graphSpace);
    }

    @Override
    public GraphMutationResultResponse deleteSpace(ChatContext context, String graphSpace) {
        ensureEnabled();
        return neo4jGraphRepository.deleteSpace(context, resolveGraphSpace(graphSpace));
    }

    @Override
    public GraphEntityPageResponse pageEntities(ChatContext context, GraphEntityPageRequest request) {
        ensureEnabled();
        return neo4jGraphRepository.pageEntities(context, request, resolveGraphSpace(request.graphSpace()));
    }

    @Override
    public GraphEntityTypeListResponse listEntityTypes(ChatContext context, GraphEntityTypeListRequest request) {
        ensureEnabled();
        return neo4jGraphRepository.listEntityTypes(context, resolveGraphSpace(request.graphSpace()));
    }

    @Override
    public GraphRelationshipPageResponse pageRelationships(ChatContext context, GraphRelationshipPageRequest request) {
        ensureEnabled();
        return neo4jGraphRepository.pageRelationships(context, request, resolveGraphSpace(request.graphSpace()));
    }

    @Override
    public GraphOneHopQueryResponse queryOneHop(ChatContext context, GraphOneHopQueryRequest request) {
        ensureEnabled();
        return neo4jGraphRepository.queryOneHop(context, resolveGraphSpace(request.graphSpace()), request.centerEntity(), request.limitNodes(), request.limitEdges());
    }

    @Override
    public GraphMutationResultResponse deleteEntities(ChatContext context, GraphDeleteEntitiesRequest request) {
        ensureEnabled();
        return neo4jGraphRepository.deleteEntities(context, resolveGraphSpace(request.graphSpace()), request.normalizedNames());
    }

    @Override
    public GraphMutationResultResponse deleteRelationships(ChatContext context, GraphDeleteRelationshipsRequest request) {
        ensureEnabled();
        return neo4jGraphRepository.deleteRelationships(context, resolveGraphSpace(request.graphSpace()), request.relationshipIds());
    }

    @Override
    public GraphMutationResultResponse deleteImportBatch(ChatContext context, GraphDeleteImportBatchRequest request) {
        ensureEnabled();
        return neo4jGraphRepository.deleteImportBatch(context, resolveGraphSpace(request.graphSpace()), request.importBatchId());
    }

    private void ensureEnabled() {
        if (!properties.getGraph().isEnabled()) {
            throw new BizException(ErrorCode.GRAPH_MODULE_DISABLED, "图谱模块未启用");
        }
    }

    private String resolveGraphSpace(String graphSpace) {
        return graphSpace == null || graphSpace.isBlank()
                ? properties.getGraph().getDefaultSpace()
                : graphSpace.trim();
    }
}