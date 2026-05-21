package com.gijela.morpheus.chatflow.dto;

import jakarta.validation.constraints.NotBlank;

public class WorkflowUpdateDTO {

    @NotBlank(message = "流程名称不能为空")
    private String name;

    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
