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
@Schema(description = "日志导出请求")
public class ExportLogsRequest {

    @Schema(description = "过滤条件")
    private LogQueryFilter filter;

    @Schema(description = "导出格式", example = "csv")
    private String format;

    @Schema(description = "导出上限", example = "1000")
    private Integer limit;
}