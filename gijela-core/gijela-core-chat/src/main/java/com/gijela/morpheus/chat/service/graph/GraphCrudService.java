package com.gijela.morpheus.chat.service.graph;

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

public interface GraphCrudService {

    GraphSpaceListResponse listSpaces(ChatContext context);

    GraphSpaceItemVO createSpace(ChatContext context, CreateGraphSpaceRequest request);

    GraphMutationResultResponse deleteSpace(ChatContext context, String graphSpace);

    GraphEntityPageResponse pageEntities(ChatContext context, GraphEntityPageRequest request);

    GraphEntityTypeListResponse listEntityTypes(ChatContext context, GraphEntityTypeListRequest request);

    GraphRelationshipPageResponse pageRelationships(ChatContext context, GraphRelationshipPageRequest request);

    GraphOneHopQueryResponse queryOneHop(ChatContext context, GraphOneHopQueryRequest request);

    GraphMutationResultResponse deleteEntities(ChatContext context, GraphDeleteEntitiesRequest request);

    GraphMutationResultResponse deleteRelationships(ChatContext context, GraphDeleteRelationshipsRequest request);

    GraphMutationResultResponse deleteImportBatch(ChatContext context, GraphDeleteImportBatchRequest request);
}