package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.adapter.mcp.KnowledgeSearchGateway;
import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.KnowledgeIndexRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.service.KnowledgeIngestionService;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class DefaultKnowledgeIngestionService implements KnowledgeIngestionService {

    private final KnowledgeSearchGateway knowledgeSearchGateway;
    private final ChatModuleProperties properties;
    private final Tika tika;

    public DefaultKnowledgeIngestionService(KnowledgeSearchGateway knowledgeSearchGateway,
                                            ChatModuleProperties properties) {
        this.knowledgeSearchGateway = knowledgeSearchGateway;
        this.properties = properties;
        this.tika = new Tika();
        this.tika.setMaxStringLength(this.properties.getRetrieval().getIndexFileMaxChars());
    }

    @Override
    public Map<String, Object> listCollections(ChatContext context) {
        return knowledgeSearchGateway.listCollections();
    }

    @Override
    public Map<String, Object> deleteCollection(ChatContext context, String collection) {
        return knowledgeSearchGateway.deleteCollection(collection);
    }

    @Override
    public Map<String, Object> deleteVector(ChatContext context, String collection, String id) {
        return knowledgeSearchGateway.deleteVectors(collection, java.util.List.of(id));
    }

    @Override
    public Map<String, Object> deleteVectors(ChatContext context, String collection, java.util.List<String> ids) {
        return knowledgeSearchGateway.deleteVectors(collection, ids);
    }

    @Override
    public Map<String, Object> clearVectors(ChatContext context, String collection) {
        return knowledgeSearchGateway.clearVectors(collection);
    }

    @Override
    public Map<String, Object> indexText(ChatContext context, KnowledgeIndexRequest request) {
        return knowledgeSearchGateway.indexText(
                context.tenantId(),
                request.title(),
                request.content(),
                request.chunkSize(),
                request.chunkOverlap(),
                request.embeddingModel()
        );
    }

    @Override
    public Map<String, Object> indexFile(ChatContext context,
                                         MultipartFile file,
                                         String title,
                                         Integer chunkSize,
                                         Integer chunkOverlap,
                                         String embeddingModel) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 不能为空");
        }
        String extracted = extractText(file);
        String resolvedTitle = resolveTitle(title, file.getOriginalFilename());
        Map<String, Object> indexResult = knowledgeSearchGateway.indexText(
                context.tenantId(),
                resolvedTitle,
                extracted,
                chunkSize,
                chunkOverlap,
                embeddingModel
        );
        Map<String, Object> result = new LinkedHashMap<>(indexResult);
        result.put("sourceType", "file");
        result.put("filename", Objects.toString(file.getOriginalFilename(), ""));
        result.put("size", file.getSize());
        result.put("mimeType", detectMimeType(file));
        return result;
    }

    @Override
    public Map<String, Object> initCollection(ChatContext context, Integer vectorSize, String distance, String embeddingModel) {
        return knowledgeSearchGateway.initCollection(context.tenantId(), vectorSize, distance, embeddingModel);
    }

    @Override
    public Map<String, Object> detectEmbeddingDimension(ChatContext context, String probeText, String embeddingModel) {
        return knowledgeSearchGateway.detectEmbeddingDimension(context.tenantId(), probeText, embeddingModel);
    }

    @Override
    public Map<String, Object> search(ChatContext context, String query) {
        return knowledgeSearchGateway.search(context.tenantId(), query);
    }

    private String extractText(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            String text = tika.parseToString(stream);
            if (text == null || text.trim().isBlank()) {
                throw new IllegalArgumentException("文件未提取到可用文本");
            }
            return text;
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败", e);
        } catch (Exception e) {
            throw new RuntimeException("Tika 文本提取失败", e);
        }
    }

    private String detectMimeType(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            return tika.detect(stream, file.getOriginalFilename());
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private String resolveTitle(String title, String originalFilename) {
        String fromParam = title == null ? "" : title.trim();
        if (!fromParam.isBlank()) {
            return fromParam;
        }
        String fromFilename = originalFilename == null ? "" : originalFilename.trim();
        return fromFilename.isBlank() ? "未命名文档" : fromFilename;
    }
}
