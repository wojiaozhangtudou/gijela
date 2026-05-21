package com.gijela.morpheus.chatflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gijela.morpheus.chatflow.domain.entity.WorkflowEntity;
import com.gijela.morpheus.chatflow.domain.entity.WorkflowRunEntity;
import com.gijela.morpheus.chatflow.domain.entity.WorkflowRunNodeEntity;
import com.gijela.morpheus.chatflow.domain.entity.WorkflowVersionEntity;
import com.gijela.morpheus.chatflow.dto.WorkflowDebugRunDTO;
import com.gijela.morpheus.chatflow.engine.NodeExecutionRecord;
import com.gijela.morpheus.chatflow.engine.RunContext;
import com.gijela.morpheus.chatflow.engine.WorkflowEngine;
import com.gijela.morpheus.chatflow.engine.WorkflowEngineResult;
import com.gijela.morpheus.chatflow.mapper.WorkflowMapper;
import com.gijela.morpheus.chatflow.mapper.WorkflowRunMapper;
import com.gijela.morpheus.chatflow.mapper.WorkflowRunNodeMapper;
import com.gijela.morpheus.chatflow.mapper.WorkflowVersionMapper;
import com.gijela.morpheus.chatflow.service.WorkflowRunService;
import com.gijela.morpheus.chatflow.vo.WorkflowRunDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunPageVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunStartVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunSummaryVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class WorkflowRunServiceImpl implements WorkflowRunService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowRunNodeMapper workflowRunNodeMapper;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;

    public WorkflowRunServiceImpl(WorkflowMapper workflowMapper,
                                 WorkflowVersionMapper workflowVersionMapper,
                                 WorkflowRunMapper workflowRunMapper,
                                 WorkflowRunNodeMapper workflowRunNodeMapper,
                                 WorkflowEngine workflowEngine,
                                 ObjectMapper objectMapper,
                                 @Qualifier("chatFlowTaskExecutor") Executor taskExecutor) {
        this.workflowMapper = workflowMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowRunMapper = workflowRunMapper;
        this.workflowRunNodeMapper = workflowRunNodeMapper;
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRunStartVO startDebugRun(String workflowId, WorkflowDebugRunDTO dto) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        Integer versionNo = resolveRunVersion(workflow, dto.getVersion(), true);
        WorkflowVersionEntity version = resolveVersion(workflow, versionNo, true);
        JsonNode definitionNode = parseJson(version.getDefinitionJson());

        String runId = "run_dbg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime startedAt = LocalDateTime.now();

        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setRunId(runId);
        run.setWorkflowId(workflow.getId());
        run.setWorkflowVersion(versionNo);
        run.setRunType("debug");
        run.setStatus("running");
        run.setTriggerBy(resolveTriggerBy(dto));
        run.setStartedAt(startedAt);
        run.setCreatedAt(startedAt);
        workflowRunMapper.insert(run);

        Map<String, Object> requestInputs = dto == null || dto.getInputs() == null
                ? Map.of()
                : new LinkedHashMap<>(dto.getInputs());

        CompletableFuture.runAsync(() -> executeDebugRunAsync(workflowId, runId, versionNo, workflow.getAppType(), definitionNode, requestInputs), taskExecutor);

        WorkflowRunStartVO vo = new WorkflowRunStartVO();
        vo.setRunId(runId);
        vo.setStatus("running");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRunStartVO startRun(String workflowId, WorkflowDebugRunDTO dto) {
        return startRunInternal(workflowId, dto, false);
    }

    private void executeDebugRunAsync(String workflowId,
                                      String runId,
                                      Integer versionNo,
                                      String appType,
                                      JsonNode definitionNode,
                                      Map<String, Object> requestInputs) {
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            RunContext runContext = new RunContext();
            runContext.setRunId(runId);
            runContext.setWorkflowId(workflowId);
            runContext.setWorkflowVersion(versionNo);
            runContext.setAppType(appType);

            WorkflowEngineResult engineResult = workflowEngine.executeB1(
                    definitionNode,
                    runContext,
                    requestInputs,
                    record -> upsertRunNodeRecord(runId, record)
            );

            LocalDateTime endedAt = LocalDateTime.now();
            long durationMs = Math.max(1L, java.time.Duration.between(startedAt, endedAt).toMillis());
            String status = engineResult.getStatus() == null ? "failed" : engineResult.getStatus();
            Map<String, Object> finalResult = engineResult.getFinalResult() == null ? Map.of() : engineResult.getFinalResult();

            WorkflowRunEntity run = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
                    .eq(WorkflowRunEntity::getRunId, runId)
                    .last("limit 1"));
            if (run == null) {
                return;
            }
            run.setStatus(status);
            run.setEndedAt(endedAt);
            run.setDurationMs(durationMs);
            run.setFinalResultJson(writeJson(finalResult));
            run.setErrorCode("success".equals(status) ? null : "NODE_EXECUTION_FAILED");
            run.setErrorMessage(engineResult.getErrorMessage());
            workflowRunMapper.updateById(run);
        } catch (Exception ex) {
            LocalDateTime endedAt = LocalDateTime.now();
            long durationMs = Math.max(1L, java.time.Duration.between(startedAt, endedAt).toMillis());
            WorkflowRunEntity run = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
                    .eq(WorkflowRunEntity::getRunId, runId)
                    .last("limit 1"));
            if (run != null) {
                run.setStatus("failed");
                run.setEndedAt(endedAt);
                run.setDurationMs(durationMs);
                run.setErrorCode("NODE_EXECUTION_FAILED");
                run.setErrorMessage(ex.getMessage());
                workflowRunMapper.updateById(run);
            }
        }
    }

    @Override
    public WorkflowRunPageVO listRuns(String workflowId, String runType, String status, String triggerBy, Integer pageNum, Integer pageSize) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        LambdaQueryWrapper<WorkflowRunEntity> query = new LambdaQueryWrapper<WorkflowRunEntity>()
                .eq(WorkflowRunEntity::getWorkflowId, workflow.getId())
                .orderByDesc(WorkflowRunEntity::getStartedAt);

        if (runType != null && !runType.isBlank()) {
            query.eq(WorkflowRunEntity::getRunType, runType.trim());
        }
        if (status != null && !status.isBlank()) {
            query.eq(WorkflowRunEntity::getStatus, status.trim());
        }
        if (triggerBy != null && !triggerBy.isBlank()) {
            query.eq(WorkflowRunEntity::getTriggerBy, triggerBy.trim());
        }

        List<WorkflowRunSummaryVO> all = workflowRunMapper.selectList(query).stream()
                .map(entity -> {
                    WorkflowRunSummaryVO vo = new WorkflowRunSummaryVO();
                    vo.setRunId(entity.getRunId());
                    vo.setWorkflowId("wf_" + entity.getWorkflowId());
                    vo.setWorkflowVersion(entity.getWorkflowVersion());
                    vo.setRunType(entity.getRunType());
                    vo.setTriggerBy(entity.getTriggerBy());
                    vo.setStatus(entity.getStatus());
                    vo.setStartedAt(entity.getStartedAt() == null ? null : entity.getStartedAt().toString());
                    vo.setEndedAt(entity.getEndedAt() == null ? null : entity.getEndedAt().toString());
                    vo.setDurationMs(entity.getDurationMs());
                    return vo;
                })
                .collect(Collectors.toList());

        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        int fromIndex = (safePageNum - 1) * safePageSize;
        int toIndex = Math.min(fromIndex + safePageSize, all.size());

        List<WorkflowRunSummaryVO> pageList;
        if (fromIndex >= all.size()) {
            pageList = List.of();
        } else {
            pageList = all.subList(fromIndex, toIndex);
        }

        WorkflowRunPageVO page = new WorkflowRunPageVO();
        page.setPageNum(safePageNum);
        page.setPageSize(safePageSize);
        page.setTotal((long) all.size());
        page.setList(pageList);
        return page;
    }

    private WorkflowRunStartVO startRunInternal(String workflowId, WorkflowDebugRunDTO dto, boolean debugMode) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        Integer versionNo = resolveRunVersion(workflow, dto.getVersion(), debugMode);
        WorkflowVersionEntity version = resolveVersion(workflow, versionNo, debugMode);
        JsonNode definitionNode = parseJson(version.getDefinitionJson());

        String prefix = debugMode ? "run_dbg_" : "run_prod_";
        String runId = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime startedAt = LocalDateTime.now();

        RunContext runContext = new RunContext();
        runContext.setRunId(runId);
        runContext.setWorkflowId(workflowId);
        runContext.setWorkflowVersion(versionNo);
        runContext.setAppType(workflow.getAppType());

        WorkflowEngineResult engineResult = workflowEngine.executeB1(definitionNode, runContext, dto.getInputs());
        LocalDateTime endedAt = LocalDateTime.now();
        long durationMs = Math.max(1L, java.time.Duration.between(startedAt, endedAt).toMillis());
        String status = engineResult.getStatus() == null ? "failed" : engineResult.getStatus();
        Map<String, Object> finalResult = engineResult.getFinalResult() == null ? Map.of() : engineResult.getFinalResult();

        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setRunId(runId);
        run.setWorkflowId(workflow.getId());
        run.setWorkflowVersion(versionNo);
        run.setRunType(debugMode ? "debug" : "prod");
        run.setStatus(status);
        run.setTriggerBy(resolveTriggerBy(dto));
        run.setStartedAt(startedAt);
        run.setEndedAt(endedAt);
        run.setDurationMs(durationMs);
        run.setFinalResultJson(writeJson(finalResult));
        run.setErrorCode("success".equals(status) ? null : "NODE_EXECUTION_FAILED");
        run.setErrorMessage(engineResult.getErrorMessage());
        run.setCreatedAt(startedAt);
        workflowRunMapper.insert(run);

        List<WorkflowRunNodeEntity> nodes = buildNodeEntities(runId, engineResult.getNodeRecords());
        for (WorkflowRunNodeEntity node : nodes) {
            workflowRunNodeMapper.insert(node);
        }

        WorkflowRunStartVO vo = new WorkflowRunStartVO();
        vo.setRunId(runId);
        vo.setStatus(run.getStatus());
        vo.setFinalResult(finalResult);
        return vo;
    }

    private List<WorkflowRunNodeEntity> buildNodeEntities(String runId, List<NodeExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<WorkflowRunNodeEntity> nodes = new ArrayList<>();
        for (NodeExecutionRecord record : records) {
            nodes.add(toNodeEntity(runId, record));
        }
        return nodes;
    }

    private WorkflowRunNodeEntity toNodeEntity(String runId, NodeExecutionRecord record) {
        WorkflowRunNodeEntity entity = new WorkflowRunNodeEntity();
        entity.setRunId(runId);
        entity.setNodeId(record.getNodeId());
        entity.setNodeType(record.getNodeType());
        entity.setStatus(record.getStatus());
        entity.setStartedAt(record.getStartedAt());
        entity.setEndedAt(record.getEndedAt());
        entity.setDurationMs(record.getDurationMs());
        entity.setInputSnapshot(writeJson(record.getInputSnapshot()));
        entity.setOutputSnapshot(writeJson(record.getOutputSnapshot()));
        entity.setErrorCode("failed".equals(record.getStatus()) ? "NODE_EXECUTION_FAILED" : null);
        entity.setErrorMessage(record.getErrorMessage());
        entity.setCreatedAt(record.getStartedAt() == null ? LocalDateTime.now() : record.getStartedAt());
        return entity;
    }

    private void upsertRunNodeRecord(String runId, NodeExecutionRecord record) {
        if (record == null || record.getNodeId() == null || record.getNodeId().isBlank()) {
            return;
        }
        WorkflowRunNodeEntity existing = workflowRunNodeMapper.selectOne(new LambdaQueryWrapper<WorkflowRunNodeEntity>()
                .eq(WorkflowRunNodeEntity::getRunId, runId)
                .eq(WorkflowRunNodeEntity::getNodeId, record.getNodeId())
                .last("limit 1"));

        if (existing == null) {
            workflowRunNodeMapper.insert(toNodeEntity(runId, record));
            return;
        }

        existing.setNodeType(record.getNodeType());
        existing.setStatus(record.getStatus());
        existing.setStartedAt(record.getStartedAt() == null ? existing.getStartedAt() : record.getStartedAt());
        existing.setEndedAt(record.getEndedAt());
        existing.setDurationMs(record.getDurationMs());
        existing.setInputSnapshot(writeJson(record.getInputSnapshot()));
        existing.setOutputSnapshot(writeJson(record.getOutputSnapshot()));
        existing.setErrorCode("failed".equals(record.getStatus()) ? "NODE_EXECUTION_FAILED" : null);
        existing.setErrorMessage(record.getErrorMessage());
        workflowRunNodeMapper.updateById(existing);
    }

    @Override
    public WorkflowRunDetailVO getRunDetail(String runId) {
        WorkflowRunEntity run = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
                .eq(WorkflowRunEntity::getRunId, runId));
        if (run == null) {
            throw new NoSuchElementException("运行记录不存在: " + runId);
        }

        List<WorkflowRunNodeEntity> nodeEntities = workflowRunNodeMapper.selectList(new LambdaQueryWrapper<WorkflowRunNodeEntity>()
                .eq(WorkflowRunNodeEntity::getRunId, runId)
                .orderByAsc(WorkflowRunNodeEntity::getId));

        WorkflowRunDetailVO detail = new WorkflowRunDetailVO();
        detail.setRunId(run.getRunId());
        detail.setWorkflowId("wf_" + run.getWorkflowId());
        detail.setWorkflowVersion(run.getWorkflowVersion());
        detail.setRunType(run.getRunType());
        detail.setStatus(run.getStatus());
        detail.setStartedAt(run.getStartedAt() == null ? null : run.getStartedAt().toString());
        detail.setEndedAt(run.getEndedAt() == null ? null : run.getEndedAt().toString());
        detail.setDurationMs(run.getDurationMs());
        detail.setFinalResult(readMap(run.getFinalResultJson()));

        List<WorkflowRunDetailVO.NodeTraceVO> traces = new ArrayList<>();
        for (WorkflowRunNodeEntity entity : nodeEntities) {
            WorkflowRunDetailVO.NodeTraceVO trace = new WorkflowRunDetailVO.NodeTraceVO();
            trace.setNodeId(entity.getNodeId());
            trace.setNodeType(entity.getNodeType());
            trace.setStatus(entity.getStatus());
            trace.setDurationMs(entity.getDurationMs());
            trace.setInputSnapshot(readMap(entity.getInputSnapshot()));
            trace.setOutputSnapshot(readMap(entity.getOutputSnapshot()));
            trace.setErrorMessage(entity.getErrorMessage());
            traces.add(trace);
        }
        detail.setNodes(traces);
        return detail;
    }

    private WorkflowEntity loadWorkflow(String workflowId) {
        Long id = parseWorkflowId(workflowId);
        WorkflowEntity workflow = workflowMapper.selectById(id);
        if (workflow == null || Objects.equals(workflow.getDeleted(), 1)) {
            throw new NoSuchElementException("流程不存在: " + workflowId);
        }
        return workflow;
    }

    private WorkflowVersionEntity resolveVersion(WorkflowEntity workflow, Integer requestedVersion, boolean debugMode) {
        if (requestedVersion == null) {
            throw new IllegalArgumentException("运行版本不能为空");
        }

        if (!debugMode) {
            WorkflowVersionEntity published = workflowVersionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersionEntity>()
                    .eq(WorkflowVersionEntity::getWorkflowId, workflow.getId())
                    .eq(WorkflowVersionEntity::getVersionNo, requestedVersion)
                    .eq(WorkflowVersionEntity::getVersionType, "published"));
            if (published == null) {
                throw new IllegalStateException("指定发布版本不存在: " + requestedVersion);
            }
            return published;
        }

        Integer draftVersion = workflow.getCurrentDraftVersion();
        Integer publishedVersion = workflow.getCurrentPublishedVersion();
        Integer versionNo = requestedVersion;

        if (requestedVersion != null && requestedVersion.equals(draftVersion) && draftVersion != null && draftVersion > 1) {
            versionNo = draftVersion - 1;
        } else if (requestedVersion != null && requestedVersion.equals(draftVersion)) {
            versionNo = 1;
        }

        WorkflowVersionEntity version = workflowVersionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersionEntity>()
                .eq(WorkflowVersionEntity::getWorkflowId, workflow.getId())
                .eq(WorkflowVersionEntity::getVersionNo, versionNo)
                .eq(WorkflowVersionEntity::getVersionType, "draft"));

        if (version == null && publishedVersion != null && publishedVersion.equals(requestedVersion)) {
            version = workflowVersionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersionEntity>()
                    .eq(WorkflowVersionEntity::getWorkflowId, workflow.getId())
                    .eq(WorkflowVersionEntity::getVersionNo, publishedVersion)
                    .eq(WorkflowVersionEntity::getVersionType, "published"));
        }

        if (version == null) {
            throw new NoSuchElementException("运行版本不存在: " + requestedVersion);
        }
        return version;
    }

    private Integer resolveRunVersion(WorkflowEntity workflow, Integer requestedVersion, boolean debugMode) {
        if (requestedVersion != null) {
            return requestedVersion;
        }

        if (debugMode) {
            Integer draftVersion = workflow.getCurrentDraftVersion();
            if (draftVersion == null || draftVersion <= 1) {
                return 1;
            }
            return draftVersion - 1;
        }

        Integer publishedVersion = workflow.getCurrentPublishedVersion();
        if (publishedVersion == null || publishedVersion < 1) {
            throw new IllegalStateException("流程尚未发布，无法执行正式运行");
        }
        return publishedVersion;
    }

    private String resolveTriggerBy(WorkflowDebugRunDTO dto) {
        if (dto == null || dto.getTriggerBy() == null || dto.getTriggerBy().isBlank()) {
            return "system";
        }
        return dto.getTriggerBy().trim();
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("流程定义 JSON 解析失败", e);
        }
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 序列化失败", e);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 反序列化失败", e);
        }
    }

    private Long parseWorkflowId(String workflowId) {
        try {
            String normalized = workflowId.startsWith("wf_") ? workflowId.substring(3) : workflowId;
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("workflowId 格式不正确: " + workflowId, ex);
        }
    }
}
