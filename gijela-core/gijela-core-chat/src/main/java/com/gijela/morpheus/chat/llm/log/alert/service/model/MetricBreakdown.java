package com.gijela.morpheus.chat.llm.log.alert.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricBreakdown {

    private String key;

    private BigDecimal value;
}
