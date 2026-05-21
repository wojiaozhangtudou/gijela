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
@TableName("llm_alert_rule")
public class LlmAlertRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("enabled")
    private Integer enabled;

    @TableField("metric_type")
    private String metricType;

    @TableField("`condition`")
    private String condition;

    @TableField("threshold")
    private BigDecimal threshold;

    @TableField("window_minutes")
    private Integer windowMinutes;

    @TableField("`group_by`")
    private String groupBy;

    @TableField("filter_json")
    private String filterJson;

    @TableField("severity")
    private String severity;

    @TableField("cooldown_minutes")
    private Integer cooldownMinutes;

    @TableField("notify_channels")
    private String notifyChannels;

    @TableField("notify_recipients")
    private String notifyRecipients;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("updated_by")
    private String updatedBy;

    @TableField("deleted")
    private Integer deleted;
}
