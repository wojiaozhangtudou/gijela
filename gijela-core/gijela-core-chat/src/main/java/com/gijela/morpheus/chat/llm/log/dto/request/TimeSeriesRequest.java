package com.gijela.morpheus.chat.llm.log.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时间序列查询请求（用于折线图、趋势分析）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "时间序列查询请求")
public class TimeSeriesRequest {
    
    @Schema(description = "日志过滤条件")
    private LogQueryFilter filter;
    
    @Schema(description = "聚合指标", example = "qps", allowableValues = {
        "qps", "errorRate", "avgLatency", "p95Latency", "p99Latency", 
        "avgFirstToken", "avgTokens", "totalCost"
    })
    private String metric;
    
    @Schema(description = "时间间隔", example = "1m", allowableValues = {"1m", "5m", "15m", "1h", "1d"})
    private String interval;
    
    @Schema(description = "是否按模型路由分组", example = "false")
    private Boolean groupByModel;
    
    @Schema(description = "是否按状态分组", example = "false")
    private Boolean groupByStatus;
}
