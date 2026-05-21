package com.gijela.morpheus.chatflow.controller;

import com.gijela.morpheus.chatflow.dto.WorkflowCreateDTO;
import com.gijela.morpheus.chatflow.dto.WorkflowDebugRunDTO;
import com.gijela.morpheus.chatflow.dto.WorkflowDraftSaveDTO;
import com.gijela.morpheus.chatflow.dto.WorkflowUpdateDTO;
import com.gijela.morpheus.chatflow.service.WorkflowRunService;
import com.gijela.morpheus.chatflow.service.WorkflowService;
import com.gijela.morpheus.chatflow.vo.WorkflowCreateVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDraftDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowDraftSaveVO;
import com.gijela.morpheus.chatflow.vo.WorkflowPublishVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunDetailVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunPageVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunStartVO;
import com.gijela.morpheus.chatflow.vo.WorkflowRunSummaryVO;
import com.gijela.morpheus.chatflow.vo.WorkflowSummaryVO;
import com.gijela.morpheus.chatflow.vo.WorkflowValidateVO;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.common.enums.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/chat-flow")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowRunService workflowRunService;

    public WorkflowController(WorkflowService workflowService, WorkflowRunService workflowRunService) {
        this.workflowService = workflowService;
        this.workflowRunService = workflowRunService;
    }

    @PostMapping("/workflows")
    public ApiResponse<WorkflowCreateVO> createWorkflow(@RequestBody @Valid WorkflowCreateDTO dto) {
        try {
            return ApiResponse.ok(workflowService.createWorkflow(dto));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage(), null);
        }
    }

    @GetMapping("/workflows")
    public ApiResponse<List<WorkflowSummaryVO>> listWorkflows(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String appType) {
        return ApiResponse.ok(workflowService.listWorkflows(name, status, appType));
    }

    @GetMapping("/workflows/{workflowId}")
    public ApiResponse<WorkflowDetailVO> getWorkflowDetail(@PathVariable String workflowId) {
        try {
            return ApiResponse.ok(workflowService.getWorkflowDetail(workflowId));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @PutMapping("/workflows/{workflowId}")
    public ApiResponse<WorkflowDetailVO> updateWorkflow(@PathVariable String workflowId,
                                                         @RequestBody @Valid WorkflowUpdateDTO dto) {
        try {
            return ApiResponse.ok(workflowService.updateWorkflow(workflowId, dto));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @DeleteMapping("/workflows/{workflowId}")
    public ApiResponse<Void> deleteWorkflow(@PathVariable String workflowId) {
        try {
            workflowService.deleteWorkflow(workflowId);
            return ApiResponse.ok(null);
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @PutMapping("/workflows/{workflowId}/draft")
    public ApiResponse<WorkflowDraftSaveVO> saveDraft(@PathVariable String workflowId,
                                                      @RequestBody @Valid WorkflowDraftSaveDTO dto) {
        try {
            return ApiResponse.ok(workflowService.saveDraft(workflowId, dto));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ErrorCode.CONFLICT.getCode(), ex.getMessage(), null);
        }
    }

    @GetMapping("/workflows/{workflowId}/draft")
    public ApiResponse<WorkflowDraftDetailVO> getDraft(@PathVariable String workflowId) {
        try {
            return ApiResponse.ok(workflowService.getDraft(workflowId));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @PostMapping("/workflows/{workflowId}/validate")
    public ApiResponse<WorkflowValidateVO> validateWorkflow(@PathVariable String workflowId) {
        try {
            return ApiResponse.ok(workflowService.validateWorkflow(workflowId));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @PostMapping("/workflows/{workflowId}/publish")
    public ApiResponse<WorkflowPublishVO> publish(@PathVariable String workflowId) {
        try {
            return ApiResponse.ok(workflowService.publish(workflowId));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        } catch (IllegalStateException ex) {
            return ApiResponse.fail(ErrorCode.ILLEGAL_STATE.getCode(), ex.getMessage(), null);
        }
    }

    @PostMapping("/workflows/{workflowId}/debug-runs")
    public ApiResponse<WorkflowRunStartVO> debugRun(@PathVariable String workflowId,
                                                    @RequestBody @Valid WorkflowDebugRunDTO dto) {
        try {
            return ApiResponse.ok(workflowRunService.startDebugRun(workflowId, dto));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage(), null);
        } catch (IllegalStateException ex) {
            return ApiResponse.fail(ErrorCode.ILLEGAL_STATE.getCode(), ex.getMessage(), null);
        }
    }

    @PostMapping("/workflows/{workflowId}/runs")
    public ApiResponse<WorkflowRunStartVO> run(@PathVariable String workflowId,
                                                @RequestBody @Valid WorkflowDebugRunDTO dto) {
        try {
            return ApiResponse.ok(workflowRunService.startRun(workflowId, dto));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage(), null);
        } catch (IllegalStateException ex) {
            return ApiResponse.fail(ErrorCode.ILLEGAL_STATE.getCode(), ex.getMessage(), null);
        }
    }

    @GetMapping("/workflows/{workflowId}/runs")
    public ApiResponse<WorkflowRunPageVO> listRuns(@PathVariable String workflowId,
                                                   @RequestParam(required = false) String runType,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String triggerBy,
                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return ApiResponse.ok(workflowRunService.listRuns(workflowId, runType, status, triggerBy, pageNum, pageSize));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @GetMapping("/debug-runs/{runId}")
    public ApiResponse<WorkflowRunDetailVO> getDebugRunDetail(@PathVariable String runId) {
        try {
            return ApiResponse.ok(workflowRunService.getRunDetail(runId));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<WorkflowRunDetailVO> getRunDetail(@PathVariable String runId) {
        try {
            return ApiResponse.ok(workflowRunService.getRunDetail(runId));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }
}
