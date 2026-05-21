package com.gijela.morpheus.chatflow.engine;

import java.time.LocalDateTime;
import java.util.Map;

public class NodeExecutionRecord {

    private String nodeId;
    private String nodeType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;
    private Map<String, Object> inputSnapshot;
    private Map<String, Object> outputSnapshot;
    private String errorMessage;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, Object> getInputSnapshot() {
        return inputSnapshot;
    }

    public void setInputSnapshot(Map<String, Object> inputSnapshot) {
        this.inputSnapshot = inputSnapshot;
    }

    public Map<String, Object> getOutputSnapshot() {
        return outputSnapshot;
    }

    public void setOutputSnapshot(Map<String, Object> outputSnapshot) {
        this.outputSnapshot = outputSnapshot;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
