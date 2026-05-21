package com.gijela.morpheus.chat.llm.log.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分布查询请求")
public class DistributionRequest {

    @Schema(description = "过滤条件")
    private LogQueryFilter filter;

    @Schema(description = "指标名称", example = "latencyMs")
    private String metric;

    @Schema(description = "桶数量", example = "10")
    private Integer buckets;
}