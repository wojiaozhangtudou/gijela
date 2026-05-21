package com.gijela.morpheus.chat.llm.log.alert.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("llm_alert_notification_log")
public class LlmAlertNotificationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("alert_event_id")
    private Long alertEventId;

    @TableField("channel")
    private String channel;

    @TableField("recipient")
    private String recipient;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("status")
    private String status;

    @TableField("response")
    private String response;
}
