package com.gijela.morpheus.chatflow.vo;

public class WorkflowDetailVO extends WorkflowSummaryVO {

    private String description;
    private WorkflowDraftDetailVO draft;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkflowDraftDetailVO getDraft() {
        return draft;
    }

    public void setDraft(WorkflowDraftDetailVO draft) {
        this.draft = draft;
    }
}
