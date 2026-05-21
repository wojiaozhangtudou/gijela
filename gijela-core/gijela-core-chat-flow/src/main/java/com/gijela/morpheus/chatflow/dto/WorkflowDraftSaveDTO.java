package com.gijela.morpheus.chatflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class WorkflowDraftSaveDTO {

    @NotNull(message = "版本号不能为空")
    @Min(value = 1, message = "版本号必须大于0")
    private Integer version;

    @NotNull(message = "流程定义不能为空")
    private Map<String, Object> definition;

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
