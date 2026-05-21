package com.gijela.morpheus.chat.llm.log.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM 审计日志 VO（合规性记录）
 * 用途：记录关键操作、模型变更、权限检查等
 * 写入 ES：llm-audit-* 索引（180 天保留）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "LLM 审计日志")
public class LlmAuditLogVO {
    
    @Schema(description = "链路 ID", example = "trace-001")
    private String traceId;
    
    @Schema(description = "会话 ID", example = "session-001")
    private String sessionId;
    
    @Schema(description = "租户 ID", example = "tenant-001")
    private String tenantId;
    
    @Schema(description = "用户 ID", example = "user-123")
    private String userId;
    
    @Schema(description = "操作人身份", example = "user|service|system")
    private String operatorType;
    
    @Schema(description = "操作人 ID")
    private String operatorId;
    
    @Schema(description = "操作类型", example = "MODEL_CHANGE", allowableValues = {
        "MODEL_CHANGE", "PERMISSION_CHECK", "DATA_ACCESS", 
        "CONFIG_UPDATE", "ALERT_TRIGGERED", "FLOW_EXECUTED"
    })
    private String operationType;
    
    @Schema(description = "操作对象", example = "gpt-4")
    private String operationTarget;
    
    @Schema(description = "操作结果", example = "SUCCESS")
    private String operationResult;
    
    @Schema(description = "操作详情（脱敏后）")
    private String operationDetail;
    
    @Schema(description = "审计事件时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;
    
    @Schema(description = "事件时间戳（毫秒）")
    private Long eventTime;
    
    @Schema(description = "模型路由", example = "gpt-4-turbo")
    private String modelRoute;
    
    @Schema(description = "关联的错误码（如有）")
    private String errorCode;
    
    @Schema(description = "相关备注")
    private String remarks;
    
    @Schema(description = "环境标记", example = "prod")
    private String env;
    
    @Schema(description = "日志来源", example = "audit-service")
    private String source;
}
