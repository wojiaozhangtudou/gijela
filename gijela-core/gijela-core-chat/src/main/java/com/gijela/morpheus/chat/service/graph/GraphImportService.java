package com.gijela.morpheus.chat.service.graph;

import com.gijela.morpheus.chat.domain.dto.graph.GraphImportRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.graph.GraphImportResultResponse;

public interface GraphImportService {

    GraphImportResultResponse importPreview(ChatContext context, GraphImportRequest request, String idempotencyKey);
}