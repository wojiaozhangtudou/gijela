package com.gijela.morpheus.chatflow.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkflowEngineResult {

    private String status;
    private String errorMessage;
    private Map<String, Object> finalResult;
    private List<NodeExecutionRecord> nodeRecords = new ArrayList<>();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(Map<String, Object> finalResult) {
        this.finalResult = finalResult;
    }

    public List<NodeExecutionRecord> getNodeRecords() {
        return nodeRecords;
    }

    public void setNodeRecords(List<NodeExecutionRecord> nodeRecords) {
        this.nodeRecords = nodeRecords;
    }
}
