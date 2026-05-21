package com.gijela.morpheus.chat.llm.log.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM 访问日志 VO（每个请求）
 * 用途：汇总一次用户请求的基本信息
 * 写入 ES：llm-access-* 索引
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "LLM 访问日志")
public class LlmAccessLogVO {
    
    @Schema(description = "链路 ID", example = "trace-001")
    private String traceId;
    
    @Schema(description = "会话 ID", example = "session-001")
    private String sessionId;
    
    @Schema(description = "租户 ID", example = "tenant-001")
    private String tenantId;
    
    @Schema(description = "应用代码", example = "chat-app")
    private String appCode;
    
    @Schema(description = "用户 ID", example = "user-123")
    private String userId;
    
    @Schema(description = "事件时间戳（毫秒）")
    private Long eventTime;
    
    @Schema(description = "事件发生时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime_dt;
    
    @Schema(description = "模型路由（用于标记应用了哪个模型配置）", example = "gpt-4-turbo")
    private String modelRoute;
    
    @Schema(description = "请求状态", example = "SUCCESS", allowableValues = {"SUCCESS", "FAILED", "TIMEOUT"})
    private String status;
    
    @Schema(description = "错误码（仅当 status=FAILED 时有值）", example = "RATE_LIMIT")
    private String errorCode;
    
    @Schema(description = "错误类型", example = "ServiceError")
    private String errorType;
    
    @Schema(description = "错误消息")
    private String errorMsg;
    
    @Schema(description = "是否可重试", example = "true")
    private Boolean retryable;
    
    @Schema(description = "请求总耗时（毫秒）")
    private Long latencyMs;
    
    @Schema(description = "LLM 提供商", example = "openai")
    private String provider;
    
    @Schema(description = "模型名称", example = "gpt-4")
    private String model;
    
    @Schema(description = "LLM 端点地址")
    private String endpoint;
    
    @Schema(description = "提示词 tokens 数")
    private Integer promptTokens;
    
    @Schema(description = "完成 tokens 数")
    private Integer completionTokens;
    
    @Schema(description = "总 tokens 数")
    private Integer totalTokens;
    
    @Schema(description = "成本（微美元）")
    private Long costMicros;
    
    @Schema(description = "首个 token 延迟（毫秒）")
    private Long firstTokenMs;
    
    @Schema(description = "完成原因", example = "stop", allowableValues = {"stop", "length", "content_filter"})
    private String finishReason;
    
    @Schema(description = "提示词哈希值")
    private String promptHash;
    
    @Schema(description = "提示词长度")
    private Integer promptLength;
    
    @Schema(description = "提示词预览（脱敏后）")
    private String promptPreview;
    
    @Schema(description = "回复内容哈希值")
    private String contentHash;
    
    @Schema(description = "回复内容长度")
    private Integer contentLength;

    @Schema(description = "模型完整输出文本（由流式 token 拼接后的最终内容）")
    private String outputText;
    
    @Schema(description = "是否采样")
    private Boolean sampled;
    
    @Schema(description = "环境标记", example = "prod", allowableValues = {"dev", "staging", "prod"})
    private String env;
    
    @Schema(description = "日志来源", example = "lm-client")
    private String source;
}
