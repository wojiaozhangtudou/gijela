package com.gijela.morpheus.chatflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class WorkflowDebugRunDTO {

    @NotNull(message = "版本号不能为空")
    @Min(value = 1, message = "版本号必须大于0")
    private Integer version;

    private Map<String, Object> inputs;

    private String triggerBy;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    public String getTriggerBy() {
        return triggerBy;
    }

    public void setTriggerBy(String triggerBy) {
        this.triggerBy = triggerBy;
    }
}
