package com.gijela.morpheus.chatflow.vo;

import java.util.Map;

public class WorkflowDraftDetailVO {

    private String workflowId;
    private Integer version;
    private Map<String, Object> definition;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }

    public void setDefinition(Map<String, Object> definition) {
        this.definition = definition;
    }
}
