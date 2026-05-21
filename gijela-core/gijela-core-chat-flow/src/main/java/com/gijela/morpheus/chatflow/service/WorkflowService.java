package com.gijela.morpheus.chatflow.service;

import com.gijela.morpheus.chatflow.dto.WorkflowCreateDTO;
import com.gijela.morpheus.chatflow.dto.WorkflowDraftSaveDTO;
import com.gijela.morpheus.chatflow.dto.WorkflowUpdateDTO;
import com.gijela.morpheus.chatflow.vo.WorkflowCreateVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDraftDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDraftSaveVO;
import com.gijela.morpheus.chatflow.vo.WorkflowPublishVO;
import com.gijela.morpheus.chatflow.vo.WorkflowSummaryVO;
import com.gijela.morpheus.chatflow.vo.WorkflowValidateVO;

import java.util.List;

public interface WorkflowService {

    WorkflowCreateVO createWorkflow(WorkflowCreateDTO dto);

    List<WorkflowSummaryVO> listWorkflows(String name, String status, String appType);

    WorkflowDetailVO getWorkflowDetail(String workflowId);

    WorkflowDetailVO updateWorkflow(String workflowId, WorkflowUpdateDTO dto);

    void deleteWorkflow(String workflowId);

    WorkflowDraftSaveVO saveDraft(String workflowId, WorkflowDraftSaveDTO dto);

    WorkflowDraftDetailVO getDraft(String workflowId);

    WorkflowValidateVO validateWorkflow(String workflowId);

    WorkflowPublishVO publish(String workflowId);

    boolean exists(String workflowId);
}
