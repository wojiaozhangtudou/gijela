package com.gijela.morpheus.chat.llm.log.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 日志查询过滤条件（所有查询的通用过滤器）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日志查询过滤条件")
public class LogQueryFilter {
    
    @Schema(description = "开始时间", example = "2026-05-07 10:00:00")
    private LocalDateTime startAt;
    
    @Schema(description = "结束时间", example = "2026-05-07 11:00:00")
    private LocalDateTime endAt;
    
    @Schema(description = "租户 ID", example = "tenant-001")
    private String tenantId;
    
    @Schema(description = "用户 ID", example = "user-123")
    private String userId;
    
    @Schema(description = "模型路由", example = "gpt-4-turbo")
    private String modelRoute;
    
    @Schema(description = "请求状态", example = "SUCCESS")
    private String status;
    
    @Schema(description = "错误码", example = "RATE_LIMIT")
    private String errorCode;
    
    @Schema(description = "关键词搜索（提示词/错误消息）")
    private String keyword;
    
    @Schema(description = "最小延迟（毫秒）")
    private Long minLatency;
    
    @Schema(description = "最大延迟（毫秒）")
    private Long maxLatency;
    
    @Schema(description = "采样标记", example = "true")
    private Boolean sampled;
}
