package com.gijela.morpheus.chatflow.vo;

import java.util.Map;

public class WorkflowRunStartVO {

    private String runId;
    private String status;
    private Map<String, Object> finalResult;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(Map<String, Object> finalResult) {
        this.finalResult = finalResult;
    }
}
