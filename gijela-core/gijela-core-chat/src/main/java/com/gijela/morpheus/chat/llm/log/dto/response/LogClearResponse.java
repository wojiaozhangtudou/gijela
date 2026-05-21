package com.gijela.morpheus.chat.llm.log.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日志清理结果")
public class LogClearResponse {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "删除条数")
    private Long deletedCount;

    @Schema(description = "结果说明")
    private String message;
}
