package com.gijela.morpheus.chat.llm.log.alert.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleUpsertRequest {

    @NotBlank(message = "规则名称不能为空")
    private String name;

    private String description;

    private Integer enabled;

    @NotBlank(message = "指标类型不能为空")
    private String metricType;

    @NotBlank(message = "条件不能为空")
    private String condition;

    @NotNull(message = "阈值不能为空")
    @DecimalMin(value = "0", message = "阈值不能小于 0")
    private BigDecimal threshold;

    @NotNull(message = "窗口时间不能为空")
    private Integer windowMinutes;

    private String groupBy;

    private String filterJson;

    @NotBlank(message = "告警级别不能为空")
    private String severity;

    private Integer cooldownMinutes;

    @NotEmpty(message = "通知渠道不能为空")
    private List<String> notifyChannels;

    private Map<String, List<String>> notifyRecipients;
}
