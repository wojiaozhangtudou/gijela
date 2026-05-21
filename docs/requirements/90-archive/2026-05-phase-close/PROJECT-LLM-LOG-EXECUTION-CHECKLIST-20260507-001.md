# PROJECT-LLM-LOG-EXECUTION-CHECKLIST-20260507-001

> 目的：LLM 日志输出项目总体执行清单（从规划到上线的完整检查表）。

---

## 一、前置环境准备（Day0 - 开工前）

### 运维准备工作（人工处理）

- [ ] ES 集群已部署并健康（3 节点+，7.17+）
  - 已创建 4 个索引模板（runtime/access/audit/metric）
  - 已部署 ILM 策略
  - 已部署 ingest pipeline（脱敏规则）
  - 磁盘空间 >= 100GB
  
- [ ] Redis 集群已部署（version >= 6.0）
  
- [ ] MySQL 数据库已初期化（version >= 8.0）
  - 已执行数据库迁移脚本（V8/V9）
  - 已创建 llm_alert_rule / llm_alert_event / llm_notification_log 等表

### 开发环境准备（开发人员处理）

- [ ] `gijela-core` 最新代码已拉取
  ```bash
  cd d:\workspace\gijela\gijela-core
  git pull origin main
  ```
  
- [ ] `gijela-bloom` 最新代码已拉取
  ```bash
  cd d:\workspace\gijela\gijela-bloom\gijela-bloom-chat
  git pull origin main
  ```

- [ ] Node.js 版本确认：v23.11.0+（使用 nvm）
  ```bash
  nvm use v23.11.0
  node --version
  ```

### 版本确认（开发人员校验）

| 组件 | 要求 | 方式 |
|------|------|------|
| Java | 17+ | `java -version` |
| Maven | 3.9.9+ | `mvn -v` |
| Node.js | 23.11.0+ | `node -v` |

---

## 二、Day1 开工前检查（Day1 09:00 前 1 小时）

### 后端 Team

- [ ] **B1**: gijela-core-chat 模块 pom.xml 已集成 ES 依赖
  ```xml
  <dependency>
      <groupId>org.elasticsearch.client</groupId>
      <artifactId>elasticsearch-rest-high-level-client</artifactId>
      <version>7.17.0</version>
  </dependency>
  ```

- [ ] **B2**: 数据库迁移脚本已准备
  - 文件：`gijela-core-chat/src/main/resources/db/migration/V8__llm_log_tables.sql`
  - 检查：`SELECT COUNT(*) FROM llm_log_access;` (应该返回 0)

- [ ] **B3**: logback 配置已更新
  - 文件：`gijela-core-chat/src/main/resources/logback-admin.xml`
  - 需包含：JSON appender 用于 structured logging

- [ ] **B4**: 应用启动成功
  ```bash
  mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9016"
  # 或使用 Dockerfile 构建镜像
  ```

### 前端 Team

- [ ] **F1**: src/views/llm-logs 目录已创建
  ```
  src/views/llm-logs/
  ├── index.ts              # 路由导出
  ├── DashboardLayout.vue   # 主容器
  ├── pages/
  │   ├── Overview.vue      # 概览页
  │   ├── Troubleshoot.vue  # 问题排查
  │   ├── Audit.vue         # 审计日志
  │   └── Alerts.vue        # 告警管理
  └── components/
      ├── MetricCard.vue
      ├── ChartContainer.vue
      └── LogTable.vue
  ```

- [ ] **F2**: 路由已注册
  - 文件：`src/router/index.ts`
  - 需包含：`/dashboard/llm-logs/*` 路由及懒加载

- [ ] **F3**: API client 已生成
  - 文件：`src/api/llm-logs.ts`
  - 需包含：6 个 API 端点（timeseries, topn, distribution, records, trace, export）

### 运维 Team

- [ ] **O1**: ES 索引模板已部署
  ```bash
  curl -X PUT http://localhost:9200/_index_template/llm-runtime \
    -H "Content-Type: application/json" \
    -d @SPEC-LLM-LOG-ES-CONFIG-20260507-001.json
  ```

- [ ] **O2**: ILM 策略已部署
  ```bash
  curl -X PUT http://localhost:9200/_ilm/policy/llm-retention \
    -H "Content-Type: application/json" \
    -d '{...}'
  ```

- [ ] **O3**: ingest pipeline 已部署（脱敏）
  ```bash
  curl -X PUT http://localhost:9200/_ingest/pipeline/llm-desensitize \
    -H "Content-Type: application/json" \
    -d '{...}'
  ```

---

## 三、Day1-Day3 开工执行（按日期）

### Day1 任务清单（2026-05-08）

#### 后端（1.5 人·天）

```markdown
- [ ] B1: LlmLogEventPublisher 实现
  文件: gijela-core-chat/src/main/java/com/gijela/morpheus/pistol/llm/log/event/LlmLogEventPublisher.java
  方法签名冻结:
    + void publish(LlmLogEvent event)
    + void publishAsync(LlmLogEvent event)
  验收:
    + 日志事件成功发布到 Logback
    + 无异常日志输出

- [ ] B2: 日志映射与 VO 实现
  文件: 
    - gijela-core-chat/src/main/java/.../llm/log/dto/LlmAccessLogVO.java
    - gijela-core-chat/src/main/java/.../llm/log/dto/LlmRuntimeLogVO.java
    - gijela-core-chat/src/main/java/.../llm/log/dto/LlmAuditLogVO.java
    - gijela-core-chat/src/main/java/.../llm/log/dto/LlmMetricLogVO.java
  方法签名冻结:
    + LlmAccessLogVO toAccessVO()
    + LlmRuntimeLogVO toRuntimeVO()
    + LlmAuditLogVO toAuditVO()
    + LlmMetricLogVO toMetricVO()
  验收:
    + 4 个 VO 类已编译通过
    + JSON 序列化无异常

- [ ] B3: 基础 API 框架搭建
  文件: gijela-core-chat/src/main/java/.../llm/log/controller/LlmLogController.java
  方法签名冻结:
    + @PostMapping("/query/timeseries") ResponseEntity<ApiResponse<TimeSeriesResponse>> timeseries(TimeSeriesRequest req)
    + @PostMapping("/query/topn") ResponseEntity<ApiResponse<TopNResponse>> topN(TopNRequest req)
    + @PostMapping("/query/distribution") ResponseEntity<ApiResponse<DistributionResponse>> distribution(DistributionRequest req)
    + @GetMapping("/query/records") ResponseEntity<ApiResponse<Page<LlmLogRecordVO>>> records(LogQueryFilter filter, Pageable page)
  验收:
    + 4 个接口已编译通过
    + 可接收 mock 请求（返回 mock 数据）
    + Swagger 文档自动生成

- [ ] B4: Redis + MockES 集成
  文件: 
    - gijela-core-chat/src/main/java/.../llm/log/cache/LlmLogCacheService.java
    - gijela-core-chat/src/main/java/.../llm/log/es/EsClientMock.java
  方法签名冻结:
    + String getFromCache(String key) // Redis mock
    + void setToCache(String key, String value, long ttl)
    + SearchResponse mockEsQuery(SearchRequest request) // MockES
  验收:
    + Redis 连接成功
    + MockES 可返回模拟结果
    + 无 NPE 错误
```

#### 前端（1 人·天）

```markdown
- [ ] F1: 页面框架搭建
  文件: 
    - src/views/llm-logs/DashboardLayout.vue (主容器)
    - src/views/llm-logs/pages/Overview.vue (概览页)
  功能:
    + 左侧导航栏（4 个菜单项）
    + 主内容区（占位符卡片）
    + 响应式布局（移动端支持）
  验收:
    + 页面加载无错误
    + 样式基本正确（未完全调优）

- [ ] F2: 路由与权限集成
  文件: src/router/index.ts
  功能:
    + 注册 /dashboard/llm-logs 路由
    + 权限检查（需 role:llm-log-viewer）
    + 懒加载 LlmLogsLayout 组件
  验收:
    + 路由可访问
    + 无权限用户跳转到 403 页面
    + 控制台无 warning

- [ ] F3: API Client 生成
  文件: src/api/llm-logs.ts
  功能:
    + 6 个 API 方法（使用 mock 响应）
    + Axios 错误处理
    + loading 状态管理
  验收:
    + 编译通过
    + 模拟请求可返回 mock 数据
```

#### 运维（0.5 人·天）

```markdown
- [ ] O1: ES 集群状态验证
  检查项:
    + 集群健康状态: curl http://localhost:9200/_cluster/health
    + 索引模板已部署: curl http://localhost:9200/_index_template/llm-runtime
    + ILM 策略已部署: curl http://localhost:9200/_ilm/policy/llm-retention
    + ingest pipeline 已部署: curl http://localhost:9200/_ingest/pipeline/llm-desensitize
  验收:
    + 所有检查返回 HTTP 200
    + 集群状态为 green 或 yellow

- [ ] O2: 数据库状态验证
  检查项:
    + 表已创建: mysql> SHOW TABLES LIKE 'llm_%'
    + 版本已迁移: mysql> SELECT * FROM flyway_schema_history
  验收:
    + 返回 8+ 个 llm_* 表
    + 迁移版本 >= V9
```

### Day1 验收标准（Day1 17:00）

- [ ] 4 个后端 API 接口可接收请求（返回 mock 数据）
- [ ] 前端 2 个页面加载无错误
- [ ] ES 集群索引模板已部署
- [ ] 数据库初期化完成
- [ ] **无 P0 阻塞问题**

---

### Day2 任务清单（2026-05-09）

#### 后端（2 人·天）

```markdown
- [ ] B5: EsQueryBuilder 实现
  文件: gijela-core-chat/src/main/java/.../llm/log/es/EsQueryBuilder.java
  方法签名:
    + SearchRequest buildTimeSeriesDsl(TimeSeriesRequest req) // 时间序列
    + SearchRequest buildTopNDsl(TopNRequest req)              // Top N
    + SearchRequest buildRecordsDsl(LogQueryFilter filter, Pageable page) // 记录
    + SearchRequest buildTraceDsl(String traceId)             // 链路追踪
  验收:
    + 4 个 DSL 生成方法编译通过
    + 返回的 SearchRequest 结构正确
    + 能处理边界情况（空过滤器、极限页码）

- [ ] B6: EsDesensitizer 实现
  文件: gijela-core-chat/src/main/java/.../llm/log/es/EsDesensitizer.java
  方法签名:
    + Map<String, Object> desensitize(Map<String, Object> doc)
    + String desensitizePhone(String phone)
    + String desensitizeIdCard(String idCard)
    + String desensitizeApiKey(String apiKey)
  验收:
    + 脱敏规则正确（保留首尾，中间打码）
    + 性能 > 1000 ops/sec
    + 无异常日志

- [ ] B7: LlmLogQueryServiceImpl 实现
  文件: gijela-core-chat/src/main/java/.../llm/log/service/impl/LlmLogQueryServiceImpl.java
  方法签名:
    + TimeSeriesResponse timeseries(TimeSeriesRequest req)
    + TopNResponse topN(TopNRequest req)
    + Page<LlmLogRecordVO> records(LogQueryFilter filter, Pageable page)
    + LlmTraceResponse trace(String traceId)
  验收:
    + 4 个方法编译通过
    + 能调用 ES（真实或 mock）
    + 结果脱敏正确
    + 缓存生效（Redis）
```

#### 前端（1 人·天）

```markdown
- [ ] F3: 图表组件集成
  文件: 
    - src/views/llm-logs/pages/Overview.vue
    - src/components/llm-logs/KPICard.vue
    - src/components/llm-logs/LineChart.vue
  功能:
    + KPI 卡片：QPS、错误率、P95、首 Token
    + 折线图：最近 24 小时趋势
    + 表格：Top 10 最慢请求
    + 实时自刷新（30 秒间隔）
  验收:
    + 图表可正确渲染
    + 数据绑定正确（使用 mock API）
    + 性能 FCP < 2s
    + 响应式布局正确
```

### Day2 验收标准（Day2 17:00）

- [ ] ES 查询全部集成（4 个 DSL 方法）
- [ ] 脱敏引擎性能达标
- [ ] 前端图表可展示 mock 数据
- [ ] **无 P0 / P1 阻塞问题**

---

### Day3 任务清单（2026-05-10）

#### 后端（1.5 人·天）

```markdown
- [ ] B8: 告警规则初期化
  文件: gijela-core-chat/src/main/java/.../llm/log/alert/AlertRuleInitializer.java
  内容:
    + 6 个 P0 规则初期化
    + 规则持久化到数据库
    + 调度器启动
  验收:
    + mysql> SELECT COUNT(*) FROM llm_alert_rule; (返回 6)

- [ ] B9: 通知渠道集成与测试
  文件:
    - gijela-core-chat/src/main/java/.../llm/log/alert/notification/WechatNotificationClient.java
    - gijela-core-chat/src/main/java/.../llm/log/alert/notification/DingdingNotificationClient.java
    - gijela-core-chat/src/main/java/.../llm/log/alert/notification/EmailNotificationClient.java
  验收:
    + 三种渠道可接收测试消息
    + 延迟 < 2 秒
    + 无异常日志
```

#### QA（1 人·天）

```markdown
- [ ] Q1: 端到端测试与验收
  测试场景:
    + 场景 1：完整日志链路（写入 → 查询 → 脱敏）
    + 场景 2：告警规则触发（模拟高错误率 → 触发告警 → 通知发送）
    + 场景 3：查询性能（P95 < 500ms）
  验收:
    + 3 个场景全部通过
    + 无崩溃或 hang 现象
```

#### 运维（0.5 人·天）

```markdown
- [ ] O2: 灰度上线准备
  内容:
    + 部署检查清单
    + 监控告警配置
    + 回滚方案就位
  验收:
    + 10% 用户灰度成功
    + 监控数据正常
    + 无 P0 告警
```

### Day3 验收标准（Day3 17:00）

- [ ] 告警规则已初期化并可触发
- [ ] 三种通知渠道已验证
- [ ] E2E 测试全部通过
- [ ] **灰度上线成功（10% 用户）**

---

## 四、关键里程碑与截止时间

| 里程碑 | 日期 | 负责 | 状态 |
|--------|------|------|------|
| 规划冻结 & 代码框架 | 2026-05-07 | PM + Arch | ✅ |
| 前置环境检查 | 2026-05-08 09:00 | Ops | [ ] |
| Day1 开工 | 2026-05-08 10:00 | All | [ ] |
| Day1 验收 | 2026-05-08 17:00 | QA | [ ] |
| Day2 开工 | 2026-05-09 10:00 | All | [ ] |
| Day2 验收 | 2026-05-09 17:00 | QA | [ ] |
| Day3 开工 | 2026-05-10 10:00 | All | [ ] |
| Day3 验收 & 灰度 | 2026-05-10 17:00 | QA + Ops | [ ] |
| 全量上线 | 2026-05-11 10:00 | Ops | [ ] |
| 稳定期（Day4-7） | 2026-05-11 ~ 2026-05-14 | All | [ ] |

---

## 五、回滚方案（应急）

### 触发条件（任一满足立即回滚）

- [ ] 查询延迟 P95 > 1000ms（连续 5 分钟）
- [ ] 错误率 > 5%（连续 3 分钟）
- [ ] P0 告警 > 3 个/小时（假告警）
- [ ] 日志丢失率 > 0.1%（连续 3 分钟）

### 回滚步骤

```bash
# Step 1: 关闭看板入口
kubectl patch configmap llm-observability-enabled \
  --type merge -p '{"data":{"enabled":"false"}}'

# Step 2: 清理路由与菜单
# 前端删除 /dashboard/llm-logs 菜单项
# 后端禁用 /api/v1/llm-logs/* 接口

# Step 3: 保留 ES 数据（不删除）
# 便于后续分析根因

# Step 4: 验证回滚
curl http://localhost:9016/api/v1/llm-logs/query/timeseries
# 预期：404 或 403

# Step 5: 发布事后报告
# 包括：根因分析、修复方案、预防措施
```

---

## 六、通知与沟通

### 每日站会（Daily Standup）

```
时间: 09:30 ~ 09:45
地点: 钉钉语音
参与: 后端 Lead + 前端 Lead + QA + Ops
议题:
  1. 昨日完成情况
  2. 今日计划
  3. 阻塞项 & 风险
```

### 每日技术同步（Day1-Day3）

```
时间: 14:00 ~ 14:30
地点: 物理会议室 / 线上会议
参与: 后端 + 前端 + QA
议题:
  1. API 契约同步
  2. 前后端集成进度
  3. 测试覆盖情况
```

### 灰度前评审（Day3 16:00）

```
参与: 项目 Lead + Tech Lead + QA + Ops
内容: 
  1. 验收标准 Go/No-Go 确认
  2. 灰度计划最终评审
  3. 风险及回滚方案确认
  4. 通知通道测试
```

---

## 七、生成的代码框架清单

### 后端（gijela-core-chat）

```
src/main/java/com/gijela/morpheus/pistol/llm/log/
├── controller/
│   └── LlmLogController.java
├── service/
│   ├── LlmLogQueryService.java (interface)
│   └── impl/
│       ├── LlmLogQueryServiceImpl.java
│       ├── LlmLogPublisherService.java
│       └── LlmLogAggregationService.java
├── dto/
│   ├── request/ (6 个 request DTO)
│   ├── response/ (6 个 response DTO)
│   └── vo/ (4 个 log VO)
├── mapper/
│   ├── LlmAlertRuleMapper.java
│   ├── LlmAlertEventMapper.java
│   └── LlmNotificationLogMapper.java
├── event/
│   └── LlmLogEventPublisher.java
├── es/
│   ├── EsClientConfig.java
│   ├── EsQueryBuilder.java
│   ├── EsClientMock.java
│   └── EsDesensitizer.java
├── cache/
│   └── LlmLogCacheService.java
└── alert/
    ├── AlertRuleEvaluator.java
    ├── AlertRuleScheduler.java
    └── notification/ (3 个 client)
```

### 前端（gijela-bloom-chat）

```
src/views/llm-logs/
├── index.ts (路由导出)
├── DashboardLayout.vue (主容器)
├── pages/
│   ├── Overview.vue (概览)
│   ├── Troubleshoot.vue (问题排查)
│   ├── Audit.vue (审计)
│   └── Alerts.vue (告警)
├── components/
│   ├── MetricCard.vue
│   ├── LineChart.vue
│   ├── LogTable.vue
│   └── AlertRuleForm.vue
├── composables/
│   ├── useQuery.ts
│   ├── useChart.ts
│   └── useAlert.ts
└── types/
    └── llm-logs.ts
```

### 数据库（SQL）

```
migrations/
├── V8__llm_log_tables.sql (访问、运行、审计、指标表)
└── V9__llm_alert_tables.sql (告警规则、事件、通知日志表)
```

### ES 配置

```
config/
├── llm-runtime-template.json
├── llm-access-template.json
├── llm-audit-template.json
├── llm-metric-template.json
├── ilm-policy.json
└── ingest-pipeline.json
```

---

## 八、风险识别与缓解措施

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|---------|
| ES 集群不可用 | 低 | 高 | 提前进行 ES 容量规划 & 压力测试 |
| API 接口超时 | 中 | 中 | 设置缓存 TTL、查询超时（5s）、降级方案 |
| 脱敏规则漏网 | 低 | 高 | 事前审计 + E2E 测试覆盖 |
| 告警假阳过多 | 中 | 低 | 灰度期间收集数据、动态调整阈值 |
| 日志丢失 | 低 | 高 | 使用 Logback async appender + 失败重试 |
| 前端加载慢 | 中 | 中 | 组件懒加载、图表库优化、CDN 缓存 |

---

## 九、成功标准（Go-Live 检查单）

### 基础设施验证

- [ ] ES 集群健康状态 = green 或 yellow（至少 2 个节点活跃）
- [ ] 索引自动创建成功（日期格式：llm-runtime-2026.05.08）
- [ ] ILM 策略已应用
- [ ] ingest pipeline 脱敏功能验证通过

### 功能验证

- [ ] 6 个 API 接口均可正常调用
- [ ] 日志从应用写入 → ES → 前端展示全链路通过
- [ ] 脱敏覆盖率 = 100%
- [ ] 告警规则可自动触发 & 通知发送

### 性能验证

- [ ] 查询延迟 P95 < 500ms
- [ ] 脱敏性能 > 1000 ops/sec
- [ ] 前端首屏加载时间 < 2s
- [ ] 告警触发延迟 < 30s

### 测试覆盖

- [ ] 单元测试覆盖率 >= 80%
- [ ] E2E 测试场景 >= 3 个，全部通过
- [ ] 灰度测试 10% 用户无误报

### 文档完整性

- [ ] API 文档（Swagger）已生成
- [ ] 运维手册已交付
- [ ] 故障处理流程已记录
- [ ] 监控告警配置已验证

---

## 十、项目交付物清单

### 代码提交

- [ ] gijela-core-chat 完整实现（Day1-Day3）
- [ ] gijela-bloom-chat 前端实现（Day1-Day3）
- [ ] ES 配置文件（template、ILM、pipeline）参考规范已生成

### 文档交付

- [ ] API 文档（Swagger）
- [ ] 架构设计文档（ARCH-REVIEW × 7）
- [ ] 规范文档（SPEC × 9）
- [ ] 执行清单（PROJECT-EXECUTION-CHECKLIST）
- [ ] 运维手册（部署、监控、回滚）

### 验收通过

- [ ] QA 验收签名
- [ ] Ops 验收签名
- [ ] 产品经理认可
- [ ] 技术委员会通过

---

## 附录：快速命令速查表

```bash
# 环境检查
mvn --version          # 检查 Maven
java -version          # 检查 Java
node -v                # 检查 Node.js

# 代码更新
cd gijela-core && git pull origin main
cd gijela-bloom/gijela-bloom-chat && git pull origin main

# 应用启动
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9016"  # 后端
pnpm run dev                                                             # 前端

# API 测试
curl http://localhost:9016/api/v1/llm-logs/query/timeseries

# ES 日志查询
curl http://localhost:9200/llm-runtime-*/_search

# 灰度上线
kubectl patch configmap llm-observability-enabled \
  --type merge -p '{"data":{"grayRatio":"10"}}'

# 回滚
kubectl patch configmap llm-observability-enabled \
  --type merge -p '{"data":{"enabled":"false"}}'
```

---

**此清单需在 Day0 确认签名，Day1 执行**。
