package com.gijela.morpheus.chat.llm.log.alert.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleVO {

    private Long id;

    private String name;

    private String description;

    private String metricType;

    private String condition;

    private BigDecimal threshold;

    private Integer windowMinutes;

    private String severity;

    private Integer enabled;

    private List<String> notifyChannels;

    private LocalDateTime lastTriggeredAt;

    private Integer recentEventCount;
}
