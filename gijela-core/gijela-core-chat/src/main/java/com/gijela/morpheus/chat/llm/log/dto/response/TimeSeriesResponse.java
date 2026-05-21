package com.gijela.morpheus.chat.llm.log.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 时间序列查询响应（返回给前端的图表数据）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "时间序列查询响应")
public class TimeSeriesResponse {
    
    @Schema(description = "聚合指标名称")
    private String metric;
    
    @Schema(description = "时间间隔")
    private String interval;
    
    @Schema(description = "数据点列表")
    private List<TimeSeriesBucket> buckets;
    
    @Schema(description = "总行数")
    private Long totalCount;
    
    @Schema(description = "查询耗时（毫秒）")
    private Long queryTimeMs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "时间序列数据点")
    public static class TimeSeriesBucket {
        
        @Schema(description = "时间戳（毫秒）")
        private Long timestamp;
        
        @Schema(description = "时间（格式化）")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime time;
        
        @Schema(description = "指标值")
        private Double value;
        
        @Schema(description = "分组标签（如模型名称、状态）")
        private String label;
        
        @Schema(description = "补充数据（用于多指标展示）")
        private Map<String, Object> extras;
    }
}
