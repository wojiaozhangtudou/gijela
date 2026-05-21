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
@Schema(description = "TopN 查询请求")
public class TopNRequest {

    @Schema(description = "过滤条件")
    private LogQueryFilter filter;

    @Schema(description = "排序指标", example = "latencyMs")
    private String metric;

    @Schema(description = "返回条数", example = "10")
    private Integer limit;

    @Schema(description = "排序方向", example = "desc")
    private String sortOrder;
}