package com.gijela.morpheus.chatflow.vo;

import java.util.List;
import java.util.Map;

public class WorkflowRunDetailVO {

    private String runId;
    private String workflowId;
    private Integer workflowVersion;
    private String runType;
    private String status;
    private String startedAt;
    private String endedAt;
    private Long durationMs;
    private Map<String, Object> finalResult;
    private List<NodeTraceVO> nodes;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public Integer getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(Integer workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public String getRunType() {
        return runType;
    }

    public void setRunType(String runType) {
        this.runType = runType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(String endedAt) {
        this.endedAt = endedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, Object> getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(Map<String, Object> finalResult) {
        this.finalResult = finalResult;
    }

    public List<NodeTraceVO> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeTraceVO> nodes) {
        this.nodes = nodes;
    }

    public static class NodeTraceVO {
        private String nodeId;
        private String nodeType;
        private String status;
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
}
