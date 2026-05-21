package com.gijela.morpheus.chat.adapter.llm;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.llm.sdk.skill.SkillRegistry;
import com.gijela.morpheus.chat.domain.dto.ChatCompletionRequest;
import com.gijela.morpheus.chat.domain.dto.ChatCompletionResponse;
import com.gijela.morpheus.chat.domain.dto.ChatMessageDTO;
import com.gijela.morpheus.chat.domain.dto.ChatUsageDTO;
import com.gijela.morpheus.chat.domain.dto.StreamChatRequest;
import com.gijela.morpheus.chat.domain.entity.ChatModelConfig;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.ChatEventVO;
import com.gijela.morpheus.chat.mapper.ChatModelConfigMapper;
import com.gijela.morpheus.chat.service.ChatAuditService;
import com.gijela.morpheus.llm.sdk.core.event.LlmEvent;
import com.gijela.morpheus.llm.sdk.core.event.LlmEventListener;
import com.gijela.morpheus.llm.sdk.core.event.LlmEventType;
import com.gijela.morpheus.llm.sdk.core.model.ChatMessage;
import com.gijela.morpheus.llm.sdk.core.model.ChatRequest;
import com.gijela.morpheus.llm.sdk.core.model.ChatResponse;
import com.gijela.morpheus.llm.sdk.core.model.TokenUsage;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleClient;
import com.gijela.morpheus.llm.sdk.openai.OpenAiCompatibleProperties;
import com.gijela.morpheus.llm.sdk.openai.OpenAiMetadataKeys;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OpenAiChatAdapter {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiChatAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** 匹配模型以文本方式输出的工具调用块，例如 qwen ReAct 格式 */
    private static final Pattern TEXT_TOOL_CALL_PATTERN =
            Pattern.compile("<tool_code>\\s*([\\s\\S]*?)\\s*</tool_code>", Pattern.DOTALL);

        private final OkHttpClient baseOkHttpClient;
        private final ChatModelConfigMapper modelConfigMapper;
    private final SkillRegistry skillRegistry;
    private final ChatAuditService chatAuditService;
    private final com.gijela.morpheus.chat.service.SkillStateService skillStateService;

    public List<String> listSkills() {
        return skillRegistry.listAll();
    }

    public OpenAiChatAdapter(OkHttpClient baseOkHttpClient,
                             ChatModelConfigMapper modelConfigMapper,
                             SkillRegistry skillRegistry,
                             ChatAuditService chatAuditService,
                             com.gijela.morpheus.chat.service.SkillStateService skillStateService) {
        this.baseOkHttpClient = baseOkHttpClient;
        this.modelConfigMapper = modelConfigMapper;
        this.skillRegistry = skillRegistry;
        this.chatAuditService = chatAuditService;
        this.skillStateService = skillStateService;
    }

    /** 仅供旧代码路径使用的兼容构造函数（不含状态过滤）。 */
    public OpenAiChatAdapter(OkHttpClient baseOkHttpClient,
                             ChatModelConfigMapper modelConfigMapper,
                             SkillRegistry skillRegistry,
                             ChatAuditService chatAuditService) {
        this(baseOkHttpClient, modelConfigMapper, skillRegistry, chatAuditService, null);
    }

    /** 按"启用"状态过滤工具 schema 列表（disabled 技能不发给模型）。 */
    private List<Map<String, Object>> resolveEnabledToolSchemas(String tenantId, List<String> requestedSkills) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (String name : skillRegistry.listAll()) {
            if (skillStateService != null && !skillStateService.isEnabled(tenantId, name)) {
                continue;
            }
            Map<String, Object> schema = skillRegistry.getSchema(name);
            if (schema != null) {
                tools.add(schema);
            }
        }
        return tools;
    }

    public List<ChatMessageDTO> mergeHistory(ChatContext context, String sessionId, List<ChatMessageDTO> currentMessages) {
        return currentMessages == null ? List.of() : currentMessages;
    }

    public ChatCompletionResponse complete(ChatContext context,
                                           String sessionId,
                                           ChatCompletionRequest request,
                                           List<ChatMessageDTO> mergedMessages) {
        long startNanos = System.nanoTime();
        try {
            OpenAiCompatibleClient runtimeClient = resolveClient(context.tenantId(), request.model());
            ChatResponse response = runtimeClient.chat(buildRequest(context, sessionId, request.model(), request.temperature(), request.maxTokens(), request.skills(), mergedMessages));
            chatAuditService.record(context.tenantId(), context.requestId(), sessionId,
                "llm.complete", buildLlmSuccessResult(response.finishReason(), startNanos));
            return new ChatCompletionResponse(
                sessionId,
                response.content(),
                response.finishReason(),
                toUsage(response.usage()),
                null
            );
        } catch (RuntimeException ex) {
            chatAuditService.record(context.tenantId(), context.requestId(), sessionId,
                "llm.complete", buildLlmErrorResult(ex.getMessage(), startNanos));
            throw ex;
        }
    }

    public void stream(ChatContext context,
                       String sessionId,
                       StreamChatRequest request,
                       List<ChatMessageDTO> mergedMessages,
                       Consumer<ChatEventVO> consumer) {
        long startNanos = System.nanoTime();
        final boolean[] terminalAudited = {false};
        final boolean[] toolResultEmitted = {false};
        StringBuilder assistantText = new StringBuilder();
        OpenAiCompatibleClient runtimeClient = resolveClient(context.tenantId(), request.model());
        consumer.accept(new ChatEventVO("start", sessionId, null, null, null, null, null, null));
        runtimeClient.stream(buildRequest(context, sessionId, request.model(), request.temperature(), request.maxTokens(), request.skills(), mergedMessages), new LlmEventListener() {
            @Override
            public void onEvent(LlmEvent event) {
                if (event.type() == LlmEventType.TOOL_RESULT) {
                    toolResultEmitted[0] = true;
                }
                // 在 DONE 事件传出前，检测并处理文本格式工具调用（如 qwen 的 <tool_code> 块）
                if (event.type() == LlmEventType.DONE) {
                    String followUp = maybeFollowUpTextToolCalls(
                            context, sessionId, mergedMessages, assistantText.toString(), request, consumer, runtimeClient);
                    if (followUp != null && !followUp.isBlank()) {
                        consumer.accept(new ChatEventVO("delta", sessionId, followUp, null, null, null, null, null));
                        assistantText.setLength(0);
                        assistantText.append(followUp);
                        toolResultEmitted[0] = true;
                    }
                    maybeForceAttachmentContextIfClaimed(context, sessionId, mergedMessages, assistantText.toString(), consumer, toolResultEmitted);
                }
                ChatEventVO eventVO = mapEvent(event, sessionId, assistantText);
                if (eventVO == null) {
                    return;
                }
                if (!terminalAudited[0] && ("done".equals(eventVO.type()) || "error".equals(eventVO.type()))) {
                    String result = "done".equals(eventVO.type())
                            ? buildLlmSuccessResult("done", startNanos)
                            : buildLlmErrorResult(eventVO.error(), startNanos);
                    chatAuditService.record(context.tenantId(), context.requestId(), sessionId, "llm.stream", result);
                    terminalAudited[0] = true;
                }
                consumer.accept(eventVO);
            }

            @Override
            public void onError(Throwable throwable) {
                if (!terminalAudited[0]) {
                    chatAuditService.record(context.tenantId(), context.requestId(), sessionId,
                            "llm.stream", buildLlmErrorResult(throwable == null ? null : throwable.getMessage(), startNanos));
                    terminalAudited[0] = true;
                }
                consumer.accept(new ChatEventVO("error", sessionId, null, null, null, throwable == null ? null : throwable.getMessage(), null, null));
            }
        });
    }

    private void maybeForceAttachmentContextIfClaimed(ChatContext context,
                                                      String sessionId,
                                                      List<ChatMessageDTO> mergedMessages,
                                                      String assistantText,
                                                      Consumer<ChatEventVO> consumer,
                                                      boolean[] toolResultEmitted) {
        if (toolResultEmitted[0]) {
            return;
        }
        if (assistantText == null || assistantText.isBlank()) {
            return;
        }
        if (!assistantText.contains("attachment.context")) {
            return;
        }
        var forcedCall = new com.gijela.morpheus.llm.sdk.core.tool.ToolCall(
                UUID.randomUUID().toString(),
                "attachment.context",
                Map.of("limit", 5)
        );
        ToolResult result = skillRegistry.execute(forcedCall,
                new ToolContext(context.tenantId(), Map.of(
                        "sessionId", sessionId,
                        "requestId", context.requestId() == null ? "" : context.requestId()
                )));
        consumer.accept(new ChatEventVO("tool_result", sessionId, null, null, toToolResult(result), null, null, null));
        toolResultEmitted[0] = true;
    }

    private String buildLlmSuccessResult(String finishReason, long startNanos) {
        String reason = (finishReason == null || finishReason.isBlank()) ? "unknown" : finishReason.trim();
        return "SUCCESS;finishReason=" + reason + ";latencyMs=" + computeElapsedMs(startNanos);
    }

    private String buildLlmErrorResult(String error, long startNanos) {
        long latencyMs = computeElapsedMs(startNanos);
        if (error == null || error.isBlank()) {
            return "ERROR;latencyMs=" + latencyMs;
        }
        String normalized = error.replace('\n', ' ').trim();
        String clipped = normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
        return "ERROR:" + clipped + ";latencyMs=" + latencyMs;
    }

    private long computeElapsedMs(long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        if (elapsedNanos <= 0) {
            return 0L;
        }
        return elapsedNanos / 1_000_000L;
    }

    private ChatRequest buildRequest(ChatContext context,
                                     String sessionId,
                                     String model,
                                     Double temperature,
                                     Integer maxTokens,
                                     List<String> skills,
                                     List<ChatMessageDTO> messages) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(OpenAiMetadataKeys.TENANT_ID, context.tenantId());
        metadata.put(OpenAiMetadataKeys.REQUEST_ID, context.requestId());
        metadata.put(OpenAiMetadataKeys.TOOL_EXECUTOR, skillRegistry);
        metadata.put(OpenAiMetadataKeys.TOOL_CONTEXT,
                new com.gijela.morpheus.llm.sdk.core.tool.ToolContext(context.tenantId(), Map.of(
                        "requestId", context.requestId(),
                "sessionId", sessionId
                )));
        metadata.put(OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, true);
        metadata.put(OpenAiMetadataKeys.TOOLS, resolveEnabledToolSchemas(context.tenantId(), skills));
        metadata.put(OpenAiMetadataKeys.TOOL_CHOICE, "auto");
        List<ChatMessageDTO> withGuidance = prependToolGuidance(messages, skills);
        return new ChatRequest(
                model,
                toSdkMessages(withGuidance),
                temperature,
                maxTokens,
                metadata
        );
    }

    private List<ChatMessageDTO> prependToolGuidance(List<ChatMessageDTO> messages, List<String> skills) {
        List<ChatMessageDTO> source = messages == null ? List.of() : messages;
        if (skills == null || skills.isEmpty()) {
            return source;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你可以调用工具辅助回答。工具调用规则：\n");
        if (skills.contains("knowledge.search")) {
            sb.append("1) 遇到需要事实依据、部署步骤、配置参数、版本差异、知识库问题时，优先调用 knowledge.search，再基于结果回答。\n");
        }
        if (skills.contains("attachment.context")) {
            sb.append("2) 用户提到‘附件/文档/上传文件/这个文件/该文档’时，先调用 attachment.context 读取上下文，再回答。\n");
        }
        sb.append("3) 禁止仅输出工具调用文本；必须在拿到工具结果后给出自然语言结论。\n");
        sb.append("4) 若工具无结果，明确告知并给出下一步建议。\n");

        ArrayList<ChatMessageDTO> result = new ArrayList<>(source.size() + 1);
        result.add(new ChatMessageDTO("system", sb.toString()));
        result.addAll(source);
        return result;
    }

    private List<ChatMessage> toSdkMessages(List<ChatMessageDTO> messages) {
        List<ChatMessage> sdkMessages = new ArrayList<>();
        for (ChatMessageDTO message : messages) {
            sdkMessages.add(new ChatMessage(message.role(), message.content()));
        }
        return sdkMessages;
    }

    private ChatUsageDTO toUsage(TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        return new ChatUsageDTO(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
    }

    private ChatEventVO mapEvent(LlmEvent event, String sessionId, StringBuilder assistantText) {
        if (event == null || event.type() == null) {
            return new ChatEventVO("error", sessionId, null, null, null, "空事件", null, null);
        }
        if (event.type() == LlmEventType.START) {
            // 已在 stream() 开头手动发出 start，避免重复 start 事件
            return null;
        }
        if (event.type() == LlmEventType.DELTA && event.textDelta() != null) {
            assistantText.append(event.textDelta());
            return new ChatEventVO("delta", sessionId, event.textDelta(), null, null, null, null, null);
        }
        if (event.type() == LlmEventType.TOOL_CALL) {
            return new ChatEventVO("tool_call", sessionId, null, toToolCall(event), null, null, null, null);
        }
        if (event.type() == LlmEventType.TOOL_RESULT) {
            return new ChatEventVO("tool_result", sessionId, null, null, toToolResult(event), null, null, null);
        }
        if (event.type() == LlmEventType.ERROR) {
            return new ChatEventVO("error", sessionId, null, null, null, event.errorMessage(), null, null);
        }
        if (event.type() == LlmEventType.DONE) {
            return new ChatEventVO("done", sessionId, assistantText.toString(), null, null, null, null, toUsage(event.usage()));
        }
        return new ChatEventVO(event.type().name().toLowerCase(), sessionId, null, null, null, null, null, null);
    }

    private Map<String, Object> toToolCall(LlmEvent event) {
        if (event.toolCall() == null) {
            return null;
        }
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", event.toolCall().id());
        toolCall.put("name", event.toolCall().name());
        toolCall.put("arguments", Objects.requireNonNullElse(event.toolCall().arguments(), Map.of()));
        return toolCall;
    }

    /**
     * 检测 assistantText 中是否包含文本格式工具调用块（如 &lt;tool_code&gt;{...}&lt;/tool_code&gt;）。
     * 若存在，则执行工具并以「工具结果 + 原始问题」追加 follow-up 调用，返回最终回答文本。
     * 若不存在文本格式工具调用，则返回 null（不做任何处理）。
     */
    @SuppressWarnings("unchecked")
    private String maybeFollowUpTextToolCalls(ChatContext context,
                                              String sessionId,
                                              List<ChatMessageDTO> mergedMessages,
                                              String assistantText,
                                              StreamChatRequest request,
                                              Consumer<ChatEventVO> consumer,
                                              OpenAiCompatibleClient runtimeClient) {
        Matcher matcher = TEXT_TOOL_CALL_PATTERN.matcher(assistantText);
        List<com.gijela.morpheus.llm.sdk.core.tool.ToolCall> textCalls = new ArrayList<>();
        while (matcher.find()) {
            try {
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(
                        matcher.group(1).trim(), new TypeReference<>() {});
                String name = parsed.get("name") instanceof String s ? s : null;
                if (name == null || name.isBlank()) {
                    continue;
                }
                Object argsObj = parsed.get("arguments");
                Map<String, Object> args = argsObj instanceof Map ? (Map<String, Object>) argsObj : Map.of();
                textCalls.add(new com.gijela.morpheus.llm.sdk.core.tool.ToolCall(
                        UUID.randomUUID().toString(), name, args));
            } catch (Exception e) {
                logger.debug("[text-tool-call] 解析 tool_code 块失败，跳过: {}", e.getMessage());
            }
        }
        if (textCalls.isEmpty()) {
            return null;
        }

        // 执行工具
        ToolContext toolCtx = new ToolContext(context.tenantId(),
                Map.of("sessionId", sessionId,
                        "requestId", context.requestId() == null ? "" : context.requestId()));
        StringBuilder toolResultSb = new StringBuilder();
        for (com.gijela.morpheus.llm.sdk.core.tool.ToolCall call : textCalls) {
            ToolResult result = skillRegistry.execute(call, toolCtx);
            consumer.accept(new ChatEventVO("tool_result", sessionId, null, null,
                    toToolResult(result), null, null, null));
            toolResultSb.append("[").append(call.name()).append("] 调用结果：\n");
            if (result.success() && result.result() != null && !result.result().isEmpty()) {
                try {
                    toolResultSb.append(OBJECT_MAPPER.writeValueAsString(result.result()));
                } catch (Exception e) {
                    toolResultSb.append(result.result().toString());
                }
            } else {
                toolResultSb.append(result.errorMessage() == null ? "（无结果）" : result.errorMessage());
            }
            toolResultSb.append("\n\n");
        }

        // 构建 follow-up 消息（不带 tools，让模型直接回答）
        List<ChatMessageDTO> followUpMessages = new ArrayList<>(mergedMessages);
        followUpMessages.add(new ChatMessageDTO("assistant", assistantText));
        followUpMessages.add(new ChatMessageDTO("user",
                "工具调用结果如下：\n" + toolResultSb + "请根据以上工具返回结果，直接、完整地回答用户的问题。"));

        try {
            ChatRequest followUpReq = buildRequest(context, sessionId,
                    request.model(), request.temperature(), request.maxTokens(),
                    List.of(), // follow-up 不注入 tools，避免再次触发工具调用
                    followUpMessages);
            ChatResponse response = runtimeClient.chat(followUpReq);
            return response.content();
        } catch (Exception e) {
            logger.warn("[text-tool-call] follow-up 调用失败，忽略工具结果: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> toToolResult(LlmEvent event) {
        if (event.toolResult() == null) {
            return null;
        }
        return toToolResult(event.toolResult());
    }

    private Map<String, Object> toToolResult(ToolResult result) {
        if (result == null) {
            return null;
        }
        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("toolCallId", result.toolCallId());
        toolResult.put("success", result.success());
        toolResult.put("result", Objects.requireNonNullElse(result.result(), Map.of()));
        toolResult.put("error", result.errorMessage());
        return toolResult;
    }

    private OpenAiCompatibleClient resolveClient(String tenantId, String requestedModel) {
        ChatModelConfig config = selectChatModelConfig(tenantId, requestedModel);
        if (config == null) {
            throw new IllegalStateException("未找到可用的 CHAT 模型配置，请先在模型配置页维护");
        }
        OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties(
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModel(),
                safeTimeout(config.getConnectTimeoutSeconds(), 10),
                safeTimeout(config.getReadTimeoutSeconds(), 60),
                safeTimeout(config.getCallTimeoutSeconds(), 120)
        );
        return new OpenAiCompatibleClient(baseOkHttpClient, properties);
    }

    private ChatModelConfig selectChatModelConfig(String tenantId, String requestedModel) {
        String resolvedTenant = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim();
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
        QueryWrapper<ChatModelConfig> query = new QueryWrapper<ChatModelConfig>()
                .eq("tenant_id", tenantId)
                .eq("config_type", "CHAT")
                .eq("enabled", 1)
                .eq("deleted", 0)
                .orderByDesc("updated_at")
                .orderByDesc("id")
                .last("limit 1");
        if (requestedModel != null && !requestedModel.isBlank()) {
            query.eq("model", requestedModel.trim());
        }
        return modelConfigMapper.selectOne(query);
    }

    private int safeTimeout(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }
}
