package com.gijela.morpheus.chat.llm.log.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分布查询响应")
public class DistributionResponse {

    @Schema(description = "指标名称")
    private String metric;

    @Schema(description = "分布桶")
    private List<Bucket> buckets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bucket {
        private String label;
        private Double from;
        private Double to;
        private Long count;
    }
}