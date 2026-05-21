package com.gijela.morpheus.chatflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class WorkflowCreateDTO {

    @NotBlank(message = "流程编码不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]{2,63}$", message = "流程编码格式不正确")
    private String code;

    @NotBlank(message = "流程名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "应用类型不能为空")
    private String appType;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

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

    public String getAppType() {
        return appType;
    }

    public void setAppType(String appType) {
        this.appType = appType;
    }
}
