package com.gijela.morpheus.chatflow.vo;

public class WorkflowCreateVO {

    private String workflowId;
    private Integer draftVersion;
    private String status;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public Integer getDraftVersion() {
        return draftVersion;
    }

    public void setDraftVersion(Integer draftVersion) {
        this.draftVersion = draftVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
