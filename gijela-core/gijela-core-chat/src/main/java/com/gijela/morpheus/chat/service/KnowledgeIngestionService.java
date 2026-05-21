package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.chat.domain.dto.KnowledgeIndexRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface KnowledgeIngestionService {

    Map<String, Object> listCollections(ChatContext context);

    Map<String, Object> deleteCollection(ChatContext context, String collection);

    Map<String, Object> deleteVector(ChatContext context, String collection, String id);

    Map<String, Object> deleteVectors(ChatContext context, String collection, java.util.List<String> ids);

    Map<String, Object> clearVectors(ChatContext context, String collection);

    Map<String, Object> indexText(ChatContext context, KnowledgeIndexRequest request);

    Map<String, Object> indexFile(ChatContext context, MultipartFile file, String title, Integer chunkSize, Integer chunkOverlap, String embeddingModel);

    Map<String, Object> initCollection(ChatContext context, Integer vectorSize, String distance, String embeddingModel);

    Map<String, Object> detectEmbeddingDimension(ChatContext context, String probeText, String embeddingModel);

    Map<String, Object> search(ChatContext context, String query);
}
