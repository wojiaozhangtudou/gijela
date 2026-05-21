package com.gijela.morpheus.chat.llm.log.alert.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chat.llm.log.alert.domain.entity.LlmAlertEvent;
import com.gijela.morpheus.chat.llm.log.alert.domain.entity.LlmAlertNotificationLog;
import com.gijela.morpheus.chat.llm.log.alert.domain.entity.LlmAlertRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AlertNotificationService {

    private final ObjectMapper objectMapper;

    public AlertNotificationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<LlmAlertNotificationLog> send(LlmAlertRule rule, LlmAlertEvent event) {
        if (rule.getNotifyChannels() == null || rule.getNotifyChannels().isBlank()) {
            return List.of();
        }
        List<LlmAlertNotificationLog> logs = new ArrayList<>();
        Map<String, List<String>> recipients = parseRecipients(rule.getNotifyRecipients());
        for (String channel : rule.getNotifyChannels().split(",")) {
            String normalizedChannel = channel == null ? "" : channel.trim();
            if (normalizedChannel.isEmpty()) {
                continue;
            }
            List<String> targets = recipients.getOrDefault(normalizedChannel, List.of());
            if (targets.isEmpty()) {
                logs.add(buildLog(normalizedChannel, "", "failed", "未配置接收人"));
                continue;
            }
            for (String target : targets) {
                log.info("[llm-alert] channel={}, target={}, eventId={}, message={}",
                        normalizedChannel, target, event.getId(), event.getMessage());
                logs.add(buildLog(normalizedChannel, target, "success", "mock-sent"));
            }
        }
        return logs;
    }

    private Map<String, List<String>> parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse alert recipients, err={}", ex.getMessage());
            return Map.of();
        }
    }

    private LlmAlertNotificationLog buildLog(String channel, String target, String status, String response) {
        return LlmAlertNotificationLog.builder()
                .channel(channel)
                .recipient(target)
                .status(status)
                .response(response)
                .sentAt(LocalDateTime.now())
                .build();
    }
}
