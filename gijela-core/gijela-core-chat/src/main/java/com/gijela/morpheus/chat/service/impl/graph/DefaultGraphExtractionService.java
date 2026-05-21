package com.gijela.morpheus.chat.service.impl.graph;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.graph.GraphExtractTextRequest;
import com.gijela.morpheus.chat.domain.entity.ChatModelConfig;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractPreviewResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractStatsVO;
import com.gijela.morpheus.chat.mapper.ChatModelConfigMapper;
import com.gijela.morpheus.chat.service.graph.GraphExtractionService;
import com.gijela.morpheus.chat.support.graph.GraphExtractionParser;
import com.gijela.morpheus.chat.support.graph.GraphPromptBuilder;
import com.gijela.morpheus.chat.support.graph.GraphPreviewStore;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;
import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.core.model.ChatResponse;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleClient;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleProperties;
import okhttp3.OkHttpClient;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DefaultGraphExtractionService implements GraphExtractionService {

    private final ChatModuleProperties properties;
    private final ChatModelConfigMapper modelConfigMapper;
    private final OkHttpClient okHttpClient;
    private final GraphExtractionParser graphExtractionParser;
    private final GraphPromptBuilder graphPromptBuilder;
    private final GraphPreviewStore graphPreviewStore;
    private final Tika tika = new Tika();

    public DefaultGraphExtractionService(ChatModuleProperties properties,
                                         ChatModelConfigMapper modelConfigMapper,
                                         OkHttpClient okHttpClient,
                                         GraphExtractionParser graphExtractionParser,
                                         GraphPromptBuilder graphPromptBuilder,
                                         GraphPreviewStore graphPreviewStore) {
        this.properties = properties;
        this.modelConfigMapper = modelConfigMapper;
        this.okHttpClient = okHttpClient;
        this.graphExtractionParser = graphExtractionParser;
        this.graphPromptBuilder = graphPromptBuilder;
        this.graphPreviewStore = graphPreviewStore;
        this.tika.setMaxStringLength(properties.getGraph().getMaxTextChars());
    }

    @Override
    public GraphExtractPreviewResponse extractText(ChatContext context, GraphExtractTextRequest request) {
        ensureGraphEnabled();
        String graphSpace = resolveGraphSpace(request.graphSpace());
        validateTextLength(request.inputText());
        return buildPreview(context, graphSpace, request.title(), request.llmModel(), request.extractMode(), request.importMode(), request.promptOverride(), request.maxTokens(), "text", request.inputText());
    }

    @Override
    public GraphExtractPreviewResponse extractFile(ChatContext context,
                                                   MultipartFile file,
                                                   String graphSpace,
                                                   String title,
                                                   String llmModel,
                                                   String extractMode,
                                                   String importMode,
                                                   String promptOverride,
                                                   Integer maxTokens) {
        ensureGraphEnabled();
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "file 不能为空");
        }
        long maxSize = properties.getGraph().getMaxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxSize) {
            throw new BizException(ErrorCode.GRAPH_FILE_TOO_LARGE, "文件超过图谱导入大小限制");
        }
        String extracted = extractText(file);
        validateTextLength(extracted);
        String resolvedTitle = title == null || title.isBlank() ? Objects.toString(file.getOriginalFilename(), "未命名文件") : title.trim();
        return buildPreview(context, resolveGraphSpace(graphSpace), resolvedTitle, llmModel, extractMode, importMode, promptOverride, maxTokens, "file", extracted);
    }

    private GraphExtractPreviewResponse buildPreview(ChatContext context,
                                                     String graphSpace,
                                                     String title,
                                                     String llmModel,
                                                     String extractMode,
                                                     String importMode,
                                                     String promptOverride,
                                                     Integer maxTokens,
                                                     String sourceType,
                                                     String rawText) {
        String llmResult = callLlm(context.tenantId(), graphSpace, title, llmModel, extractMode, promptOverride, maxTokens, rawText);
        GraphExtractionParser.ParsedResult parsed = graphExtractionParser.parse(llmResult, context.tenantId(), graphSpace);
        List<String> warnings = new ArrayList<>(parsed.warnings());
        if (promptOverride != null && !promptOverride.isBlank()) {
            warnings.add("当前预览使用了自定义 promptOverride");
        }
        GraphExtractPreviewResponse response = new GraphExtractPreviewResponse(
                buildPreviewId(),
                graphSpace,
                title,
                sourceType,
                defaultIfBlank(extractMode, "LLM_RULES"),
                defaultIfBlank(importMode, "MERGE_APPEND"),
                "PREVIEW_READY",
                parsed.entities(),
                parsed.relationships(),
                warnings,
                new GraphExtractStatsVO(parsed.entities().size(), parsed.relationships().size())
        );
        graphPreviewStore.save(response);
        return response;
    }

    private String callLlm(String tenantId,
                           String graphSpace,
                           String title,
                           String llmModel,
                           String extractMode,
                           String promptOverride,
                           Integer maxTokens,
                           String rawText) {
        String prompt = graphPromptBuilder.buildPrompt(graphSpace, title, extractMode, promptOverride, rawText);
        OpenAiCompatibleClient client = resolveClient(tenantId, llmModel);
        String resolvedModel = llmModel == null || llmModel.isBlank() ? null : llmModel.trim();
        ChatRequest request = new ChatRequest(
                resolvedModel,
                List.of(new ChatMessage("user", prompt)),
                0.2,
                resolveMaxTokens(maxTokens),
                Map.of()
        );
        try {
            ChatResponse response = client.chat(request);
            if (response == null || response.content() == null || response.content().isBlank()) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "图谱抽取未返回有效结果");
            }
            return response.content();
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.NETWORK_IO_ERROR, "图谱抽取调用失败: " + ex.getMessage());
        }
    }

    private OpenAiCompatibleClient resolveClient(String tenantId, String requestedModel) {
        String resolvedTenant = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim();
        ChatModelConfig config = queryChatModelConfig(resolvedTenant, requestedModel);
        if (config == null && !"default".equals(resolvedTenant)) {
            config = queryChatModelConfig("default", requestedModel);
        }
        if (config == null) {
            throw new IllegalStateException("未找到可用的 CHAT 模型配置，请先在模型配置页维护");
        }
        OpenAiCompatibleProperties p = new OpenAiCompatibleProperties(
                config.getBaseUrl(), config.getApiKey(), config.getModel(),
                safeTimeout(config.getConnectTimeoutSeconds(), 10),
                safeTimeout(config.getReadTimeoutSeconds(), 120),
                safeTimeout(config.getCallTimeoutSeconds(), 180));
        return new OpenAiCompatibleClient(okHttpClient, p);
    }

    private ChatModelConfig queryChatModelConfig(String tenantId, String requestedModel) {
        QueryWrapper<ChatModelConfig> q = new QueryWrapper<ChatModelConfig>()
                .eq("tenant_id", tenantId)
                .eq("config_type", "CHAT")
                .eq("enabled", 1)
                .eq("deleted", 0)
                .orderByDesc("updated_at").orderByDesc("id")
                .last("limit 1");
        if (requestedModel != null && !requestedModel.isBlank()) {
            q.eq("model", requestedModel.trim());
        }
        return modelConfigMapper.selectOne(q);
    }

    private int safeTimeout(Integer v, int fallback) {
        return (v == null || v <= 0) ? fallback : v;
    }

    private void validateTextLength(String inputText) {
        if (inputText != null && inputText.length() > properties.getGraph().getMaxTextChars()) {
            throw new BizException(ErrorCode.GRAPH_TEXT_TOO_LONG, "文本超过图谱抽取长度限制");
        }
    }

    private String extractText(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            String content = tika.parseToString(inputStream);
            if (content == null || content.isBlank()) {
                throw new BizException(ErrorCode.INVALID_ARGUMENT, "文件未提取到可用文本");
            }
            return content;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.NETWORK_IO_ERROR, "文件文本提取失败");
        }
    }

    private String resolveGraphSpace(String graphSpace) {
        return graphSpace == null || graphSpace.isBlank() ? properties.getGraph().getDefaultSpace() : graphSpace.trim();
    }

    private String buildPreviewId() {
        return "gpv_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private Integer resolveMaxTokens(Integer maxTokens) {
        if (maxTokens == null || maxTokens < 1) {
            return properties.getGraph().getMaxTokens();
        }
        return Math.min(maxTokens, properties.getGraph().getMaxTokens());
    }

    private void ensureGraphEnabled() {
        if (!properties.getGraph().isEnabled()) {
            throw new BizException(ErrorCode.GRAPH_MODULE_DISABLED, "图谱模块未启用");
        }
    }
}