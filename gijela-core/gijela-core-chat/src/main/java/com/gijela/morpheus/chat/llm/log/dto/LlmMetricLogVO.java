package com.gijela.morpheus.chat.llm.log.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM 指标日志 VO（聚合级别）
 * 用途：5 分钟/1 小时维度的关键指标汇总
 * 写入 ES：llm-metric-* 索引
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "LLM 指标日志")
public class LlmMetricLogVO {
    
    @Schema(description = "链路 ID", example = "metric-001")
    private String traceId;
    
    @Schema(description = "租户 ID", example = "tenant-001")
    private String tenantId;
    
    @Schema(description = "模型路由", example = "gpt-4-turbo")
    private String modelRoute;
    
    @Schema(description = "指标时段（如 2026-05-07 10:00:00）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime metricTime;
    
    @Schema(description = "事件时间戳（毫秒）")
    private Long eventTime;
    
    @Schema(description = "时间粒度", example = "5m", allowableValues = {"5m", "1h"})
    private String interval;
    
    @Schema(description = "该时段内的请求数")
    private Integer qps;
    
    @Schema(description = "该时段内的错误数")
    private Integer errorCount;
    
    @Schema(description = "错误率（百分比）")
    private Double errorRate;
    
    @Schema(description = "平均延迟（毫秒）")
    private Long avgLatency;
    
    @Schema(description = "P50 延迟")
    private Long p50Latency;
    
    @Schema(description = "P95 延迟")
    private Long p95Latency;
    
    @Schema(description = "P99 延迟")
    private Long p99Latency;
    
    @Schema(description = "平均首 token 延迟")
    private Long avgFirstTokenLatency;
    
    @Schema(description = "平均 tokens 消耗")
    private Long avgTotalTokens;
    
    @Schema(description = "总成本（微美元）")
    private Long totalCostMicros;
    
    @Schema(description = "工具调用失败率")
    private Double toolErrorRate;
    
    @Schema(description = "知识库查询成功率")
    private Double knowledgeSuccessRate;
    
    @Schema(description = "流式响应比例")
    private Double streamingRatio;
    
    @Schema(description = "采样的请求数")
    private Integer sampledCount;
    
    @Schema(description = "环境标记", example = "prod")
    private String env;
    
    @Schema(description = "日志来源", example = "metric-aggregator")
    private String source;
}
