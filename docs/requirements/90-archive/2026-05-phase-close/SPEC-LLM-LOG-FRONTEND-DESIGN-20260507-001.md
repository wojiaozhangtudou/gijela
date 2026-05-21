# SPEC-LLM-LOG-FRONTEND-DESIGN-20260507-001

> 目的：给前端看板页面提供组件架构与路由设计。

## 1. 路由与菜单结构

```typescript
// src/router/index.ts 新增路由
{
  path: '/llm-observability',
  component: LlmObservabilityLayout,
  meta: {
    title: 'LLM 可观测中心',
    requiresAuth: true,
    permission: 'llm:observability:view'
  },
  children: [
    {
      path: 'overview',
      component: () => import('@/views/LlmObservability/OverviewPage.vue'),
      meta: { title: 'SRE 总览', activeTab: 'overview' }
    },
    {
      path: 'troubleshoot',
      component: () => import('@/views/LlmObservability/TroubleshootPage.vue'),
      meta: { title: '研发排障', activeTab: 'troubleshoot' }
    },
    {
      path: 'audit',
      component: () => import('@/views/LlmObservability/AuditPage.vue'),
      meta: { title: '审计视图', activeTab: 'audit' }
    },
    {
      path: 'alerts',
      component: () => import('@/views/LlmObservability/AlertCenterPage.vue'),
      meta: { title: '告警中心', activeTab: 'alerts' }
    }
  ]
}
```

## 2. 核心组件树

### 2.1 总体布局

```
LlmObservabilityLayout.vue
├── ElTabs (activeTab 同步路由)
│   ├── Tab0: OverviewPage (SRE 总览)
│   ├── Tab1: TroubleshootPage (研发排障)
│   ├── Tab2: AuditPage (审计视图)
│   └── Tab3: AlertCenterPage (告警中心)
└── 全局过滤栏 (GlobalFilterBar)
    ├── 时间范围选择器 (DateRangePickerQuick)
    ├── 模型路由 (ModelRouteSelect)
    ├── 状态 (StatusCheckbox)
    └── 更多过滤 (AdvancedFilterDrawer)
```

### 2.2 SRE 总览页（OverviewPage.vue）

```
OverviewPage
├── KpiCardsRow
│   ├── KpiCard (QPS)
│   ├── KpiCard (ErrorRate%)
│   ├── KpiCard (P95 Latency)
│   ├── KpiCard (FirstToken P95)
│   └── KpiCard (ES Ingest Failure%)
├── ChartsGrid
│   ├── RequestTrendChart (折线图，时序)
│   ├── ErrorTrendChart (折线图，时序)
│   ├── SlowRequestTopNChart (柱状图，TopN)
│   ├── ModelRouteComparisonChart (横向柱，分组对比)
│   ├── ErrorCodeDistributionChart (饼图)
│   └── ToolErrorRateChart (表格，TopN)
└── AutoRefreshControl (刷新间隔)
```

关键组件：
- `KpiCard.vue`：展示数值 + 环比/同比趋势
- `LineChart.vue`：基于 ECharts 折线图（支持多条线、分组）
- `BarChart.vue`：基于 ECharts 柱状图
- `PieChart.vue`：基于 ECharts 饼图
- `DataTable.vue`：表格展示 TopN（支持排序、筛选）

### 2.3 研发排障页（TroubleshootPage.vue）

```
TroubleshootPage
├── SearchBar
│   ├── TraceIdInput (快速查询)
│   ├── SessionIdInput
│   ├── ErrorCodeSelect (多选)
│   ├── ToolNameSelect (多选)
│   └── SearchButton
├── ChartsGrid
│   ├── ErrorCodeTopNChart
│   ├── ToolErrorTopNChart
│   ├── SlowSessionTopNChart (会话 ID + 耗时)
│   └── ErrorTimelineChart (错误事件时间分布)
├── EventTable (主明细表)
│   ├── 列：eventTime, traceId, eventType, status, errorCode, latencyMs
│   ├── 操作列：查看详情、下钻 trace
│   └── 分页
└── TraceDetailDrawer (右侧抽屉)
    ├── TraceTimeline (垂直时间线)
    │   ├── chat.request
    │   ├── model.request
    │   ├── tool.invoke (可展开子事件)
    │   ├── model.response
    │   └── chat.finish
    └── 事件详情面板
```

关键组件：
- `TraceTimeline.vue`：可视化事件流
- `EventTable.vue`：可排序、可搜索的事件表
- `TraceDetailDrawer.vue`：右侧抽屉，展示 trace 链路

### 2.4 审计视图页（AuditPage.vue）

```
AuditPage
├── SearchBar
│   ├── TenantIdSelect (多租户)
│   ├── OperatorIdInput (操作人)
│   ├── ModelRouteSelect
│   ├── ActionTypeSelect (create/update/delete)
│   └── SearchButton
├── ChartsGrid
│   ├── OperationTrendChart (操作量时序)
│   └── HighRiskOperationTopNChart (TopN 高风险操作)
├── AuditEventTable (审计事件表)
│   ├── 列：eventTime, tenantId, operator, action, resource, status
│   ├── 内容脱敏显示（原文不显示）
│   └── 操作列：查看详情
└── AuditDetailDrawer
    ├── Before/After Diff 对比
    └── 操作确认信息
```

关键组件：
- `AuditEventTable.vue`：审计日志表格
- `DiffViewer.vue`：前后对比展示

### 2.5 告警中心页（AlertCenterPage.vue）

```
AlertCenterPage
├── TabBar (告警规则 / 告警历史)
│
├── AlertRulesTab
│   ├── CreateRuleButton
│   ├── RuleTable
│   │   ├── 列：ruleName, metric, threshold, condition, window, enabled, actions
│   │   ├── 操作列：编辑、删除、测试触发、日志查看
│   │   └── 分页
│   └── RuleDetailDrawer (编辑规则)
│       ├── 基础信息 (名称、描述)
│       ├── 规则配置 (metric/threshold/condition/window)
│       ├── 通知渠道 (企业微信/钉钉/邮件)
│       └── 保存/删除
│
└── AlertHistoryTab
    ├── StatusFilter (open/ack/resolved)
    ├── AlertEventTable
    │   ├── 列：triggeredAt, ruleId, metricValue, alertLevel, status, message
    │   ├── 操作列：确认、解决、查看日志
    │   └── 分页
    └── EventDetailDrawer
        ├── 告警触发原因
        ├── 对应的日志链接
        └── 处置记录
```

关键组件：
- `RuleTable.vue`：规则表格
- `RuleDetailDrawer.vue`：规则编辑抽屉
- `AlertEventTable.vue`：告警历史表
- `AlertDetailDrawer.vue`：告警详情

## 3. API 集成（Composables）

```typescript
// src/composables/useLlmLogQuery.ts
export const useLlmLogQuery = () => {
  const queryTimeseries = async (params: TimeSeriesRequest) => {
    return await api.post('/api/v1/llm-logs/query/timeseries', params)
  }
  
  const queryTopN = async (params: TopNRequest) => {
    return await api.post('/api/v1/llm-logs/query/topn', params)
  }
  
  const queryRecords = async (params: LogQueryFilter) => {
    return await api.post('/api/v1/llm-logs/query/records', params)
  }
  
  const getTraceDetail = async (traceId: string) => {
    return await api.get(`/api/v1/llm-logs/query/trace/${traceId}`)
  }
  
  return { queryTimeseries, queryTopN, queryRecords, getTraceDetail }
}

// 全局过滤状态管理
export const useLlmLogFilter = () => {
  const filter = reactive<LogQueryFilter>({
    startAt: new Date(Date.now() - 24 * 3600 * 1000),
    endAt: new Date(),
    modelRoute: 'default',
    // ...
  })
  
  const updateFilter = (partial: Partial<LogQueryFilter>) => {
    Object.assign(filter, partial)
  }
  
  return { filter, updateFilter }
}
```

## 4. 文件清单

```
src/
├── views/
│   └── LlmObservability/
│       ├── LlmObservabilityLayout.vue       (总体布局 + Tab 管理)
│       ├── OverviewPage.vue                 (SRE 总览)
│       ├── TroubleshootPage.vue             (研发排障)
│       ├── AuditPage.vue                    (审计视图)
│       ├── AlertCenterPage.vue              (告警中心)
│       └── components/
│           ├── GlobalFilterBar.vue          (全局过滤)
│           ├── AdvancedFilterDrawer.vue     (高级过滤)
│           ├── KpiCard.vue                  (KPI 卡片)
│           ├── LineChart.vue                (折线图)
│           ├── BarChart.vue                 (柱状图)
│           ├── PieChart.vue                 (饼图)
│           ├── DataTable.vue                (通用表格)
│           ├── TraceTimeline.vue            (时间线)
│           ├── TraceDetailDrawer.vue        (Trace 详情)
│           ├── AuditEventTable.vue          (审计表格)
│           ├── DiffViewer.vue               (Diff 对比)
│           ├── RuleTable.vue                (规则表格)
│           ├── RuleDetailDrawer.vue         (规则编辑)
│           ├── AlertEventTable.vue          (告警历表)
│           └── AlertDetailDrawer.vue        (告警详情)
├── api/
│   └── llmLog.ts                            (查询、告警 API 客户端)
├── composables/
│   ├── useLlmLogQuery.ts                    (查询逻辑)
│   ├── useLlmLogFilter.ts                   (过滤状态)
│   ├── useLlmAlertRule.ts                   (告警规则)
│   └── useLlmAutoRefresh.ts                 (自动刷新)
└── types/
    └── llmLog.ts                            (TypeScript 类型定义)
```

## 5. 开发顺序（建议并行）

1. **基础设施**（Day1）
   - [ ] 路由与布局框架
   - [ ] 全局过滤栏组件
   - [ ] 通用图表组件（LineChart/BarChart/PieChart）
   - [ ] API 客户端（mock 数据）

2. **SRE 总览**（Day1~Day2）
   - [ ] KPI 卡片
   - [ ] 趋势图表
   - [ ] 自动刷新

3. **研发排障**（Day2~Day3）
   - [ ] 搜索栏
   - [ ] 事件表格
   - [ ] Trace 时间线
   - [ ] 右侧抽屉集成

4. **审计视图**（Day3）
   - [ ] 审计表格
   - [ ] Diff 对比
   - [ ] 过滤条件

5. **告警中心**（Day3~Day4）
   - [ ] 规则管理
   - [ ] 规则编辑
   - [ ] 告警历史

## 6. 验收清单
- [ ] 所有路由可访问。
- [ ] 图表与表格支持 mock 数据渲染。
- [ ] 全局过滤栏生效（同步改变所有图表）。
- [ ] Trace 下钻可展开完整链路。
- [ ] 响应式设计在移动端正常。
- [ ] 无 TypeScript 类型错误。
