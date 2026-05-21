package com.gijela.morpheus.chat.llm.log.alert.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventVO {

    private Long id;

    private Long ruleId;

    private String ruleName;

    private LocalDateTime triggeredAt;

    private BigDecimal metricValue;

    private String message;

    private String status;

    private String alertLevel;

    private String ackBy;

    private LocalDateTime ackAt;

    private String resolvedBy;

    private LocalDateTime resolvedAt;

    private Integer notificationCount;
}
