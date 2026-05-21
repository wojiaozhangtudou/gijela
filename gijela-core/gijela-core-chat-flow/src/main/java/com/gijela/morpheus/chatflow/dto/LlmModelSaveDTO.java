package com.gijela.morpheus.chatflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LlmModelSaveDTO {

    @NotBlank(message = "模型键不能为空")
    @Size(max = 64, message = "模型键长度不能超过64")
    private String modelKey;

    @NotBlank(message = "展示名称不能为空")
    @Size(max = 128, message = "展示名称长度不能超过128")
    private String displayName;

    @NotBlank(message = "供应商不能为空")
    @Size(max = 32, message = "供应商长度不能超过32")
    private String provider;

    @NotBlank(message = "目标模型不能为空")
    @Size(max = 128, message = "目标模型长度不能超过128")
    private String targetModel;

    @NotBlank(message = "模型URL不能为空")
    @Size(max = 255, message = "模型URL长度不能超过255")
    private String baseUrl;

    @NotBlank(message = "API Key不能为空")
    @Size(max = 255, message = "API Key长度不能超过255")
    private String apiKey;

    @NotNull(message = "启用状态不能为空")
    private Integer enabled;

    @Min(value = 0, message = "默认温度不能小于0")
    @Max(value = 2, message = "默认温度不能大于2")
    private Double defaultTemperature;

    @Min(value = 1, message = "默认最大令牌数必须大于0")
    private Integer defaultMaxTokens;

    @Size(max = 512, message = "备注长度不能超过512")
    private String remark;

    public String getModelKey() {
        return modelKey;
    }

    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getTargetModel() {
        return targetModel;
    }

    public void setTargetModel(String targetModel) {
        this.targetModel = targetModel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Double getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(Double defaultTemperature) {
        this.defaultTemperature = defaultTemperature;
    }

    public Integer getDefaultMaxTokens() {
        return defaultMaxTokens;
    }

    public void setDefaultMaxTokens(Integer defaultMaxTokens) {
        this.defaultMaxTokens = defaultMaxTokens;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
