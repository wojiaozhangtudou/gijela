package com.gijela.morpheus.chat.llm.log.alert.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvaluationResult {

    private Long ruleId;

    private boolean triggered;

    private BigDecimal metricValue;

    private List<MetricBreakdown> breakdown;

    private String error;
}
