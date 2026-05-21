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
@Schema(description = "日志记录项")
public class LlmLogRecordVO {

    private String traceId;
    private String sessionId;
    private String eventType;
    private String logType;
    private String modelRoute;
    private String status;
    private Long latencyMs;
    private Integer totalTokens;
    private String errorCode;
    private String outputText;
    private Long eventTime;
}