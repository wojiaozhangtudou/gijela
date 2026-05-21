package com.gijela.morpheus.chat.llm.log.alert.dto.request;

import lombok.Data;

@Data
public class AlertEventActionRequest {

    private String operator;

    private String comment;
}
