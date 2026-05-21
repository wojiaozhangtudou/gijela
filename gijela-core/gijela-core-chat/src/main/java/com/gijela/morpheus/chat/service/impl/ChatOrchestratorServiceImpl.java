package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.adapter.llm.OpenAiChatAdapter;
import com.gijela.morpheus.chat.domain.dto.ChatCompletionRequest;
import com.gijela.morpheus.chat.domain.dto.ChatCompletionResponse;
import com.gijela.morpheus.chat.domain.dto.ChatMessageDTO;
import com.gijela.morpheus.chat.domain.dto.ChatUsageDTO;
import com.gijela.morpheus.chat.domain.dto.StreamChatRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.ChatEventVO;
import com.gijela.morpheus.chat.llm.log.dto.LlmAccessLogVO;
import com.gijela.morpheus.chat.llm.log.dto.LlmAuditLogVO;
import com.gijela.morpheus.chat.llm.log.dto.LlmRuntimeLogVO;
import com.gijela.morpheus.chat.llm.log.event.LlmLogEventPublisher;
import com.gijela.morpheus.chat.service.ChatAuditService;
import com.gijela.morpheus.chat.service.ChatOrchestratorService;
import com.gijela.morpheus.chat.service.ConversationStore;
import com.gijela.morpheus.chat.service.KnowledgeIngestionService;
import com.gijela.morpheus.chat.service.PromptConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class ChatOrchestratorServiceImpl implements ChatOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(ChatOrchestratorServiceImpl.class);
    private static final Set<String> SMALL_TALK_QUERIES = Set.of(
            "你好", "您好", "哈喽", "嗨", "在吗", "在不在",
            "早上好", "中午好", "下午好", "晚上好",
            "hi", "hello", "hey", "thanks", "thankyou"
    );

    private final OpenAiChatAdapter openAiChatAdapter;
    private final ConversationStore conversationStore;
    private final ChatAuditService chatAuditService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final PromptConfigService promptConfigService;
    private final LlmLogEventPublisher llmLogEventPublisher;

    @Override
    public List<String> listSkills() {
        return openAiChatAdapter.listSkills();
    }

    public ChatOrchestratorServiceImpl(OpenAiChatAdapter openAiChatAdapter,
                                       ConversationStore conversationStore,
                                       ChatAuditService chatAuditService,
                                       KnowledgeIngestionService knowledgeIngestionService) {
        this(openAiChatAdapter, conversationStore, chatAuditService, knowledgeIngestionService, null, null);
    }

    public ChatOrchestratorServiceImpl(OpenAiChatAdapter openAiChatAdapter,
                                       ConversationStore conversationStore,
                                       ChatAuditService chatAuditService,
                                       KnowledgeIngestionService knowledgeIngestionService,
                                       PromptConfigService promptConfigService) {
        this(openAiChatAdapter, conversationStore, chatAuditService, knowledgeIngestionService, promptConfigService, null);
    }

    @Autowired
    public ChatOrchestratorServiceImpl(OpenAiChatAdapter openAiChatAdapter,
                                       ConversationStore conversationStore,
                                       ChatAuditService chatAuditService,
                                       KnowledgeIngestionService knowledgeIngestionService,
                                       PromptConfigService promptConfigService,
                                       LlmLogEventPublisher llmLogEventPublisher) {
        this.openAiChatAdapter = openAiChatAdapter;
        this.conversationStore = conversationStore;
        this.chatAuditService = chatAuditService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.promptConfigService = promptConfigService;
        this.llmLogEventPublisher = llmLogEventPublisher;
    }

    @Override
    public ChatCompletionResponse complete(ChatContext context, ChatCompletionRequest request) {
        long startNanos = System.nanoTime();
        String sessionId = conversationStore.ensureSessionId(request.sessionId());
        conversationStore.ensureConversationModel(context.tenantId(), sessionId, request.model());
        List<ChatMessageDTO> mergedMessages = mergeHistory(context, sessionId, request.model(), request.messages());
        String effectivePromptHash = resolveEffectivePromptHash(context, sessionId, request.model());
        publishRuntimeLog(context, sessionId, request.model(), "chat.start", "SUCCESS", null, null, null, null, null, false, false, startNanos);
        conversationStore.appendMessages(context.tenantId(), sessionId, request.messages());
        
        // 搜索知识库获取引用
        List<Map<String, Object>> references = searchKnowledgeBase(context, request);
        
        try {
            ChatCompletionResponse response = openAiChatAdapter.complete(context, sessionId, request, mergedMessages);
            conversationStore.appendAssistantMessage(context.tenantId(), sessionId, response.content(), references);
            chatAuditService.record(context.tenantId(), context.requestId(), sessionId,
                "chat.complete", buildSuccessResult(startNanos, effectivePromptHash));
            publishRuntimeLog(context, sessionId, request.model(), "chat.finish", "SUCCESS", null, null,
                    response.usage(), response.finishReason(), response.content(), false, true, startNanos);
                publishAccessLog(context, sessionId, request.model(), "SUCCESS", null, response.usage(), response.finishReason(), response.content(), startNanos);
            publishAuditLog(context, sessionId, request.model(), "FLOW_EXECUTED", "chat.complete", "SUCCESS", null);
            // 返回带引用的响应
            return new ChatCompletionResponse(
                response.sessionId(),
                response.content(),
                response.finishReason(),
                response.usage(),
                references
            );
        } catch (RuntimeException ex) {
            chatAuditService.record(context.tenantId(), context.requestId(), sessionId,
                "chat.complete", buildErrorResult(ex.getMessage(), startNanos, effectivePromptHash));
            publishRuntimeLog(context, sessionId, request.model(), "chat.finish", "FAILED", resolveErrorCode(ex), ex.getMessage(), null, null, null, false, true, startNanos);
            publishAccessLog(context, sessionId, request.model(), "FAILED", ex, null, null, null, startNanos);
            publishAuditLog(context, sessionId, request.model(), "FLOW_EXECUTED", "chat.complete", "FAILED", ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void stream(ChatContext context, StreamChatRequest request, Consumer<ChatEventVO> consumer) {
        long startNanos = System.nanoTime();
        String sessionId = conversationStore.ensureSessionId(request.sessionId());
        conversationStore.ensureConversationModel(context.tenantId(), sessionId, request.model());
        List<ChatMessageDTO> mergedMessages = mergeHistory(context, sessionId, request.model(), request.messages());
        String effectivePromptHash = resolveEffectivePromptHash(context, sessionId, request.model());
        publishRuntimeLog(context, sessionId, request.model(), "chat.start", "SUCCESS", null, null, null, null, null, true, false, startNanos);
        conversationStore.appendMessages(context.tenantId(), sessionId, request.messages());

        final boolean[] terminalAudited = {false};
        final AtomicReference<List<Map<String, Object>>> referencesHolder = new AtomicReference<>(null);
        final int[] deltaTokenTotal = {0};
        
        try {
            openAiChatAdapter.stream(context, sessionId, request, mergedMessages, event -> {
                // 仅当模型真实调用 knowledge.search 工具时，才采集引用
                if ("tool_result".equals(event.type())) {
                    List<Map<String, Object>> references = extractReferencesFromToolResult(event);
                    if (references != null && !references.isEmpty()) {
                        referencesHolder.set(references);
                    }
                    publishRuntimeLog(context, sessionId, request.model(), "tool.result", resolveToolStatus(event),
                            resolveToolErrorCode(event), resolveToolError(event), null, null, null, false, false, startNanos);
                }
                if ("tool_call".equals(event.type())) {
                    publishRuntimeLog(context, sessionId, request.model(), "tool.call", "SUCCESS", null, null,
                            null, null, null, false, false, startNanos);
                }
                if ("delta".equals(event.type()) && event.content() != null && !event.content().isBlank()) {
                    int deltaTokens = estimateOutputTokens(event.content());
                    deltaTokenTotal[0] += deltaTokens;
                    publishRuntimeLog(context, sessionId, request.model(), "chat.delta", "SUCCESS", null, null,
                            new ChatUsageDTO(null, deltaTokens, deltaTokenTotal[0]), null, event.content(), false, false, startNanos);
                }

                // 在 done 事件中附加引用
                ChatEventVO eventToSend = event;
                List<Map<String, Object>> references = referencesHolder.get();
                if ("done".equals(event.type()) && references != null) {
                    eventToSend = new ChatEventVO(event.type(), event.sessionId(), event.content(), 
                        event.toolCall(), event.toolResult(), event.error(), references, event.usage());
                }
                
                if (!terminalAudited[0] && ("done".equals(event.type()) || "error".equals(event.type()))) {
                    String result = "done".equals(event.type())
                            ? buildSuccessResult(startNanos, effectivePromptHash)
                            : buildErrorResult(event.error(), startNanos, effectivePromptHash);
                    chatAuditService.record(context.tenantId(), context.requestId(), sessionId, "chat.stream", result);
                    if ("done".equals(event.type())) {
                        publishRuntimeLog(context, sessionId, request.model(), "chat.finish", "SUCCESS", null, null, event.usage(), "done", event.content(), true, true, startNanos);
                        publishAccessLog(context, sessionId, request.model(), "SUCCESS", null, event.usage(), "done", event.content(), startNanos);
                        publishAuditLog(context, sessionId, request.model(), "FLOW_EXECUTED", "chat.stream", "SUCCESS", null);
                    } else {
                        publishRuntimeLog(context, sessionId, request.model(), "chat.finish", "FAILED", "STREAM_ERROR", event.error(), null, null, null, true, true, startNanos);
                        publishAccessLog(context, sessionId, request.model(), "FAILED", new RuntimeException(event.error()), null, null, null, startNanos);
                        publishAuditLog(context, sessionId, request.model(), "FLOW_EXECUTED", "chat.stream", "FAILED", event.error());
                    }
                    terminalAudited[0] = true;
                }
                if ("done".equals(event.type()) && event.content() != null && !event.content().isBlank()) {
                    conversationStore.appendAssistantMessage(context.tenantId(), sessionId, event.content(), references);
                }
                consumer.accept(eventToSend);
            });
        } catch (RuntimeException ex) {
            if (!terminalAudited[0]) {
                chatAuditService.record(context.tenantId(), context.requestId(), sessionId,
                        "chat.stream", buildErrorResult(ex.getMessage(), startNanos, effectivePromptHash));
                publishRuntimeLog(context, sessionId, request.model(), "chat.finish", "FAILED", resolveErrorCode(ex), ex.getMessage(), null, null, null, true, true, startNanos);
                publishAccessLog(context, sessionId, request.model(), "FAILED", ex, null, null, null, startNanos);
                publishAuditLog(context, sessionId, request.model(), "FLOW_EXECUTED", "chat.stream", "FAILED", ex.getMessage());
            }
            throw ex;
        }
    }

    private void publishAccessLog(ChatContext context,
                                  String sessionId,
                                  String modelRoute,
                                  String status,
                                  RuntimeException error,
                                  ChatUsageDTO usage,
                                  String finishReason,
                                  String outputText,
                                  long startNanos) {
        if (llmLogEventPublisher == null) {
            return;
        }
        long now = System.currentTimeMillis();
        llmLogEventPublisher.publishAccessLogAsync(LlmAccessLogVO.builder()
                .traceId(context.requestId())
                .sessionId(sessionId)
                .tenantId(context.tenantId())
                .eventTime(now)
                .eventTime_dt(LocalDateTime.now())
                .modelRoute(normalizeModelRoute(modelRoute))
                .status(status)
                .errorCode(error == null ? null : resolveErrorCode(error))
                .errorType(error == null ? null : error.getClass().getSimpleName())
                .errorMsg(error == null ? null : error.getMessage())
                .latencyMs(computeElapsedMs(startNanos))
                .provider("openai-compatible")
                .model(normalizeModelRoute(modelRoute))
                .promptTokens(usage == null ? null : usage.promptTokens())
                .completionTokens(usage == null ? null : usage.completionTokens())
                .totalTokens(usage == null ? null : usage.totalTokens())
                .finishReason(finishReason)
                .contentLength(outputText == null ? null : outputText.length())
                .outputText(outputText)
                .sampled(Boolean.TRUE)
                .env("dev")
                .source("chat-orchestrator")
                .build());
    }

    private void publishRuntimeLog(ChatContext context,
                                   String sessionId,
                                   String modelRoute,
                                   String eventType,
                                   String status,
                                   String errorCode,
                                   String errorMsg,
                                   ChatUsageDTO usage,
                                   String finishReason,
                                   String outputText,
                                   boolean streamStart,
                                   boolean streamDone,
                                   long startNanos) {
        if (llmLogEventPublisher == null) {
            return;
        }
        long now = System.currentTimeMillis();
        llmLogEventPublisher.publishRuntimeLogAsync(LlmRuntimeLogVO.builder()
                .traceId(context.requestId())
                .sessionId(sessionId)
                .tenantId(context.tenantId())
                .eventType(eventType)
                .eventTime(now)
                .eventTime_dt(LocalDateTime.now())
                .modelRoute(normalizeModelRoute(modelRoute))
                .status(status)
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .latencyMs(computeElapsedMs(startNanos))
                .provider("openai-compatible")
                .model(normalizeModelRoute(modelRoute))
                .promptTokens(usage == null ? null : usage.promptTokens())
                .completionTokens(usage == null ? null : usage.completionTokens())
                .totalTokens(usage == null ? null : usage.totalTokens())
                .contentLength(outputText == null ? null : outputText.length())
                .outputText(outputText)
                .streamStart(streamStart)
                .streamDone(streamDone)
                .sampled(Boolean.TRUE)
                .env("dev")
                .source("chat-orchestrator")
                .build());
    }

    private void publishAuditLog(ChatContext context,
                                 String sessionId,
                                 String modelRoute,
                                 String operationType,
                                 String operationTarget,
                                 String operationResult,
                                 String detail) {
        if (llmLogEventPublisher == null) {
            return;
        }
        long now = System.currentTimeMillis();
        llmLogEventPublisher.publishAuditLog(LlmAuditLogVO.builder()
                .traceId(context.requestId())
                .sessionId(sessionId)
                .tenantId(context.tenantId())
                .operatorType("service")
                .operatorId("chat-orchestrator")
                .operationType(operationType)
                .operationTarget(operationTarget)
                .operationResult(operationResult)
                .operationDetail(detail)
                .auditTime(LocalDateTime.now())
                .eventTime(now)
                .modelRoute(normalizeModelRoute(modelRoute))
                .errorCode("FAILED".equals(operationResult) ? "ORCHESTRATOR_ERROR" : null)
                .env("dev")
                .source("chat-orchestrator")
                .build());
    }

    private String resolveErrorCode(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "UNKNOWN";
        }
        String upper = ex.getMessage().toUpperCase();
        if (upper.contains("TIMEOUT")) {
            return "TIMEOUT";
        }
        if (upper.contains("RATE_LIMIT") || upper.contains("RATE LIMIT")) {
            return "RATE_LIMIT";
        }
        if (upper.contains("NETWORK")) {
            return "NETWORK_ERROR";
        }
        return "UNKNOWN";
    }

    private String resolveToolStatus(ChatEventVO event) {
        if (event == null || event.toolResult() == null) {
            return "SUCCESS";
        }
        Object success = event.toolResult().get("success");
        if (success instanceof Boolean b) {
            return b ? "SUCCESS" : "FAILED";
        }
        return "SUCCESS";
    }

    private String resolveToolErrorCode(ChatEventVO event) {
        String error = resolveToolError(event);
        if (error == null || error.isBlank()) {
            return null;
        }
        String upper = error.toUpperCase();
        if (upper.contains("TIMEOUT")) {
            return "TIMEOUT";
        }
        if (upper.contains("RATE_LIMIT") || upper.contains("RATE LIMIT")) {
            return "RATE_LIMIT";
        }
        return "TOOL_ERROR";
    }

    private String resolveToolError(ChatEventVO event) {
        if (event == null || event.toolResult() == null) {
            return null;
        }
        Object error = event.toolResult().get("error");
        return error == null ? null : String.valueOf(error);
    }

    private String buildSuccessResult(long startNanos, String effectivePromptHash) {
        return "SUCCESS;latencyMs=" + computeElapsedMs(startNanos) + ";effectivePromptHash=" + normalizeHash(effectivePromptHash);
    }

    private String buildErrorResult(String error, long startNanos, String effectivePromptHash) {
        long elapsedMs = computeElapsedMs(startNanos);
        if (error == null || error.isBlank()) {
            return "ERROR;latencyMs=" + elapsedMs + ";effectivePromptHash=" + normalizeHash(effectivePromptHash);
        }
        String normalized = error.replace('\n', ' ').trim();
        String clipped = normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
        return "ERROR:" + clipped + ";latencyMs=" + elapsedMs + ";effectivePromptHash=" + normalizeHash(effectivePromptHash);
    }

    private long computeElapsedMs(long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        if (elapsedNanos <= 0) {
            return 0L;
        }
        return elapsedNanos / 1_000_000L;
    }

    private List<ChatMessageDTO> mergeHistory(ChatContext context, String sessionId, String modelRoute, List<ChatMessageDTO> currentMessages) {
        List<ChatMessageDTO> normalizedCurrent = currentMessages == null ? List.of() : currentMessages;
        List<ChatMessageDTO> history = conversationStore.getRecentMessages(context.tenantId(), sessionId);
        List<ChatMessageDTO> merged;
        if (history == null || history.isEmpty()) {
            merged = new java.util.ArrayList<>(normalizedCurrent);
        } else {
            merged = new java.util.ArrayList<>(history.size() + normalizedCurrent.size());
            merged.addAll(history);
            merged.addAll(normalizedCurrent);
        }
        return prependEffectivePromptIfPresent(context, sessionId, modelRoute, merged);
    }

    private List<ChatMessageDTO> prependEffectivePromptIfPresent(ChatContext context,
                                                                 String sessionId,
                                                                 String modelRoute,
                                                                 List<ChatMessageDTO> messages) {
        if (promptConfigService == null) {
            return messages;
        }
        try {
            String effectivePrompt = promptConfigService.resolveEffectivePrompt(
                    context.tenantId(),
                    sessionId,
                    "chat",
                    normalizeModelRoute(modelRoute)
            );
            if (effectivePrompt == null || effectivePrompt.isBlank()) {
                return messages;
            }
            ArrayList<ChatMessageDTO> withPrompt = new ArrayList<>(messages.size() + 1);
            withPrompt.add(new ChatMessageDTO("system", effectivePrompt));
            withPrompt.addAll(messages);
            return withPrompt;
        } catch (Exception ex) {
            logger.warn("[prompt] resolve effective prompt failed, continue without it. sessionId={}", sessionId, ex);
            return messages;
        }
    }

    private String resolveEffectivePromptHash(ChatContext context, String sessionId, String modelRoute) {
        if (promptConfigService == null) {
            return "none";
        }
        try {
            String hash = promptConfigService.resolveEffectivePromptHash(
                    context.tenantId(),
                    sessionId,
                    "chat",
                    normalizeModelRoute(modelRoute)
            );
            return normalizeHash(hash);
        } catch (Exception ex) {
            logger.warn("[prompt] resolve effective prompt hash failed, fallback none. sessionId={}", sessionId, ex);
            return "none";
        }
    }

    private String normalizeModelRoute(String modelRoute) {
        return (modelRoute == null || modelRoute.isBlank()) ? "default" : modelRoute;
    }

    private String normalizeHash(String hash) {
        return (hash == null || hash.isBlank()) ? "none" : hash;
    }

    private int estimateOutputTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String normalized = text.trim();
        int charBased = Math.max(1, (normalized.length() + 3) / 4);
        int wordBased = normalized.split("\\s+").length;
        return Math.max(charBased, wordBased);
    }

    /**
     * 搜索知识库并获取引用信息
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchKnowledgeBase(ChatContext context, Object request) {
        try {
            // 获取用户输入（从最后一条消息）
            String query = extractUserQuery(request);
            if (query == null || query.isBlank()) {
                logger.debug("[knowledge:search] 无有效查询词，跳过检索");
                return null;
            }

            if (!shouldSearchKnowledge(query)) {
                logger.debug("[knowledge:search] 命中闲聊/低信息量输入，跳过检索 - query={}", query);
                return null;
            }
            
            logger.debug("[knowledge:search] 开始检索 - tenant={}, query={}", context.tenantId(), query);
            
            // 调用知识库搜索
            Map<String, Object> searchResult = knowledgeIngestionService.search(context, query);
            if (searchResult == null) {
                logger.warn("[knowledge:search] 搜索结果为 null - tenant={}, query={}", context.tenantId(), query);
                return null;
            }
            
            if (!searchResult.containsKey("hits")) {
                logger.warn("[knowledge:search] 搜索结果缺少 hits 字段 - tenant={}, result keys={}", 
                    context.tenantId(), searchResult.keySet());
                return null;
            }
            
            // 提取 hits 数组并转换为引用列表
            Object hits = searchResult.get("hits");
            if (hits instanceof List) {
                List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits;
                logger.debug("[knowledge:search] 检索成功 - tenant={}, hits count={}", context.tenantId(), hitsList.size());
                return hitsList;
            }
            
            logger.warn("[knowledge:search] hits 字段类型异常 - 期望 List，实际 {}", hits.getClass().getSimpleName());
            return null;
        } catch (Exception ex) {
            // 知识库搜索失败时不阻断对话流程，记录错误但继续
            logger.error("[knowledge:search] 搜索异常 - tenant={}", context.tenantId(), ex);
            return null;
        }
    }

    /**
     * 从请求中提取用户查询语句
     */
    private String extractUserQuery(Object request) {
        if (request instanceof ChatCompletionRequest req) {
            if (req.messages() != null && !req.messages().isEmpty()) {
                ChatMessageDTO lastMsg = req.messages().get(req.messages().size() - 1);
                return lastMsg.content();
            }
        } else if (request instanceof StreamChatRequest req) {
            if (req.messages() != null && !req.messages().isEmpty()) {
                ChatMessageDTO lastMsg = req.messages().get(req.messages().size() - 1);
                return lastMsg.content();
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractReferencesFromToolResult(ChatEventVO event) {
        if (event == null || event.toolResult() == null) {
            return null;
        }
        List<Map<String, Object>> references = extractHits(event.toolResult().get("result"));
        if (references != null && !references.isEmpty()) {
            return references;
        }
        references = extractHits(event.toolResult());
        if (references != null && !references.isEmpty()) {
            return references;
        }
        references = extractAttachmentReferences(event.toolResult().get("result"));
        if (references != null && !references.isEmpty()) {
            return references;
        }
        references = extractAttachmentReferences(event.toolResult());
        if (references != null && !references.isEmpty()) {
            return references;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractHits(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return null;
        }

        Object hits = map.get("hits");
        if (hits instanceof List<?> hitList) {
            return (List<Map<String, Object>>) hitList;
        }

        Object data = map.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object dataHits = dataMap.get("hits");
            if (dataHits instanceof List<?> hitList) {
                return (List<Map<String, Object>>) hitList;
            }
        }

        Object nestedResult = map.get("result");
        if (nestedResult instanceof Map<?, ?> nestedMap) {
            Object nestedHits = nestedMap.get("hits");
            if (nestedHits instanceof List<?> hitList) {
                return (List<Map<String, Object>>) hitList;
            }
            Object nestedData = nestedMap.get("data");
            if (nestedData instanceof Map<?, ?> nestedDataMap) {
                Object nestedDataHits = nestedDataMap.get("hits");
                if (nestedDataHits instanceof List<?> hitList) {
                    return (List<Map<String, Object>>) hitList;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractAttachmentReferences(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return null;
        }

        List<?> items = null;
        Object itemsObj = map.get("items");
        if (itemsObj instanceof List<?> itemList && !itemList.isEmpty()) {
            items = itemList;
        }
        if (items == null || items.isEmpty()) {
            Object dataObj = map.get("data");
            if (dataObj instanceof Map<?, ?> dataMap) {
                Object dataItems = dataMap.get("items");
                if (dataItems instanceof List<?> dataItemList) {
                    items = dataItemList;
                }
            }
        }
        if (items == null || items.isEmpty()) {
            Object nestedResult = map.get("result");
            if (nestedResult instanceof Map<?, ?> nestedMap) {
                return extractAttachmentReferences(nestedMap);
            }
            return null;
        }

        List<Map<String, Object>> references = new ArrayList<>();
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> itemMapRaw)) {
                continue;
            }
            Map<String, Object> itemMap = (Map<String, Object>) itemMapRaw;
            Map<String, Object> ref = new LinkedHashMap<>();

            Object id = itemMap.get("id");
            Object fileName = itemMap.get("fileName");
            if (id != null) {
                ref.put("id", id);
            }
            if (fileName != null) {
                ref.put("title", String.valueOf(fileName));
            } else {
                ref.put("title", "附件上下文");
            }
            ref.put("score", 1.0d);
            ref.put("payload", itemMap);
            references.add(ref);
        }

        return references.isEmpty() ? null : references;
    }

    private boolean shouldSearchKnowledge(String rawQuery) {
        if (rawQuery == null) {
            return false;
        }
        String normalized = normalizeQuery(rawQuery);
        if (normalized.isBlank()) {
            return false;
        }
        if (SMALL_TALK_QUERIES.contains(normalized)) {
            return false;
        }
        if (normalized.startsWith("你好") || normalized.startsWith("您好")) {
            return false;
        }
        return normalized.length() >= 3;
    }

    private String normalizeQuery(String query) {
        return query
                .toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。！？、；：‘’“”（）【】《》]+", "")
                .trim();
    }
}
