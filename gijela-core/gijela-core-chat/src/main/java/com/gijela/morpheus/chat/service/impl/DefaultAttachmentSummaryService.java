package com.gijela.morpheus.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.entity.ChatAttachment;
import com.gijela.morpheus.chat.domain.entity.ChatModelConfig;
import com.gijela.morpheus.chat.mapper.ChatAttachmentMapper;
import com.gijela.morpheus.chat.mapper.ChatModelConfigMapper;
import com.gijela.morpheus.chat.service.AttachmentSummaryService;
import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.core.model.ChatResponse;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleClient;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleProperties;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 附件摘要提炼服务默认实现。
 * 两次 LLM 调用：raw_text → condensed_md → summary
 * 全程异步，失败降级（status=3），不阻断对话主流程。
 */
@Service
public class DefaultAttachmentSummaryService implements AttachmentSummaryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAttachmentSummaryService.class);

    private final ChatAttachmentMapper attachmentMapper;
    private final ChatModelConfigMapper modelConfigMapper;
    private final OkHttpClient okHttpClient;
    private final ChatModuleProperties properties;

    public DefaultAttachmentSummaryService(ChatAttachmentMapper attachmentMapper,
                                           ChatModelConfigMapper modelConfigMapper,
                                           OkHttpClient okHttpClient,
                                           ChatModuleProperties properties) {
        this.attachmentMapper = attachmentMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.okHttpClient = okHttpClient;
        this.properties = properties;
    }

    @Async("chatSummaryExecutor")
    @Override
    public void processAsync(Long attachmentId, String llmModel) {
        ChatAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            log.warn("[附件摘要] attachmentId={} 不存在，跳过", attachmentId);
            return;
        }
        String rawText = attachment.getRawText();
        if (rawText == null || rawText.isBlank()) {
            // 不支持的文件类型，直接标为完成（无摘要）
            updateStatus(attachmentId, 2, null, null, null);
            return;
        }

        // 标记处理中
        updateStatus(attachmentId, 1, null, null, null);

        try {
            ChatModuleProperties.AttachmentSummary cfg = properties.getAttachmentSummary();

            // 第一次 LLM：raw_text → condensed_md
            String condensedPromptTpl = cfg.getCondensedPrompt();
            String condensedMd = null;
            if (condensedPromptTpl != null && !condensedPromptTpl.isBlank()) {
                String condensedPrompt = condensedPromptTpl.replace("{text}", rawText);
                condensedMd = callLlm(attachment.getTenantId(), condensedPrompt, llmModel);
            }

            // 第二次 LLM：condensed_md → summary
            String summaryInput = condensedMd != null ? condensedMd : rawText;
            String summaryPromptTpl = cfg.getSummaryPrompt();
            String summary = null;
            if (summaryPromptTpl != null && !summaryPromptTpl.isBlank()) {
                String summaryPrompt = summaryPromptTpl.replace("{text}", summaryInput);
                summary = callLlm(attachment.getTenantId(), summaryPrompt, llmModel);
                if (summary != null && summary.length() > 500) {
                    summary = summary.substring(0, 500);
                }
            }

            updateStatus(attachmentId, 2, condensedMd, summary, null);
            log.info("[附件摘要] attachmentId={} 提炼完成", attachmentId);

        } catch (Exception e) {
            log.error("[附件摘要] attachmentId={} 提炼失败: {}", attachmentId, e.getMessage(), e);
            updateStatus(attachmentId, 3, null, null, e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getStatus(Long attachmentId) {
        ChatAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("附件不存在: " + attachmentId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attachmentId", attachmentId);
        result.put("fileName", attachment.getFileName());
        result.put("status", attachment.getProcessStatus());
        result.put("summary", attachment.getSummary());
        // status=2 时返回 condensedMd（供前端详情展示或注入 LLM）
        if (Integer.valueOf(2).equals(attachment.getProcessStatus())) {
            result.put("condensedMd", attachment.getCondensedMd());
        }
        return result;
    }

    // ── 私有方法 ──────────────────────────────────────────────────────────

    private String callLlm(String tenantId, String prompt, String llmModel) {
        OpenAiCompatibleClient client = resolveClient(tenantId, llmModel);
        String resolvedModel = llmModel == null || llmModel.isBlank() ? null : llmModel.trim();
        ChatRequest request = new ChatRequest(
                resolvedModel,
                List.of(new ChatMessage("user", prompt)),
                0.3,
                4096,
                Map.of()
        );
        ChatResponse response = client.chat(request);
        return response == null ? null : response.content();
    }

    private OpenAiCompatibleClient resolveClient(String tenantId, String requestedModel) {
        ChatModelConfig config = selectChatModelConfig(tenantId, requestedModel);
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

    private ChatModelConfig selectChatModelConfig(String tenantId, String requestedModel) {
        String resolvedTenant = normalizeTenant(tenantId);
        ChatModelConfig byTenant = queryChatModelConfig(resolvedTenant, requestedModel);
        if (byTenant != null) {
            return byTenant;
        }
        if (!"default".equals(resolvedTenant)) {
            return queryChatModelConfig("default", requestedModel);
        }
        return null;
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

    private String normalizeTenant(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim();
    }

    private void updateStatus(Long attachmentId, int status,
                               String condensedMd, String summary, String errorMsg) {
        ChatAttachment update = new ChatAttachment();
        update.setId(attachmentId);
        update.setProcessStatus(status);
        if (condensedMd != null) {
            update.setCondensedMd(condensedMd);
        }
        if (summary != null) {
            update.setSummary(summary);
        }
        attachmentMapper.updateById(update);
    }
}
