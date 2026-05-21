package com.gijela.morpheus.chat.llm.log.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gijela.morpheus.chat.llm.log.alert.domain.entity.LlmAlertEvent;
import com.gijela.morpheus.chat.llm.log.alert.domain.entity.LlmAlertNotificationLog;
import com.gijela.morpheus.chat.llm.log.alert.domain.entity.LlmAlertRule;
import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertEventActionRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertEventPageRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertRuleUpsertRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.response.AlertEventVO;
import com.gijela.morpheus.chat.llm.log.alert.dto.response.AlertRuleVO;
import com.gijela.morpheus.chat.llm.log.alert.mapper.LlmAlertEventMapper;
import com.gijela.morpheus.chat.llm.log.alert.mapper.LlmAlertNotificationLogMapper;
import com.gijela.morpheus.chat.llm.log.alert.mapper.LlmAlertRuleMapper;
import com.gijela.morpheus.chat.llm.log.alert.service.AlertNotificationService;
import com.gijela.morpheus.chat.llm.log.alert.service.AlertRuleEvaluator;
import com.gijela.morpheus.chat.llm.log.alert.service.LlmAlertService;
import com.gijela.morpheus.chat.llm.log.alert.service.model.AlertEvaluationResult;
import com.gijela.morpheus.chat.llm.log.alert.service.model.MetricBreakdown;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LlmAlertServiceImpl implements LlmAlertService {

    private final LlmAlertRuleMapper ruleMapper;
    private final LlmAlertEventMapper eventMapper;
    private final LlmAlertNotificationLogMapper notificationLogMapper;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final AlertNotificationService alertNotificationService;
    private final ObjectMapper objectMapper;

    public LlmAlertServiceImpl(LlmAlertRuleMapper ruleMapper,
                               LlmAlertEventMapper eventMapper,
                               LlmAlertNotificationLogMapper notificationLogMapper,
                               AlertRuleEvaluator alertRuleEvaluator,
                               AlertNotificationService alertNotificationService,
                               ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
        this.notificationLogMapper = notificationLogMapper;
        this.alertRuleEvaluator = alertRuleEvaluator;
        this.alertNotificationService = alertNotificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AlertRuleVO> listRules(String tenantId) {
        String normalizedTenantId = normalizeTenant(tenantId);
        List<LlmAlertRule> rules = ruleMapper.selectList(new QueryWrapper<LlmAlertRule>()
                .eq("tenant_id", normalizedTenantId)
                .eq("deleted", 0)
                .orderByDesc("updated_at"));
        return rules.stream().map(this::toRuleVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRule(String tenantId, String operator, AlertRuleUpsertRequest request) {
        LlmAlertRule rule = new LlmAlertRule();
        mergeRule(rule, request, tenantId, operator, true);
        ruleMapper.insert(rule);
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateRule(Long id, String tenantId, String operator, AlertRuleUpsertRequest request) {
        LlmAlertRule rule = getRule(id, tenantId);
        mergeRule(rule, request, tenantId, operator, false);
        ruleMapper.updateById(rule);
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleRule(Long id, String tenantId, String operator, boolean enabled) {
        LlmAlertRule rule = getRule(id, tenantId);
        rule.setEnabled(enabled ? 1 : 0);
        rule.setUpdatedBy(normalizeOperator(operator));
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
    }

    @Override
    public org.springframework.data.domain.Page<AlertEventVO> pageEvents(AlertEventPageRequest request) {
        AlertEventPageRequest query = request == null ? new AlertEventPageRequest() : request;
        String tenantId = normalizeTenant(query.getTenantId());
        Page<LlmAlertEvent> page = new Page<>(normalizePageNo(query.getPageNo()), normalizePageSize(query.getPageSize()));
        QueryWrapper<LlmAlertEvent> wrapper = new QueryWrapper<LlmAlertEvent>()
                .eq("tenant_id", tenantId)
                .orderByDesc("triggered_at");
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq("status", query.getStatus());
        }
        if (query.getRuleId() != null) {
            wrapper.eq("rule_id", query.getRuleId());
        }
        Page<LlmAlertEvent> result = eventMapper.selectPage(page, wrapper);
        Map<Long, String> ruleNames = loadRuleNames(result.getRecords().stream().map(LlmAlertEvent::getRuleId).filter(Objects::nonNull).toList());
        List<AlertEventVO> content = result.getRecords().stream()
                .map(item -> toEventVO(item, ruleNames.get(item.getRuleId())))
                .toList();
        return new PageImpl<>(content, org.springframework.data.domain.PageRequest.of((int) result.getCurrent() - 1, (int) result.getSize()), result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ackEvent(Long id, String tenantId, AlertEventActionRequest request) {
        updateEvent(id, tenantId, event -> {
            event.setStatus("ack");
            event.setAckBy(normalizeOperator(request == null ? null : request.getOperator()));
            event.setAckAt(LocalDateTime.now());
            event.setAckComment(request == null ? null : request.getComment());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveEvent(Long id, String tenantId, AlertEventActionRequest request) {
        updateEvent(id, tenantId, event -> {
            event.setStatus("resolved");
            event.setResolvedBy(normalizeOperator(request == null ? null : request.getOperator()));
            event.setResolvedAt(LocalDateTime.now());
            event.setResolvedComment(request == null ? null : request.getComment());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long triggerRule(Long id, String tenantId, String operator) {
        LlmAlertRule rule = getRule(id, tenantId);
        return triggerRule(rule, operator, true);
    }

    @Override
    public void triggerEnabledRules() {
        List<LlmAlertRule> rules = ruleMapper.selectList(new QueryWrapper<LlmAlertRule>()
                .eq("enabled", 1)
                .eq("deleted", 0));
        for (LlmAlertRule rule : rules) {
            try {
                triggerRule(rule, "scheduler", false);
            } catch (Exception ex) {
                log.error("Failed to trigger alert rule {}", rule.getId(), ex);
            }
        }
    }

    private Long triggerRule(LlmAlertRule rule, String operator, boolean force) {
        AlertEvaluationResult evaluationResult = alertRuleEvaluator.evaluate(rule);
        if (!evaluationResult.isTriggered()) {
            return null;
        }
        if (!force && !shouldTrigger(rule)) {
            return null;
        }
        LlmAlertEvent event = LlmAlertEvent.builder()
                .tenantId(rule.getTenantId())
                .ruleId(rule.getId())
                .triggeredAt(LocalDateTime.now())
                .metricValue(evaluationResult.getMetricValue() == null ? BigDecimal.ZERO : evaluationResult.getMetricValue())
                .metricJson(writeBreakdown(evaluationResult.getBreakdown()))
                .message(formatMessage(rule, evaluationResult))
                .status("open")
                .alertLevel(defaultString(rule.getSeverity(), "warning"))
                .relatedLogQuery("/dashboard/llm-logs/alerts?ruleId=" + rule.getId())
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventMapper.insert(event);
        List<LlmAlertNotificationLog> notificationLogs = alertNotificationService.send(rule, event);
        for (LlmAlertNotificationLog notificationLog : notificationLogs) {
            notificationLog.setAlertEventId(event.getId());
            notificationLogMapper.insert(notificationLog);
        }
        log.info("[llm-alert] ruleId={}, operator={}, metricValue={}", rule.getId(), normalizeOperator(operator), event.getMetricValue());
        return event.getId();
    }

    private boolean shouldTrigger(LlmAlertRule rule) {
        int cooldown = rule.getCooldownMinutes() == null || rule.getCooldownMinutes() <= 0 ? 5 : rule.getCooldownMinutes();
        Long count = eventMapper.selectCount(new QueryWrapper<LlmAlertEvent>()
                .eq("rule_id", rule.getId())
                .ge("triggered_at", LocalDateTime.now().minusMinutes(cooldown)));
        return count == null || count == 0;
    }

    private void mergeRule(LlmAlertRule rule,
                           AlertRuleUpsertRequest request,
                           String tenantId,
                           String operator,
                           boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        rule.setTenantId(normalizeTenant(tenantId));
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        rule.setMetricType(request.getMetricType());
        rule.setCondition(request.getCondition());
        rule.setThreshold(request.getThreshold());
        rule.setWindowMinutes(request.getWindowMinutes());
        rule.setGroupBy(request.getGroupBy());
        rule.setFilterJson(request.getFilterJson());
        rule.setSeverity(request.getSeverity());
        rule.setCooldownMinutes(request.getCooldownMinutes() == null ? 5 : request.getCooldownMinutes());
        rule.setNotifyChannels(request.getNotifyChannels() == null ? "" : String.join(",", request.getNotifyChannels()));
        rule.setNotifyRecipients(writeRecipients(request.getNotifyRecipients()));
        rule.setVersion(rule.getVersion() == null ? 1L : rule.getVersion() + (creating ? 0 : 1));
        rule.setUpdatedBy(normalizeOperator(operator));
        rule.setUpdatedAt(now);
        rule.setDeleted(0);
        if (creating) {
            rule.setCreatedBy(normalizeOperator(operator));
            rule.setCreatedAt(now);
        }
    }

    private String writeRecipients(Map<String, List<String>> notifyRecipients) {
        try {
            return objectMapper.writeValueAsString(notifyRecipients == null ? Map.of() : notifyRecipients);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String writeBreakdown(List<MetricBreakdown> breakdown) {
        try {
            return objectMapper.writeValueAsString(breakdown == null ? List.of() : breakdown);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private AlertRuleVO toRuleVO(LlmAlertRule rule) {
        LlmAlertEvent lastEvent = eventMapper.selectOne(new QueryWrapper<LlmAlertEvent>()
                .eq("rule_id", rule.getId())
                .orderByDesc("triggered_at")
                .last("LIMIT 1"));
        Long recentCount = eventMapper.selectCount(new QueryWrapper<LlmAlertEvent>()
                .eq("rule_id", rule.getId())
                .ge("triggered_at", LocalDateTime.now().minusHours(24)));
        return AlertRuleVO.builder()
                .id(rule.getId())
                .name(rule.getName())
                .description(rule.getDescription())
                .metricType(rule.getMetricType())
                .condition(rule.getCondition())
                .threshold(rule.getThreshold())
                .windowMinutes(rule.getWindowMinutes())
                .severity(rule.getSeverity())
                .enabled(rule.getEnabled())
                .notifyChannels(splitChannels(rule.getNotifyChannels()))
                .lastTriggeredAt(lastEvent == null ? null : lastEvent.getTriggeredAt())
                .recentEventCount(recentCount == null ? 0 : recentCount.intValue())
                .build();
    }

    private AlertEventVO toEventVO(LlmAlertEvent event, String ruleName) {
        Long notificationCount = notificationLogMapper.selectCount(new QueryWrapper<LlmAlertNotificationLog>()
                .eq("alert_event_id", event.getId()));
        return AlertEventVO.builder()
                .id(event.getId())
                .ruleId(event.getRuleId())
                .ruleName(ruleName)
                .triggeredAt(event.getTriggeredAt())
                .metricValue(event.getMetricValue())
                .message(event.getMessage())
                .status(event.getStatus())
                .alertLevel(event.getAlertLevel())
                .ackBy(event.getAckBy())
                .ackAt(event.getAckAt())
                .resolvedBy(event.getResolvedBy())
                .resolvedAt(event.getResolvedAt())
                .notificationCount(notificationCount == null ? 0 : notificationCount.intValue())
                .build();
    }

    private Map<Long, String> loadRuleNames(List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Map.of();
        }
        return ruleMapper.selectBatchIds(ruleIds).stream()
                .collect(Collectors.toMap(LlmAlertRule::getId, LlmAlertRule::getName, (left, right) -> left));
    }

    private void updateEvent(Long id, String tenantId, Consumer<LlmAlertEvent> consumer) {
        LlmAlertEvent event = eventMapper.selectOne(new QueryWrapper<LlmAlertEvent>()
                .eq("id", id)
                .eq("tenant_id", normalizeTenant(tenantId))
                .last("LIMIT 1"));
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "告警事件不存在");
        }
        consumer.accept(event);
        event.setUpdatedAt(LocalDateTime.now());
        eventMapper.updateById(event);
    }

    private LlmAlertRule getRule(Long id, String tenantId) {
        LlmAlertRule rule = ruleMapper.selectOne(new QueryWrapper<LlmAlertRule>()
                .eq("id", id)
                .eq("tenant_id", normalizeTenant(tenantId))
                .eq("deleted", 0)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "告警规则不存在");
        }
        return rule;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo <= 0 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 20L : Math.min(pageSize, 100);
    }

    private String normalizeTenant(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

    private String normalizeOperator(String operator) {
        return (operator == null || operator.isBlank()) ? "system" : operator;
    }

    private List<String> splitChannels(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatMessage(LlmAlertRule rule, AlertEvaluationResult result) {
        return String.format("[%s] %s 触发告警：%s %s %s (阈值 %s)",
                defaultString(rule.getSeverity(), "warning").toUpperCase(),
                rule.getName(),
                rule.getMetricType(),
                defaultString(rule.getCondition(), "gt"),
                result.getMetricValue(),
                rule.getThreshold());
    }
}
