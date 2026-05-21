package com.gijela.morpheus.chat.service.graph;

import com.gijela.morpheus.chat.domain.dto.graph.GraphExtractTextRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractPreviewResponse;
import org.springframework.web.multipart.MultipartFile;

public interface GraphExtractionService {

    GraphExtractPreviewResponse extractText(ChatContext context, GraphExtractTextRequest request);

    GraphExtractPreviewResponse extractFile(ChatContext context,
                                           MultipartFile file,
                                           String graphSpace,
                                           String title,
                                           String llmModel,
                                           String extractMode,
                                           String importMode,
                                           String promptOverride,
                                           Integer maxTokens);
}