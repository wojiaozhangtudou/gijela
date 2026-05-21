package com.gijela.morpheus.chat.llm.log.alert.dto.request;

import lombok.Data;

@Data
public class AlertEventPageRequest {

    private Integer pageNo = 1;

    private Integer pageSize = 20;

    private String tenantId;

    private String status;

    private Long ruleId;
}
