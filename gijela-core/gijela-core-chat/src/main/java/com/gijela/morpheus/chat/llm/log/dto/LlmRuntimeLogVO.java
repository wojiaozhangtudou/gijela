package com.gijela.morpheus.chat.llm.log.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM 运行时日志 VO（模型编排、工具调用等）
 * 用途：汇总模型编排过程中的中间状态
 * 写入 ES：llm-runtime-* 索引
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "LLM 运行时日志")
public class LlmRuntimeLogVO {
    
    @Schema(description = "链路 ID", example = "trace-001")
    private String traceId;
    
    @Schema(description = "跨度 ID", example = "span-001")
    private String spanId;
    
    @Schema(description = "会话 ID", example = "session-001")
    private String sessionId;
    
    @Schema(description = "消息 ID", example = "msg-001")
    private String messageId;
    
    @Schema(description = "轮次 ID")
    private Integer turnId;
    
    @Schema(description = "租户 ID", example = "tenant-001")
    private String tenantId;
    
    @Schema(description = "用户 ID", example = "user-123")
    private String userId;
    
    @Schema(description = "事件类型", example = "chat.start", allowableValues = {
        "chat.start", "chat.stream", "chat.finish",
        "tool.call", "tool.execute", "tool.result",
        "knowledge.search", "knowledge.retrieve"
    })
    private String eventType;
    
    @Schema(description = "事件时间戳（毫秒）")
    private Long eventTime;
    
    @Schema(description = "事件发生时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime_dt;
    
    @Schema(description = "模型路由", example = "gpt-4-turbo")
    private String modelRoute;
    
    @Schema(description = "事件状态", example = "SUCCESS")
    private String status;
    
    @Schema(description = "错误码")
    private String errorCode;
    
    @Schema(description = "错误消息")
    private String errorMsg;
    
    @Schema(description = "当前事件耗时（毫秒）")
    private Long latencyMs;
    
    @Schema(description = "模型提供商", example = "openai")
    private String provider;
    
    @Schema(description = "模型名称", example = "gpt-4")
    private String model;
    
    @Schema(description = "本次调用 tokens")
    private Integer promptTokens;
    
    @Schema(description = "本次完成 tokens")
    private Integer completionTokens;
    
    @Schema(description = "本次总 tokens")
    private Integer totalTokens;
    
    @Schema(description = "首 token 延迟（毫秒）")
    private Long firstTokenMs;
    
    @Schema(description = "工具名称", example = "search")
    private String toolName;
    
    @Schema(description = "工具调用 ID")
    private String toolCallId;
    
    @Schema(description = "工具执行状态", example = "SUCCESS")
    private String toolStatus;
    
    @Schema(description = "工具执行耗时（毫秒）")
    private Long toolLatencyMs;
    
    @Schema(description = "工具错误码")
    private String toolErrorCode;
    
    @Schema(description = "提示词哈希")
    private String promptHash;
    
    @Schema(description = "提示词长度")
    private Integer promptLength;
    
    @Schema(description = "提示词预览（脱敏）")
    private String promptPreview;
    
    @Schema(description = "回复内容哈希")
    private String contentHash;
    
    @Schema(description = "回复内容长度")
    private Integer contentLength;

    @Schema(description = "模型完整输出文本（由流式 token 拼接后的最终内容）")
    private String outputText;
    
    @Schema(description = "流式开始标记")
    private Boolean streamStart;
    
    @Schema(description = "流式完成标记")
    private Boolean streamDone;
    
    @Schema(description = "是否采样")
    private Boolean sampled;
    
    @Schema(description = "环境标记", example = "prod")
    private String env;
    
    @Schema(description = "日志来源", example = "orchestrator")
    private String source;
}
