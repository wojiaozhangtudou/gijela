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
@Schema(description = "日志导出响应")
public class ExportLogsResponse {

    private String downloadUrl;
    private String fileName;
}