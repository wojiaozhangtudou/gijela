# SPEC-LLM-LOG-ALERT-ENGINE-20260507-001

> 目的：给告警规则引擎提供完整实现方案（包括规则定义、触发逻辑、通知渠道）。

## 1. 告警规则模型

### 1.1 数据库 DDL

```sql
CREATE TABLE `llm_alert_rule` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default',
  `name` VARCHAR(255) NOT NULL,
  `description` TEXT,
  `enabled` TINYINT DEFAULT 1,
  
  -- 规则定义
  `metric_type` VARCHAR(64) NOT NULL COMMENT '指标类型：qps/errorRate/p95Latency/firstTokenMs/toolErrorRate/esIngestFailure',
  `condition` VARCHAR(16) NOT NULL COMMENT '比较条件：gt/lt/eq/gte/lte',
  `threshold` DECIMAL(10, 2) NOT NULL COMMENT '阈值',
  `window_minutes` INT NOT NULL DEFAULT 5 COMMENT '时间窗口（分钟）',
  
  -- 聚合维度
  `group_by` VARCHAR(255) COMMENT '分组维度：modelRoute,provider,toolName（逗号分隔）',
  `filter_json` TEXT COMMENT '固定过滤条件：{status: [SUCCESS], env: [prod]}',
  
  -- 告警配置
  `severity` VARCHAR(16) NOT NULL COMMENT '严重级别：info/warning/critical',
  `cooldown_minutes` INT DEFAULT 5 COMMENT '告警冷却期（避免重复告警）',
  `notify_channels` VARCHAR(255) NOT NULL COMMENT '通知渠道：wechat,dingding,email（逗号分隔）',
  `notify_recipients` TEXT NOT NULL COMMENT 'JSON：{wechat: [@group_id], dingding: [@robot_id], email: [user@xxx.com]}',
  
  -- 版本管理
  `version` BIGINT DEFAULT 1,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `created_by` VARCHAR(64),
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` VARCHAR(64),
  `deleted` TINYINT DEFAULT 0,
  
  UNIQUE KEY `uk_rule_name` (`tenant_id`, `name`),
  KEY `idx_rule_enabled_metric` (`tenant_id`, `enabled`, `metric_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `llm_alert_event` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` VARCHAR(64) NOT NULL,
  `rule_id` BIGINT NOT NULL,
  
  -- 触发信息
  `triggered_at` DATETIME NOT NULL COMMENT '告警触发时间',
  `metric_value` DECIMAL(10, 4) NOT NULL COMMENT '指标实际值',
  `metric_json` TEXT COMMENT '聚合细节：{modelRoute: default, value: 123}',
  `message` TEXT COMMENT '告警消息',
  
  -- 状态管理
  `status` VARCHAR(16) NOT NULL COMMENT 'open/ack/resolved',
  `alert_level` VARCHAR(16) NOT NULL COMMENT '告警等级：info/warning/critical',
  
  -- 处理信息
  `ack_by` VARCHAR(64),
  `ack_at` DATETIME,
  `ack_comment` TEXT,
  `resolved_by` VARCHAR(64),
  `resolved_at` DATETIME,
  `resolved_comment` TEXT,
  
  -- 关联
  `related_log_query` VARCHAR(255) COMMENT '关联日志查询链接',
  `version` BIGINT DEFAULT 1,
  
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  KEY `idx_alert_rule_status` (`rule_id`, `status`),
  KEY `idx_alert_tenant_time` (`tenant_id`, `triggered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `llm_alert_notification_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `alert_event_id` BIGINT NOT NULL,
  `channel` VARCHAR(32) NOT NULL COMMENT 'wechat/dingding/email',
  `recipient` VARCHAR(255),
  `sent_at` DATETIME,
  `status` VARCHAR(16) COMMENT 'success/failed',
  `response` TEXT COMMENT '返回值',
  KEY `idx_notification_alert` (`alert_event_id`, `channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 1.2 Java 实体与 VO

```java
// domain/entity/LlmAlertRule.java
@Data
@TableName("llm_alert_rule")
public class LlmAlertRule extends BaseEntity {
    private String tenantId;
    private String name;
    private String description;
    private Integer enabled;
    
    private String metricType;  // qps/errorRate/p95Latency/...
    private String condition;   // gt/lt/eq/...
    private BigDecimal threshold;
    private Integer windowMinutes;
    
    private String groupBy;     // 分组维度
    private String filterJson;  // 过滤条件
    
    private String severity;    // info/warning/critical
    private Integer cooldownMinutes;
    private String notifyChannels;   // wechat,dingding,email
    private String notifyRecipients; // JSON
}

// domain/vo/AlertRuleVO.java
@Data
@Builder
public class AlertRuleVO {
    private Long id;
    private String name;
    private String metricType;
    private String condition;
    private BigDecimal threshold;
    private Integer windowMinutes;
    private String severity;
    private Integer enabled;
    private List<String> notifyChannels;
    private LocalDateTime lastTriggeredAt;
    private Integer recentEventCount;  // 最近 24h 触发次数
}

// domain/vo/AlertEventVO.java
@Data
@Builder
public class AlertEventVO {
    private Long id;
    private Long ruleId;
    private String ruleName;
    private LocalDateTime triggeredAt;
    private BigDecimal metricValue;
    private String message;
    private String status;      // open/ack/resolved
    private String alertLevel;
    private String ackBy;
    private LocalDateTime ackAt;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
}
```

## 2. 规则引擎核心逻辑

### 2.1 告警规则评估器

```java
@Component
public class AlertRuleEvaluator {
    
    private static final Map<String, MetricCalculator> METRICS = Map.ofEntries(
        Map.entry("qps", new QpsMetricCalculator()),
        Map.entry("errorRate", new ErrorRateMetricCalculator()),
        Map.entry("p95Latency", new P95LatencyCalculator()),
        Map.entry("firstTokenMs", new FirstTokenMsCalculator()),
        Map.entry("toolErrorRate", new ToolErrorRateCalculator()),
        Map.entry("esIngestFailure", new EsIngestFailureCalculator())
    );
    
    /**
     * 评估单个规则
     */
    public AlertEvaluationResult evaluate(LlmAlertRule rule) {
        try {
            // 1. 从 ES 查询指标数据
            MetricCalculator calculator = METRICS.get(rule.getMetricType());
            if (calculator == null) {
                throw new IllegalArgumentException("Unknown metric: " + rule.getMetricType());
            }
            
            // 2. 构造查询过滤
            LogQueryFilter filter = buildFilter(rule);
            
            // 3. 计算指标值
            MetricResult result = calculator.calculate(filter);
            
            // 4. 与阈值比较
            boolean triggered = compareMetric(
                result.getValue(),
                rule.getCondition(),
                rule.getThreshold()
            );
            
            return AlertEvaluationResult.builder()
                .ruleId(rule.getId())
                .triggered(triggered)
                .metricValue(result.getValue())
                .breakdown(result.getBreakdown())  // 分组细节
                .build();
        } catch (Exception e) {
            log.error("Failed to evaluate rule {}", rule.getId(), e);
            return AlertEvaluationResult.builder()
                .ruleId(rule.getId())
                .triggered(false)
                .error(e.getMessage())
                .build();
        }
    }
    
    private LogQueryFilter buildFilter(LlmAlertRule rule) {
        LogQueryFilter filter = new LogQueryFilter();
        filter.setStartAt(LocalDateTime.now().minusMinutes(rule.getWindowMinutes()));
        filter.setEndAt(LocalDateTime.now());
        
        // 应用固定过滤条件
        if (StringUtils.hasText(rule.getFilterJson())) {
            Map<String, Object> fixed = JsonUtil.parseMap(rule.getFilterJson());
            // 应用到 filter...
        }
        
        return filter;
    }
    
    private boolean compareMetric(BigDecimal value, String condition, BigDecimal threshold) {
        int cmp = value.compareTo(threshold);
        return switch (condition) {
            case "gt" -> cmp > 0;
            case "lt" -> cmp < 0;
            case "eq" -> cmp == 0;
            case "gte" -> cmp >= 0;
            case "lte" -> cmp <= 0;
            default -> false;
        };
    }
}

// 指标计算接口
public interface MetricCalculator {
    MetricResult calculate(LogQueryFilter filter);
    
    @Data
    @Builder
    class MetricResult {
        private BigDecimal value;  // 聚合值
        private List<MetricDetail> breakdown;  // 分组细节
    }
    
    @Data
    class MetricDetail {
        private String groupKey;
        private BigDecimal value;
    }
}

// 示例：错误率计算器
@Component
public class ErrorRateMetricCalculator implements MetricCalculator {
    
    @Autowired
    private LlmLogQueryService logQueryService;
    
    @Override
    public MetricResult calculate(LogQueryFilter filter) {
        // 查询总数 + 错误数
        Long total = logQueryService.countRecords(filter);
        
        LogQueryFilter errorFilter = new LogQueryFilter();
        BeanUtils.copyProperties(filter, errorFilter);
        errorFilter.setStatus(List.of("FAILED"));
        Long errors = logQueryService.countRecords(errorFilter);
        
        BigDecimal errorRate = total > 0 
            ? BigDecimal.valueOf(errors * 100.0 / total)
            : BigDecimal.ZERO;
        
        return MetricResult.builder()
            .value(errorRate)
            .breakdown(List.of())
            .build();
    }
}
```

### 2.2 告警触发器

```java
@Component
public class AlertRuleScheduler {
    
    @Autowired
    private LlmAlertRuleMapper ruleMapper;
    
    @Autowired
    private AlertRuleEvaluator evaluator;
    
    @Autowired
    private AlertEventService eventService;
    
    @Autowired
    private AlertNotificationService notificationService;
    
    /**
     * 定时触发规则评估（每 5 分钟）
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void triggerAllRules() {
        try {
            // 1. 查询所有启用的规则
            List<LlmAlertRule> enabledRules = ruleMapper.selectList(
                new QueryWrapper<LlmAlertRule>()
                    .eq("enabled", 1)
                    .eq("deleted", 0)
            );
            
            for (LlmAlertRule rule : enabledRules) {
                triggerRule(rule);
            }
        } catch (Exception e) {
            log.error("Failed to trigger alert rules", e);
        }
    }
    
    private void triggerRule(LlmAlertRule rule) {
        // 1. 评估规则
        AlertEvaluationResult result = evaluator.evaluate(rule);
        
        if (!result.isTriggered()) {
            log.debug("Rule {} not triggered", rule.getId());
            return;
        }
        
        // 2. 检查冷却期（避免重复告警）
        if (!eventService.shouldTrigger(rule)) {
            log.debug("Rule {} in cooldown", rule.getId());
            return;
        }
        
        // 3. 创建告警事件
        LlmAlertEvent event = LlmAlertEvent.builder()
            .ruleId(rule.getId())
            .triggeredAt(LocalDateTime.now())
            .metricValue(result.getMetricValue())
            .metricJson(JsonUtil.toJson(result.getBreakdown()))
            .message(formatMessage(rule, result))
            .status("open")
            .alertLevel(rule.getSeverity())
            .tenantId(rule.getTenantId())
            .build();
        
        Long eventId = eventService.createEvent(event);
        
        // 4. 发送通知
        sendNotifications(rule, event, eventId);
        
        log.info("Alert triggered for rule {}: {}", rule.getId(), event.getMessage());
    }
    
    private void sendNotifications(LlmAlertRule rule, LlmAlertEvent event, Long eventId) {
        String[] channels = rule.getNotifyChannels().split(",");
        Map<String, Object> recipients = JsonUtil.parseMap(rule.getNotifyRecipients());
        
        for (String channel : channels) {
            try {
                List<String> targets = (List<String>) recipients.getOrDefault(channel, List.of());
                for (String target : targets) {
                    notificationService.send(channel, target, event);
                    
                    // 记录发送日志
                    eventService.logNotification(eventId, channel, target, "success", null);
                }
            } catch (Exception e) {
                log.error("Failed to send notification via {}", channel, e);
                eventService.logNotification(eventId, channel, "", "failed", e.getMessage());
            }
        }
    }
    
    private String formatMessage(LlmAlertRule rule, AlertEvaluationResult result) {
        return String.format(
            "[%s] %s 触发告警：%s %s %.2f (阈值: %.2f)",
            rule.getSeverity().toUpperCase(),
            rule.getName(),
            rule.getMetricType(),
            rule.getCondition(),
            result.getMetricValue(),
            rule.getThreshold()
        );
    }
}
```

## 3. 通知渠道集成

### 3.1 通知服务

```java
@Component
public class AlertNotificationService {
    
    @Autowired
    private WechatNotificationClient wechatClient;
    
    @Autowired
    private DingdingNotificationClient dingdingClient;
    
    @Autowired
    private EmailNotificationClient emailClient;
    
    public void send(String channel, String target, LlmAlertEvent event) {
        String message = buildAlertMessage(event);
        
        switch (channel) {
            case "wechat" -> wechatClient.send(target, message);
            case "dingding" -> dingdingClient.send(target, message);
            case "email" -> emailClient.send(target, message);
            default -> throw new IllegalArgumentException("Unknown channel: " + channel);
        }
    }
    
    private String buildAlertMessage(LlmAlertEvent event) {
        return String.format(
            "【LLM 告警】\n" +
            "告警时间：%s\n" +
            "告警级别：%s\n" +
            "告警内容：%s\n" +
            "操作链接：/llm-observability/alerts?eventId=%d",
            event.getTriggeredAt(),
            event.getAlertLevel(),
            event.getMessage(),
            event.getId()
        );
    }
}
```

### 3.2 企业微信客户端示例

```java
@Component
@Configuration
public class WechatNotificationClient {
    
    @Value("${alert.wechat.webhook-url}")
    private String webhookUrl;
    
    public void send(String groupId, String message) {
        try {
            WechatMessage wechatMsg = WechatMessage.builder()
                .msgtype("text")
                .text(WechatMessage.TextContent.builder()
                    .content(message)
                    .mentionedList(List.of())
                    .build())
                .build();
            
            HttpEntity<String> entity = new HttpEntity<>(
                JsonUtil.toJson(wechatMsg),
                new HttpHeaders() {{
                    setContentType(MediaType.APPLICATION_JSON);
                }}
            );
            
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForEntity(webhookUrl, entity, String.class);
        } catch (Exception e) {
            throw new AlertNotificationException("Failed to send wechat message", e);
        }
    }
}
```

## 4. 服务实现

```java
@Service
public class LlmAlertServiceImpl implements LlmAlertService {
    
    @Autowired
    private LlmAlertRuleMapper ruleMapper;
    
    @Autowired
    private LlmAlertEventMapper eventMapper;
    
    @Override
    public Long createRule(AlertRuleRequest request) {
        LlmAlertRule rule = LlmAlertRule.builder()
            .tenantId(getCurrentTenantId())
            .name(request.getName())
            .metricType(request.getMetricType())
            .condition(request.getCondition())
            .threshold(request.getThreshold())
            .windowMinutes(request.getWindowMinutes())
            .enabled(1)
            .notifyChannels(String.join(",", request.getNotifyChannels()))
            .notifyRecipients(JsonUtil.toJson(request.getNotifyRecipients()))
            .severity(request.getSeverity())
            .build();
        
        ruleMapper.insert(rule);
        return rule.getId();
    }
    
    @Override
    public PageResponse<AlertEventVO> listAlertEvents(AlertEventQuery query) {
        QueryWrapper<LlmAlertEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", getCurrentTenantId());
        
        if (query.getStatus() != null) {
            wrapper.eq("status", query.getStatus());
        }
        if (query.getRuleId() != null) {
            wrapper.eq("rule_id", query.getRuleId());
        }
        
        wrapper.orderByDesc("triggered_at");
        
        IPage<LlmAlertEvent> page = eventMapper.selectPage(
            new Page<>(query.getPageNo(), query.getPageSize()),
            wrapper
        );
        
        List<AlertEventVO> vos = page.getRecords().stream()
            .map(this::toVO)
            .collect(Collectors.toList());
        
        return PageResponse.of(page.getTotal(), page.getPages(), vos);
    }
}
```

## 5. 验收清单
- [ ] 至少 6 个规则已定义（QPS、错误率、P95、首 token、工具失败、ES 写入）。
- [ ] 规则引擎定时触发成功率 >= 99%。
- [ ] 告警通知延迟 < 1 分钟。
- [ ] 脱敏检查：告警消息不含敏感字段。
- [ ] 冷却期有效：不会频繁重复告警。
- [ ] 通知渠道测试通过（企业微信/钉钉/邮件）。
