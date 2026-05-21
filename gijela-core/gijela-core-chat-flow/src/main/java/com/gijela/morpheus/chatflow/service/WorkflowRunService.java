package com.gijela.morpheus.chatflow.service;

import com.gijela.morpheus.chatflow.dto.WorkflowDebugRunDTO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunPageVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunStartVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunSummaryVO;

import java.util.List;

public interface WorkflowRunService {

    WorkflowRunStartVO startDebugRun(String workflowId, WorkflowDebugRunDTO dto);

    WorkflowRunStartVO startRun(String workflowId, WorkflowDebugRunDTO dto);

    WorkflowRunPageVO listRuns(String workflowId, String runType, String status, String triggerBy, Integer pageNum, Integer pageSize);

    WorkflowRunDetailVO getRunDetail(String runId);
}
