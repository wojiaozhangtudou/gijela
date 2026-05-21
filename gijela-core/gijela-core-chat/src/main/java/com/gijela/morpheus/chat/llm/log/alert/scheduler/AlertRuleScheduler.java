package com.gijela.morpheus.chat.llm.log.alert.scheduler;

import com.gijela.morpheus.chat.llm.log.alert.service.LlmAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.alert.enabled", havingValue = "true", matchIfMissing = true)
public class AlertRuleScheduler {

    private final LlmAlertService llmAlertService;

    public AlertRuleScheduler(LlmAlertService llmAlertService) {
        this.llmAlertService = llmAlertService;
    }

    @Scheduled(cron = "${app.llm.alert.scheduler-cron:0 */5 * * * ?}")
    public void triggerEnabledRules() {
        log.debug("[llm-alert] scheduler trigger start");
        llmAlertService.triggerEnabledRules();
    }
}
