package com.gijela.morpheus.chatflow.vo;

import java.util.Map;

public class ChatflowSessionVO {

    private String sessionId;
    private String title;
    private String workflowId;
    private String status;
    private Map<String, Object> workflowInputs;
    private String createdAt;
    private String updatedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getWorkflowInputs() {
        return workflowInputs;
    }

    public void setWorkflowInputs(Map<String, Object> workflowInputs) {
        this.workflowInputs = workflowInputs;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
