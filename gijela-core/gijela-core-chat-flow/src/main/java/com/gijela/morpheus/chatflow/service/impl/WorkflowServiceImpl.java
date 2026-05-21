package com.gijela.morpheus.chatflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gijela.morpheus.chatflow.domain.entity.WorkflowEntity;
import com.gijela.morpheus.chatflow.domain.entity.WorkflowVersionEntity;
import com.gijela.morpheus.chatflow.dto.WorkflowCreateDTO;
import com.gijela.morpheus.chatflow.dto.WorkflowDraftSaveDTO;
import com.gijela.morpheus.chatflow.dto.WorkflowUpdateDTO;
import com.gijela.morpheus.chatflow.mapper.WorkflowMapper;
import com.gijela.morpheus.chatflow.mapper.WorkflowVersionMapper;
import com.gijela.morpheus.chatflow.service.WorkflowService;
import com.gijela.morpheus.chatflow.vo.WorkflowCreateVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDraftDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDraftSaveVO;
import com.gijela.morpheus.chatflow.vo.WorkflowPublishVO;
import com.gijela.morpheus.chatflow.vo.WorkflowSummaryVO;
import com.gijela.morpheus.chatflow.vo.WorkflowValidateVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final ObjectMapper objectMapper;

    public WorkflowServiceImpl(WorkflowMapper workflowMapper,
                               WorkflowVersionMapper workflowVersionMapper,
                               ObjectMapper objectMapper) {
        this.workflowMapper = workflowMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowCreateVO createWorkflow(WorkflowCreateDTO dto) {
        WorkflowEntity entity = new WorkflowEntity();
        entity.setWorkflowCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAppType(dto.getAppType());
        entity.setStatus("draft");
        entity.setCurrentDraftVersion(1);
        entity.setCurrentPublishedVersion(null);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        workflowMapper.insert(entity);

        WorkflowCreateVO vo = new WorkflowCreateVO();
        vo.setWorkflowId(formatWorkflowId(entity.getId()));
        vo.setDraftVersion(entity.getCurrentDraftVersion());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    @Override
    public List<WorkflowSummaryVO> listWorkflows(String name, String status, String appType) {
        LambdaQueryWrapper<WorkflowEntity> query = new LambdaQueryWrapper<WorkflowEntity>()
                .eq(WorkflowEntity::getDeleted, 0)
                .orderByDesc(WorkflowEntity::getUpdatedAt);

        if (StringUtils.hasText(name)) {
            query.like(WorkflowEntity::getName, name.trim());
        }
        if (StringUtils.hasText(status)) {
            query.eq(WorkflowEntity::getStatus, status.trim());
        }
        if (StringUtils.hasText(appType)) {
            query.eq(WorkflowEntity::getAppType, appType.trim());
        }

        return workflowMapper.selectList(query).stream()
                .map(this::toSummaryVO)
                .collect(Collectors.toList());
    }

    @Override
    public WorkflowDetailVO getWorkflowDetail(String workflowId) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        WorkflowDetailVO detail = new WorkflowDetailVO();
        copySummary(detail, workflow);
        detail.setDescription(workflow.getDescription());
        try {
            detail.setDraft(buildDraftDetail(workflow));
        } catch (IllegalStateException e) {
            // 流程暂无可用版本，返回空 draft
            WorkflowDraftDetailVO emptyDraft = new WorkflowDraftDetailVO();
            emptyDraft.setWorkflowId(workflowId);
            emptyDraft.setVersion(1);
            emptyDraft.setDefinition(new java.util.LinkedHashMap<>());
            detail.setDraft(emptyDraft);
        }
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailVO updateWorkflow(String workflowId, WorkflowUpdateDTO dto) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());
        workflow.setUpdatedBy("system");
        workflow.setUpdatedAt(LocalDateTime.now());
        workflowMapper.updateById(workflow);
        return getWorkflowDetail(workflowId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWorkflow(String workflowId) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        workflow.setDeleted(1);
        workflow.setUpdatedBy("system");
        workflow.setUpdatedAt(LocalDateTime.now());
        workflowMapper.updateById(workflow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDraftSaveVO saveDraft(String workflowId, WorkflowDraftSaveDTO dto) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        Integer currentDraftVersion = workflow.getCurrentDraftVersion();
        if (currentDraftVersion == null) {
            currentDraftVersion = 1;
        }
        if (!currentDraftVersion.equals(dto.getVersion())) {
            throw new IllegalArgumentException("草稿版本已过期，请刷新后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        WorkflowVersionEntity draft = new WorkflowVersionEntity();
        draft.setWorkflowId(workflow.getId());
        draft.setVersionNo(dto.getVersion());
        draft.setVersionType("draft");
        draft.setDefinitionJson(writeJson(objectMapper.valueToTree(dto.getDefinition())));
        draft.setCreatedBy("system");
        draft.setCreatedAt(now);
        workflowVersionMapper.insert(draft);

        workflow.setCurrentDraftVersion(dto.getVersion() + 1);
        workflow.setUpdatedBy("system");
        workflow.setUpdatedAt(now);
        workflowMapper.updateById(workflow);

        WorkflowDraftSaveVO vo = new WorkflowDraftSaveVO();
        vo.setWorkflowId(formatWorkflowId(workflow.getId()));
        vo.setDraftVersion(workflow.getCurrentDraftVersion());
        vo.setSavedAt(now.toString());
        return vo;
    }

    @Override
    public WorkflowDraftDetailVO getDraft(String workflowId) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        return buildDraftDetail(workflow);
    }

    @Override
    public WorkflowValidateVO validateWorkflow(String workflowId) {
        WorkflowEntity workflow = loadWorkflow(workflowId);

        List<String> issues = new ArrayList<>();
        Integer version = null;
        Map<String, Object> definition = null;

        try {
            WorkflowDraftDetailVO draft = buildDraftDetail(workflow);
            version = draft.getVersion();
            definition = draft.getDefinition();
        } catch (IllegalStateException | IllegalArgumentException | NoSuchElementException ex) {
            issues.add(ex.getMessage());
        }

        if (definition != null) {
            try {
                JsonNode definitionNode = objectMapper.valueToTree(definition);
                validateDefinition(workflow.getAppType(), definitionNode);
            } catch (IllegalArgumentException ex) {
                issues.add(ex.getMessage());
            }
        }

        WorkflowValidateVO vo = new WorkflowValidateVO();
        vo.setWorkflowId(formatWorkflowId(workflow.getId()));
        vo.setVersion(version);
        vo.setValid(issues.isEmpty());
        vo.setIssues(issues);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowPublishVO publish(String workflowId) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        Integer draftVersionNo = resolveLatestDraftVersionNo(workflow);
        WorkflowVersionEntity draft = loadDraftVersion(workflow, draftVersionNo);

        WorkflowVersionEntity published = workflowVersionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersionEntity>()
                .eq(WorkflowVersionEntity::getWorkflowId, workflow.getId())
                .eq(WorkflowVersionEntity::getVersionNo, draftVersionNo)
                .eq(WorkflowVersionEntity::getVersionType, "published"));

        LocalDateTime now = LocalDateTime.now();
        if (published == null) {
            published = new WorkflowVersionEntity();
            published.setWorkflowId(workflow.getId());
            published.setVersionNo(draftVersionNo);
            published.setVersionType("published");
            published.setDefinitionJson(draft.getDefinitionJson());
            published.setPublishedBy("system");
            published.setPublishedAt(now);
            published.setCreatedBy("system");
            published.setCreatedAt(now);
            workflowVersionMapper.insert(published);
        }

        workflow.setStatus("published");
        workflow.setCurrentPublishedVersion(draftVersionNo);
        workflow.setUpdatedBy("system");
        workflow.setUpdatedAt(now);
        workflowMapper.updateById(workflow);

        WorkflowPublishVO vo = new WorkflowPublishVO();
        vo.setWorkflowId(workflowId);
        vo.setPublishedVersion(draftVersionNo);
        vo.setStatus(workflow.getStatus());
        vo.setPublishedAt(now.toString());
        return vo;
    }

    @Override
    public boolean exists(String workflowId) {
        return workflowMapper.selectById(parseWorkflowId(workflowId)) != null;
    }

    WorkflowEntity loadWorkflow(String workflowId) {
        Long id = parseWorkflowId(workflowId);
        WorkflowEntity workflow = workflowMapper.selectById(id);
        if (workflow == null || Integer.valueOf(1).equals(workflow.getDeleted())) {
            throw new NoSuchElementException("流程不存在: " + workflowId);
        }
        return workflow;
    }

    WorkflowVersionEntity loadDraftVersion(WorkflowEntity workflow, Integer requestedVersion) {
        Integer draftVersion = workflow.getCurrentDraftVersion();
        Integer versionNo = requestedVersion != null && requestedVersion.equals(draftVersion)
                ? draftVersion - 1
                : requestedVersion;
        if (versionNo == null || versionNo < 1) {
            throw new NoSuchElementException("草稿版本不存在: " + requestedVersion);
        }

        WorkflowVersionEntity version = workflowVersionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersionEntity>()
                .eq(WorkflowVersionEntity::getWorkflowId, workflow.getId())
                .eq(WorkflowVersionEntity::getVersionNo, versionNo)
                .eq(WorkflowVersionEntity::getVersionType, "draft"));
        if (version == null) {
            throw new NoSuchElementException("草稿版本不存在: " + requestedVersion);
        }
        return version;
    }

    private WorkflowDraftDetailVO buildDraftDetail(WorkflowEntity workflow) {
        Integer draftVersionNo = resolveLatestDraftVersionNo(workflow);
        WorkflowVersionEntity draft = loadDraftVersion(workflow, draftVersionNo);
        WorkflowDraftDetailVO draftDetail = new WorkflowDraftDetailVO();
        draftDetail.setWorkflowId(formatWorkflowId(workflow.getId()));
        draftDetail.setVersion(draft.getVersionNo());
        draftDetail.setDefinition(readMap(draft.getDefinitionJson()));
        return draftDetail;
    }

    private Integer resolveLatestDraftVersionNo(WorkflowEntity workflow) {
        Integer currentDraftVersion = workflow.getCurrentDraftVersion();
        Integer draftVersionNo = currentDraftVersion == null ? null : currentDraftVersion - 1;
        if (draftVersionNo == null || draftVersionNo < 1) {
            throw new IllegalStateException("当前流程无可用草稿版本");
        }
        return draftVersionNo;
    }

    private WorkflowSummaryVO toSummaryVO(WorkflowEntity entity) {
        WorkflowSummaryVO vo = new WorkflowSummaryVO();
        copySummary(vo, entity);
        return vo;
    }

    private void copySummary(WorkflowSummaryVO vo, WorkflowEntity entity) {
        vo.setWorkflowId(formatWorkflowId(entity.getId()));
        vo.setCode(entity.getWorkflowCode());
        vo.setName(entity.getName());
        vo.setAppType(entity.getAppType());
        vo.setStatus(entity.getStatus());
        vo.setDraftVersion(entity.getCurrentDraftVersion());
        vo.setPublishedVersion(entity.getCurrentPublishedVersion());
        vo.setUpdatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString());
    }

    private void validateDefinition(String appType, JsonNode definitionNode) {
        if (definitionNode == null || definitionNode.isNull()) {
            throw new IllegalArgumentException("流程定义不能为空");
        }
        JsonNode nodes = definitionNode.get("nodes");
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            throw new IllegalArgumentException("流程定义必须包含 nodes");
        }

        boolean hasStart = false;
        boolean hasEnd = false;
        for (JsonNode node : nodes) {
            String type = text(node, "type");
            if ("start".equals(type)) {
                hasStart = true;
            }
            if ("end".equals(type)) {
                hasEnd = true;
            }
        }

        if (!hasStart) {
            throw new IllegalArgumentException("流程定义缺少 start 节点");
        }
        if ("workflow".equalsIgnoreCase(appType) && !hasEnd) {
            throw new IllegalArgumentException("Workflow 定义缺少 end 节点");
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("流程定义序列化失败", e);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("流程定义反序列化失败", e);
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long parseWorkflowId(String workflowId) {
        try {
            String normalized = workflowId.startsWith("wf_") ? workflowId.substring(3) : workflowId;
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("workflowId 格式不正确: " + workflowId, ex);
        }
    }

    private String formatWorkflowId(Long id) {
        return "wf_" + id;
    }
}
