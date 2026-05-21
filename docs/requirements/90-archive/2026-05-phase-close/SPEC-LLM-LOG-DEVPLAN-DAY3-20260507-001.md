# SPEC-LLM-LOG-DEVPLAN-DAY3-20260507-001

> 目的：Day3 开工清单（告警规则、端到端测试、灰度上线准备）。

## 阶段总览
- **Duration**: Day3 = 1 整个工作日
- **Team**: 后端 1.5 人、前端 1 人、运维 0.5 人、QA 1 人
- **Goal**: 告警规则上线、E2E 测试通过、灰度上线预案就位
- **依赖**：Day2 产出（全链路查询、脱敏验证、前端集成）

## 后端 Team（1.5 人·天）

### 任务 B8：告警规则初期化与调度器（0.75 人·天）
**责任人**：后端告警开发  
**核心任务**：

```java
// 初期化 6 个 P0 告警规则
List<LlmAlertRule> initRules() {
    return List.of(
        LlmAlertRule.builder()
            .name("高错误率告警")
            .metricType("errorRate")
            .condition("gt")
            .threshold(new BigDecimal("5.0"))
            .windowMinutes(5)
            .severity("critical")
            .notifyChannels("wechat,dingding")
            .cooldownMinutes(10)
            .build(),
        
        LlmAlertRule.builder()
            .name("P95 延迟告警")
            .metricType("p95Latency")
            .condition("gt")
            .threshold(new BigDecimal("3000"))
            .windowMinutes(10)
            .severity("warning")
            .notifyChannels("wechat")
            .cooldownMinutes(15)
            .build(),
        
        LlmAlertRule.builder()
            .name("首 Token 超时告警")
            .metricType("firstTokenMs")
            .condition("gt")
            .threshold(new BigDecimal("3000"))
            .windowMinutes(10)
            .severity("warning")
            .notifyChannels("wechat,email")
            .cooldownMinutes(15)
            .build(),
        
        LlmAlertRule.builder()
            .name("工具调用失败告警")
            .metricType("toolErrorRate")
            .condition("gt")
            .threshold(new BigDecimal("10.0"))
            .windowMinutes(5)
            .severity("warning")
            .notifyChannels("wechat")
            .cooldownMinutes(10)
            .build(),
        
        LlmAlertRule.builder()
            .name("ES 写入失败告警")
            .metricType("esIngestFailure")
            .condition("gt")
            .threshold(new BigDecimal("1.0"))
            .windowMinutes(5)
            .severity("critical")
            .notifyChannels("wechat,dingding,email")
            .cooldownMinutes(5)
            .build(),
        
        LlmAlertRule.builder()
            .name("QPS 异常告警")
            .metricType("qps")
            .condition("lt")
            .threshold(new BigDecimal("10"))
            .windowMinutes(10)
            .severity("info")
            .notifyChannels("email")
            .cooldownMinutes(30)
            .build()
    );
}

// AlertRuleScheduler 已在 Day1 定义，Day3 需补充以下内容
@Component
public class AlertRuleScheduler {
    
    @PostConstruct
    public void initializeRules() {
        // 初期化 6 个规则（如果不存在）
        List<LlmAlertRule> existingRules = ruleMapper.selectList(null);
        if (existingRules.isEmpty()) {
            initRules().forEach(rule -> {
                rule.setTenantId("default");
                rule.setEnabled(1);
                ruleMapper.insert(rule);
            });
            log.info("Initialized {} alert rules", initRules().size());
        }
    }
    
    @Scheduled(cron = "0 */1 * * * ?")  // 每分钟触发一次（调整粒度）
    public void triggerAllRules() {
        // 同 Day1 实现
    }
}
```

**验收标准**：
- [ ] 6 个规则已初期化。
- [ ] 规则可通过 API 查询、编辑。
- [ ] 调度器正常运行。
- [ ] 告警事件正确记录到数据库。

---

### 任务 B9：通知渠道集成与测试（0.75 人·天）
**责任人**：后端告警开发  
**核心实现**：

```java
// 企业微信测试
@Component
public class WechatNotificationClient {
    
    @Value("${alert.wechat.webhook-url:}")
    private String webhookUrl;
    
    public void send(String groupId, String message) throws Exception {
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("Wechat webhook URL not configured");
            return;
        }
        
        WechatMessage msg = WechatMessage.builder()
            .msgtype("markdown")
            .markdown(WechatMessage.MarkdownContent.builder()
                .content(message)
                .build())
            .build();
        
        HttpEntity<String> entity = new HttpEntity<>(
            JsonUtil.toJson(msg),
            new HttpHeaders() {{
                setContentType(MediaType.APPLICATION_JSON);
            }}
        );
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                webhookUrl,
                entity,
                String.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new AlertNotificationException(
                    "Wechat notification failed: " + response.getBody()
                );
            }
        } catch (RestClientException e) {
            throw new AlertNotificationException("Failed to send wechat message", e);
        }
    }
}

// 钉钉测试
@Component
public class DingdingNotificationClient {
    
    @Value("${alert.dingding.webhook-url:}")
    private String webhookUrl;
    
    public void send(String robotId, String message) throws Exception {
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("Dingding webhook URL not configured");
            return;
        }
        
        DingdingMessage msg = DingdingMessage.builder()
            .msgtype("markdown")
            .markdown(DingdingMessage.MarkdownContent.builder()
                .title("LLM 告警")
                .text(message)
                .build())
            .build();
        
        // 类似企业微信的实现
    }
}

// 邮件测试
@Component
public class EmailNotificationClient {
    
    @Autowired
    private JavaMailSender mailSender;
    
    public void send(String recipient, String message) throws MessagingException {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom("alerts@gijela.com");
        mail.setTo(recipient);
        mail.setSubject("【LLM 告警】");
        mail.setText(message);
        
        mailSender.send(mail);
    }
}
```

**测试用例**：
```java
@SpringBootTest
@ActiveProfiles("test")
public class AlertNotificationTest {
    
    @Autowired
    private WechatNotificationClient wechatClient;
    
    @Autowired
    private DingdingNotificationClient dingdingClient;
    
    @Autowired
    private EmailNotificationClient emailClient;
    
    @Test
    public void testWechatNotification() throws Exception {
        String message = "【LLM 告警】高错误率触发\n错误率: 7.2%\n阈值: 5.0%\n时间: 2026-05-07 10:00:00";
        // 需要配置真实的 webhook URL，否则跳过
        if (System.getenv("WECHAT_WEBHOOK") != null) {
            wechatClient.send("group_id", message);
            // 手工验证是否收到通知
        }
    }
    
    @Test
    public void testDingdingNotification() throws Exception {
        // 类似
    }
    
    @Test
    public void testEmailNotification() throws Exception {
        // 类似
    }
}
```

**验收标准**：
- [ ] 三种通知渠道已集成。
- [ ] 通知发送成功率 >= 99%。
- [ ] 延迟 < 2 秒。
- [ ] 失败重试机制就位。

---

## QA Team（1 人·天）

### 任务 Q1：端到端测试与验收（1 人·天）
**责任人**：QA / 测试开发  
**测试场景**：

```java
@SpringBootTest
@ActiveProfiles("test")
public class LlmLogE2ETest {
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private LlmAlertRuleMapper ruleMapper;
    
    @Autowired
    private LlmAlertEventMapper eventMapper;
    
    private String baseUrl = "http://localhost:9016";
    
    /**
     * 场景 1：完整日志链路
     * - 日志写入 ES
     * - API 查询成功
     * - 结果脱敏正确
     */
    @Test
    public void testCompleteLogPipeline() {
        // 1. 写入测试日志
        Map<String, Object> logEvent = Map.ofEntries(
            Map.entry("eventType", "chat.finish"),
            Map.entry("eventTime", System.currentTimeMillis()),
            Map.entry("traceId", "test-trace-001"),
            Map.entry("sessionId", "test-session-001"),
            Map.entry("status", "SUCCESS"),
            Map.entry("latencyMs", 500L),
            Map.entry("promptPreview", "测试提示词内容"),
            Map.entry("apiKey", "sk-123456789")
        );
        
        // 2. 写入 ES（通过 Logback）
        logger.info(JsonUtil.toJson(logEvent));
        
        // 3. 等待 ES 同步
        Thread.sleep(2000);
        
        // 4. 查询
        TimeSeriesRequest request = TimeSeriesRequest.builder()
            .filter(LogQueryFilter.builder()
                .startAt(LocalDateTime.now().minusMinutes(10))
                .endAt(LocalDateTime.now())
                .build())
            .metric("qps")
            .interval("1m")
            .build();
        
        ResponseEntity<ApiResponse<TimeSeriesResponse>> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/llm-logs/query/timeseries",
            request,
            new ParameterizedTypeReference<ApiResponse<TimeSeriesResponse>>() {}
        );
        
        // 5. 验证结果
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getBuckets()).isNotEmpty();
        
        // 6. 验证脱敏
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("traceId", "test-trace-001");
        // 查询 ES，确认 apiKey 已脱敏
    }
    
    /**
     * 场景 2：告警规则触发
     * - 模拟高错误率
     * - 告警被触发
     * - 通知被发送
     */
    @Test
    public void testAlertRuleTriggering() {
        // 1. 创建告警规则
        LlmAlertRule rule = LlmAlertRule.builder()
            .name("Test Rule")
            .metricType("errorRate")
            .condition("gt")
            .threshold(new BigDecimal("5.0"))
            .windowMinutes(1)
            .enabled(1)
            .notifyChannels("wechat")
            .build();
        ruleMapper.insert(rule);
        
        // 2. 模拟高错误率日志
        for (int i = 0; i < 100; i++) {
            Map<String, Object> logEvent = Map.ofEntries(
                Map.entry("eventType", "chat.finish"),
                Map.entry("status", i < 10 ? "SUCCESS" : "FAILED"),  // 90% 失败率
                Map.entry("traceId", "test-trace-" + i)
            );
            logger.info(JsonUtil.toJson(logEvent));
        }
        
        // 3. 等待告警触发
        Thread.sleep(5000);
        
        // 4. 验证告警事件
        QueryWrapper<LlmAlertEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("rule_id", rule.getId())
            .eq("status", "open");
        List<LlmAlertEvent> events = eventMapper.selectList(wrapper);
        
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getMetricValue()).isGreaterThan(new BigDecimal("5.0"));
    }
    
    /**
     * 场景 3：查询性能
     * - P95 延迟 < 500ms
     * - P99 延迟 < 1000ms
     */
    @Test
    public void testQueryPerformance() {
        long totalTime = 0;
        int iterations = 100;
        
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            
            TimeSeriesRequest request = TimeSeriesRequest.builder()
                .filter(LogQueryFilter.builder()
                    .startAt(LocalDateTime.now().minusHours(1))
                    .endAt(LocalDateTime.now())
                    .build())
                .metric("qps")
                .interval("5m")
                .build();
            
            restTemplate.postForEntity(
                baseUrl + "/api/v1/llm-logs/query/timeseries",
                request,
                String.class
            );
            
            long duration = System.currentTimeMillis() - start;
            totalTime += duration;
        }
        
        long avgTime = totalTime / iterations;
        assertThat(avgTime).isLessThan(500);
        
        log.info("Average query time: {} ms", avgTime);
    }
}
```

**验收标准**：
- [ ] 3 个 E2E 场景全部通过。
- [ ] 查询性能达标（P95 < 500ms）。
- [ ] 脱敏验证通过。
- [ ] 告警通知成功。

---

## 运维 Team（0.5 人·天）

### 任务 O2：灰度上线准备与监控（0.5 人·天）
**责任人**：运维/SRE  
**上线前检查清单**：

```yaml
---
上线前 Go/No-Go 检查单:

基础设施:
  - [ ] ES 集群 3 副本部署完成，健康状态 green
  - [ ] ILM 策略已应用到所有索引
  - [ ] ingest pipeline 脱敏规则已验证
  - [ ] 初始索引存储容量 < 100GB

服务端:
  - [ ] 后端服务启动无错误日志
  - [ ] 数据库迁移脚本已执行（V9__llm_alert_tables.sql）
  - [ ] 6 个告警规则已初期化
  - [ ] 通知渠道（企业微信/钉钉/邮件）已配置

前端:
  - [ ] 静态资源加载正常
  - [ ] 4 个页面无 console 错误
  - [ ] 图表渲染流畅

端到端:
  - [ ] 日志写入 → 查询 → 脱敏全链路通过
  - [ ] 告警规则触发 → 通知发送通过
  - [ ] 查询性能：P95 < 500ms

数据质量:
  - [ ] traceId 串联率 >= 99%
  - [ ] 脱敏命中率 100%
  - [ ] 无数据重复或丢失

灰度策略:
  - [ ] 灰度比例：10% 用户
  - [ ] 灰度时长：24 小时
  - [ ] 回滚方案：关闭看板入口 + 清理相关 URL 路由
  - [ ] 回滚命令: kubectl delete configmap llm-observability-enabled
```

**监控告警**（基于 prometheus）：
```yaml
# prometheus/alerts.yml
groups:
  - name: llm_observability
    rules:
      - alert: EsClusterUnhealthy
        expr: |
          elasticsearch_cluster_health_status != 1
        for: 5m
        annotations:
          summary: "ES 集群不健康"
      
      - alert: LlmLogQueryLatency
        expr: |
          histogram_quantile(0.95, llm_log_query_duration_ms) > 500
        for: 10m
        annotations:
          summary: "日志查询 P95 超时"
      
      - alert: LlmAlertTriggerFailure
        expr: |
          increase(llm_alert_trigger_errors_total[5m]) > 5
        for: 5m
        annotations:
          summary: "告警规则触发失败"
```

**验收标准**：
- [ ] 所有检查清单项已确认。
- [ ] 灰度切流成功。
- [ ] 监控告警无误报。

---

## 全体 Team 同步

### 上线前 1 小时最后检查
- 后端：检查日志输出无异常
- 前端：检查加载时间、样式一致性
- 运维：确认灰度比例正确、回滚方案就位
- QA：最后执行冒烟测试

### 灰度上线执行
```bash
# 1. 更新路由配置（启用看板入口）
kubectl patch configmap llm-observability-enabled \
  --type merge -p '{"data":{"enabled":"true","grayRatio":"10"}}'

# 2. 监控 SLO
# - 查询延迟 P95
# - 错误率
# - 日志写入失败率

# 3. 收集反馈（2 小时）
# - 前端响应时间
# - 告警通知准确性

# 4. 决策
# - OK -> 扩大灰度（50%）或全量
# - NG -> 回滚（kubectl delete configmap ...）
```

---

## Day3 End-of-Day 验收清单
- [ ] 6 个告警规则已初期化并可触发
- [ ] 通知渠道三种已验证
- [ ] E2E 测试全部通过
- [ ] 灰度上线切流成功（10% 用户）
- [ ] 监控告警无误报
- [ ] 无 P0 阻塞问题
- [ ] 上线总结文档已输出
