package com.gijela.morpheus.chat.llm.log.alert.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("llm_alert_event")
public class LlmAlertEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("rule_id")
    private Long ruleId;

    @TableField("triggered_at")
    private LocalDateTime triggeredAt;

    @TableField("metric_value")
    private BigDecimal metricValue;

    @TableField("metric_json")
    private String metricJson;

    @TableField("message")
    private String message;

    @TableField("status")
    private String status;

    @TableField("alert_level")
    private String alertLevel;

    @TableField("ack_by")
    private String ackBy;

    @TableField("ack_at")
    private LocalDateTime ackAt;

    @TableField("ack_comment")
    private String ackComment;

    @TableField("resolved_by")
    private String resolvedBy;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    @TableField("resolved_comment")
    private String resolvedComment;

    @TableField("related_log_query")
    private String relatedLogQuery;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
