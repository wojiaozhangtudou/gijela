package com.gijela.morpheus.chatflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public class ChatflowSessionSaveDTO {

    @NotBlank(message = "会话标题不能为空")
    @Size(max = 128, message = "会话标题不能超过128字符")
    private String title;

    @NotBlank(message = "workflowId不能为空")
    @Size(max = 64, message = "workflowId长度不能超过64")
    private String workflowId;

    private Map<String, Object> workflowInputs;

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

    public Map<String, Object> getWorkflowInputs() {
        return workflowInputs;
    }

    public void setWorkflowInputs(Map<String, Object> workflowInputs) {
        this.workflowInputs = workflowInputs;
    }
}
