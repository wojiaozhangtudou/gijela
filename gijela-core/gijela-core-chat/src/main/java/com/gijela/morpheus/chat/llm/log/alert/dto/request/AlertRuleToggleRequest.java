package com.gijela.morpheus.chat.llm.log.alert.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertRuleToggleRequest {

    @NotNull(message = "启停状态不能为空")
    private Boolean enabled;
}
