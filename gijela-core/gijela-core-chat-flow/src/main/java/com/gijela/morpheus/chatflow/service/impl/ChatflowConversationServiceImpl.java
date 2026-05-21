package com.gijela.morpheus.chatflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chatflow.domain.entity.ChatflowMessageEntity;
import com.gijela.morpheus.chatflow.domain.entity.ChatflowSessionEntity;
import com.gijela.morpheus.chatflow.dto.ChatflowCompletionDTO;
import com.gijela.morpheus.chatflow.dto.ChatflowSessionSaveDTO;
import com.gijela.morpheus.chatflow.dto.WorkflowDebugRunDTO;
import com.gijela.morpheus.chatflow.mapper.ChatflowMessageMapper;
import com.gijela.morpheus.chatflow.mapper.ChatflowSessionMapper;
import com.gijela.morpheus.chatflow.service.ChatflowConversationService;
import com.gijela.morpheus.chatflow.service.WorkflowRunService;
import com.gijela.morpheus.chatflow.service.WorkflowService;
import com.gijela.morpheus.chatflow.vo.ChatflowCompletionVO;
import com.gijela.morpheus.chatflow.vo.ChatflowMessagePageVO;
import com.gijela.morpheus.chatflow.vo.ChatflowMessageVO;
import com.gijela.morpheus.chatflow.vo.ChatflowSessionPageVO;
import com.gijela.morpheus.chatflow.vo.ChatflowSessionVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunStartVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ChatflowConversationServiceImpl implements ChatflowConversationService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_HISTORY_LIMIT = 10;

    private final ChatflowSessionMapper sessionMapper;
    private final ChatflowMessageMapper messageMapper;
    private final WorkflowService workflowService;
    private final WorkflowRunService workflowRunService;
    private final ObjectMapper objectMapper;

    public ChatflowConversationServiceImpl(ChatflowSessionMapper sessionMapper,
                                           ChatflowMessageMapper messageMapper,
                                           WorkflowService workflowService,
                                           WorkflowRunService workflowRunService,
                                           ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.workflowService = workflowService;
        this.workflowRunService = workflowRunService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatflowSessionPageVO listSessions(String keyword, Integer pageNum, Integer pageSize) {
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize, DEFAULT_PAGE_SIZE);
        int offset = (safePageNum - 1) * safePageSize;

        LambdaQueryWrapper<ChatflowSessionEntity> query = new LambdaQueryWrapper<ChatflowSessionEntity>()
                .eq(ChatflowSessionEntity::getDeleted, 0)
                .orderByDesc(ChatflowSessionEntity::getUpdatedAt)
                .orderByDesc(ChatflowSessionEntity::getId);

        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            query.and(q -> q.like(ChatflowSessionEntity::getTitle, k)
                    .or().like(ChatflowSessionEntity::getSessionId, k)
                    .or().like(ChatflowSessionEntity::getWorkflowId, k));
        }

        Long total = sessionMapper.selectCount(query);
        List<ChatflowSessionEntity> list = total == null || total == 0
                ? Collections.emptyList()
                : sessionMapper.selectList(query.last("limit " + offset + "," + safePageSize));

        ChatflowSessionPageVO vo = new ChatflowSessionPageVO();
        vo.setPageNum(safePageNum);
        vo.setPageSize(safePageSize);
        vo.setTotal(total == null ? 0L : total);
        vo.setList(list.stream().map(this::toSessionVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatflowSessionVO createSession(ChatflowSessionSaveDTO dto) {
        ensureWorkflowAvailable(dto.getWorkflowId());

        ChatflowSessionEntity entity = new ChatflowSessionEntity();
        entity.setSessionId(generateSessionId());
        entity.setTitle(dto.getTitle().trim());
        entity.setWorkflowId(dto.getWorkflowId().trim());
        entity.setWorkflowInputs(writeJson(dto.getWorkflowInputs()));
        entity.setStatus("active");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        sessionMapper.insert(entity);

        return toSessionVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatflowSessionVO updateSession(String sessionId, ChatflowSessionSaveDTO dto) {
        ChatflowSessionEntity entity = loadSession(sessionId);
        ensureWorkflowAvailable(dto.getWorkflowId());

        entity.setTitle(dto.getTitle().trim());
        entity.setWorkflowId(dto.getWorkflowId().trim());
        entity.setWorkflowInputs(writeJson(dto.getWorkflowInputs()));
        entity.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(entity);

        return toSessionVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        ChatflowSessionEntity entity = loadSession(sessionId);
        entity.setDeleted(1);
        entity.setStatus("disabled");
        entity.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(entity);
    }

    @Override
    public ChatflowMessagePageVO listMessages(String sessionId, Integer pageNum, Integer pageSize) {
        loadSession(sessionId);

        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize, 50);
        int offset = (safePageNum - 1) * safePageSize;

        LambdaQueryWrapper<ChatflowMessageEntity> query = new LambdaQueryWrapper<ChatflowMessageEntity>()
                .eq(ChatflowMessageEntity::getDeleted, 0)
                .eq(ChatflowMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatflowMessageEntity::getCreatedAt)
                .orderByAsc(ChatflowMessageEntity::getId);

        Long total = messageMapper.selectCount(query);
        List<ChatflowMessageEntity> list = total == null || total == 0
                ? Collections.emptyList()
                : messageMapper.selectList(query.last("limit " + offset + "," + safePageSize));

        ChatflowMessagePageVO vo = new ChatflowMessagePageVO();
        vo.setPageNum(safePageNum);
        vo.setPageSize(safePageSize);
        vo.setTotal(total == null ? 0L : total);
        vo.setList(list.stream().map(this::toMessageVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatflowCompletionVO completion(ChatflowCompletionDTO dto) {
        ChatflowSessionEntity session = loadSession(dto.getSessionId());
        if (!"active".equalsIgnoreCase(session.getStatus())) {
            throw new IllegalStateException("会话不可用: " + session.getSessionId());
        }

        WorkflowDetailVO workflow = ensureWorkflowAvailable(session.getWorkflowId());
        Integer publishedVersion = workflow.getPublishedVersion();
        if (publishedVersion == null || publishedVersion < 1) {
            throw new IllegalStateException("绑定工作流未发布: " + session.getWorkflowId());
        }

        String content = dto.getContent() == null ? null : dto.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        String historyJson = buildHistoryJson(session.getSessionId(), DEFAULT_HISTORY_LIMIT, content);

        LocalDateTime now = LocalDateTime.now();
        ChatflowMessageEntity userMessage = new ChatflowMessageEntity();
        userMessage.setSessionId(session.getSessionId());
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setExtraJson(null);
        userMessage.setCreatedAt(now);
        userMessage.setDeleted(0);
        messageMapper.insert(userMessage);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("sys.query", content);
    inputs.put("sys.chat_history", historyJson);
        inputs.put("sys.conversation_id", session.getSessionId());
        inputs.putAll(readMap(session.getWorkflowInputs()));

        WorkflowDebugRunDTO runDTO = new WorkflowDebugRunDTO();
        runDTO.setVersion(publishedVersion);
        runDTO.setInputs(inputs);
        runDTO.setTriggerBy(session.getSessionId());

        WorkflowRunStartVO run = workflowRunService.startDebugRun(session.getWorkflowId(), runDTO);
        CompletableFuture.runAsync(() -> finalizeRunAsync(session.getSessionId(), run.getRunId()));

        ChatflowCompletionVO vo = new ChatflowCompletionVO();
        vo.setSessionId(session.getSessionId());
        vo.setMessageId(formatMessageId(userMessage.getId()));
        vo.setAnswer("");
        vo.setWorkflowId(session.getWorkflowId());
        vo.setRunId(run.getRunId());
        vo.setCreatedAt(now.toString());
        return vo;
    }

    private void finalizeRunAsync(String sessionId, String runId) {
        int maxAttempts = 120;
        int intervalMs = 1000;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                WorkflowRunDetailVO runDetail = workflowRunService.getRunDetail(runId);
                String status = runDetail.getStatus() == null ? "" : runDetail.getStatus().trim().toLowerCase();

                if ("running".equals(status)) {
                    sleep(intervalMs);
                    continue;
                }

                if (!"success".equals(status)) {
                    return;
                }

                String answer = extractAnswer(runDetail.getFinalResult());
                ChatflowMessageEntity assistantMessage = new ChatflowMessageEntity();
                assistantMessage.setSessionId(sessionId);
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(answer);
                assistantMessage.setExtraJson(writeJson(Map.of("runId", runId)));
                assistantMessage.setCreatedAt(LocalDateTime.now());
                assistantMessage.setDeleted(0);
                messageMapper.insert(assistantMessage);

                ChatflowSessionEntity session = loadSession(sessionId);
                session.setUpdatedAt(LocalDateTime.now());
                sessionMapper.updateById(session);
                return;
            } catch (NoSuchElementException ex) {
                sleep(intervalMs);
            } catch (Exception ex) {
                return;
            }
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildHistoryJson(String sessionId, int limit, String currentContent) {
        LambdaQueryWrapper<ChatflowMessageEntity> query = new LambdaQueryWrapper<ChatflowMessageEntity>()
                .eq(ChatflowMessageEntity::getDeleted, 0)
                .eq(ChatflowMessageEntity::getSessionId, sessionId)
                .orderByDesc(ChatflowMessageEntity::getCreatedAt)
                .orderByDesc(ChatflowMessageEntity::getId)
                .last("limit " + limit);

        List<ChatflowMessageEntity> latest = messageMapper.selectList(query);
        Collections.reverse(latest);
        List<ChatflowMessageEntity> sanitized = new ArrayList<>();
        for (ChatflowMessageEntity message : latest) {
            if (message == null) {
                continue;
            }
            if (!sanitized.isEmpty()) {
                ChatflowMessageEntity previous = sanitized.get(sanitized.size() - 1);
                String previousRole = previous.getRole() == null ? "" : previous.getRole().trim();
                String currentRole = message.getRole() == null ? "" : message.getRole().trim();
                String previousContent = previous.getContent() == null ? "" : previous.getContent().trim();
                String currentMessageContent = message.getContent() == null ? "" : message.getContent().trim();
                if (previousRole.equals(currentRole) && previousContent.equals(currentMessageContent)) {
                    continue;
                }
            }
            sanitized.add(message);
        }

        String normalizedCurrent = currentContent == null ? "" : currentContent.trim();
        while (!sanitized.isEmpty()) {
            ChatflowMessageEntity tail = sanitized.get(sanitized.size() - 1);
            String tailRole = tail.getRole() == null ? "" : tail.getRole().trim();
            String tailContent = tail.getContent() == null ? "" : tail.getContent().trim();
            if (!"user".equalsIgnoreCase(tailRole) || !tailContent.equals(normalizedCurrent)) {
                break;
            }
            sanitized.remove(sanitized.size() - 1);
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (ChatflowMessageEntity message : sanitized) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            history.add(item);
        }
        return writeJson(history);
    }

    private String extractAnswer(Map<String, Object> finalResult) {
        if (finalResult == null || finalResult.isEmpty()) {
            throw new IllegalStateException("工作流无输出结果");
        }
        Object answer = finalResult.get("answer");
        if (answer == null) {
            throw new IllegalStateException("工作流输出缺少answer字段");
        }
        String text = String.valueOf(answer).trim();
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("工作流输出answer为空");
        }
        return text;
    }

    private WorkflowDetailVO ensureWorkflowAvailable(String workflowId) {
        WorkflowDetailVO detail = workflowService.getWorkflowDetail(workflowId);
        if (!"published".equalsIgnoreCase(detail.getStatus()) || detail.getPublishedVersion() == null || detail.getPublishedVersion() < 1) {
            throw new IllegalStateException("绑定工作流不可用: " + workflowId);
        }
        return detail;
    }

    private ChatflowSessionEntity loadSession(String sessionId) {
        ChatflowSessionEntity entity = sessionMapper.selectOne(new LambdaQueryWrapper<ChatflowSessionEntity>()
                .eq(ChatflowSessionEntity::getSessionId, sessionId)
                .eq(ChatflowSessionEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new NoSuchElementException("会话不存在: " + sessionId);
        }
        return entity;
    }

    private String generateSessionId() {
        return "cfs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String formatMessageId(Long id) {
        return id == null ? null : "cmsg_" + id;
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int normalizePageSize(Integer pageSize, int defaultSize) {
        int size = pageSize == null || pageSize < 1 ? defaultSize : pageSize;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private ChatflowSessionVO toSessionVO(ChatflowSessionEntity entity) {
        ChatflowSessionVO vo = new ChatflowSessionVO();
        vo.setSessionId(entity.getSessionId());
        vo.setTitle(entity.getTitle());
        vo.setWorkflowId(entity.getWorkflowId());
        vo.setStatus(entity.getStatus());
        vo.setWorkflowInputs(readMap(entity.getWorkflowInputs()));
        vo.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
        vo.setUpdatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString());
        return vo;
    }

    private ChatflowMessageVO toMessageVO(ChatflowMessageEntity entity) {
        ChatflowMessageVO vo = new ChatflowMessageVO();
        vo.setMessageId(formatMessageId(entity.getId()));
        vo.setRole(entity.getRole());
        vo.setContent(entity.getContent());
        Map<String, Object> extra = readMap(entity.getExtraJson());
        Object runId = extra.get("runId");
        vo.setRunId(runId == null ? null : String.valueOf(runId));
        vo.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
        return vo;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON序列化失败", e);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON反序列化失败", e);
        }
    }
}
