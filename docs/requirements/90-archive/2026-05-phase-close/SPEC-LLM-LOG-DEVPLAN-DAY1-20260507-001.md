# SPEC-LLM-LOG-DEVPLAN-DAY1-20260507-001

> 目的：Day1 开工清单（第一天要做什么、人天、方法签名冻结）。

## 阶段总览
- **Duration**: Day1 = 1 整个工作日
- **Team**: 后端 1.5 人、前端 1 人、运维 0.5 人
- **Goal**: ES 部署 + 后端 API mock + 前端路由骨架就位

## 后端 Team（1.5 人·天）

### 任务 B1：依赖引入与项目结构（0.5 人·天）
**责任人**：后端主程  
**方法签名冻结**：

```java
// pom.xml 新增依赖
<dependency>
    <groupId>org.elasticsearch.client</groupId>
    <artifactId>elasticsearch-rest-high-level-client</artifactId>
    <version>7.17.0</version>
</dependency>
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
    <version>3.3.0</version>
</dependency>

// 新增配置类
class EsClientConfig {
    public RestHighLevelClient elasticsearchClient() { }
    public RestClient restClient() { }
}

// 新增 properties
llm-log.es.host=localhost
llm-log.es.port=9200
llm-log.es.username=elastic
llm-log.es.password=password
```

**验收标准**：
- [ ] pom.xml 已更新，依赖可解析。
- [ ] EsClientConfig 类已创建，@Bean 正确。
- [ ] 应用启动无红线。

---

### 任务 B2：DTO/VO/Mapper 骨架（0.5 人·天）
**责任人**：后端主程  
**核心类定义**：

```java
// domain/dto/llmlog/LogQueryFilter.java
@Data @Builder
public class LogQueryFilter {
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    // ... 所有字段见 SPEC-LLM-LOG-API
    public void validate() { }
}

// domain/dto/llmlog/TimeSeriesRequest.java
@Data
public class TimeSeriesRequest {
    private LogQueryFilter filter;
    private String metric;
    private String interval;
    private List<String> groupBy;
}

// domain/vo/llmlog/TimeSeriesResponse.java
@Data
public class TimeSeriesResponse {
    private List<TimeBucket> buckets;
    @Data @Builder
    public static class TimeBucket {
        private LocalDateTime ts;
        private Long value;
        private String group;
    }
}

// mapper/LlmAlertEventMapper.java
@Mapper
public interface LlmAlertEventMapper extends BaseMapper<LlmAlertEvent> {
    List<LlmAlertEvent> selectByRuleIdAndTimeRange(...);
}

// domain/entity/LlmAlertRule.java
@Data @TableName("llm_alert_rule")
public class LlmAlertRule extends BaseEntity {
    // 所有字段见 SQL DDL
}

// domain/entity/LlmAlertEvent.java
@Data @TableName("llm_alert_event")
public class LlmAlertEvent extends BaseEntity {
    // 所有字段见 SQL DDL
}
```

**验收标准**：
- [ ] 6 个 DTO/VO 类已定义（无方法体）。
- [ ] 2 个 Mapper 接口已定义。
- [ ] 2 个 Entity 类已定义。
- [ ] TypeScript/Swagger 文件已生成。

---

### 任务 B3：Service 接口与 mock 实现（0.5 人·天）
**责任人**：后端 API 层开发  
**方法签名冻结**：

```java
// service/LlmLogQueryService.java
public interface LlmLogQueryService {
    TimeSeriesResponse timeseries(LogQueryFilter filter, String metric, String interval, List<String> groupBy);
    TopNResponse topn(LogQueryFilter filter, String metric, String dimension, Integer limit);
    DistributionResponse distribution(LogQueryFilter filter, String metric, List<String> dimensions);
    PageResponse<LogRecord> records(LogQueryFilter filter);
    TraceDetailResponse traceDetail(String traceId);
    String export(LogQueryFilter filter, List<String> columns, String format);
}

// service/impl/LlmLogQueryServiceImpl.java （mock 版，返回示例数据）
@Service
public class LlmLogQueryServiceImpl implements LlmLogQueryService {
    @Override
    public TimeSeriesResponse timeseries(LogQueryFilter filter, String metric, String interval, List<String> groupBy) {
        // 返回 mock 数据
        return TimeSeriesResponse.builder()
            .buckets(List.of(
                TimeBucket.builder().ts(LocalDateTime.now()).value(120L).group("default").build(),
                TimeBucket.builder().ts(LocalDateTime.now().plusMinutes(5)).value(125L).group("default").build()
            ))
            .build();
    }
    // 其他方法类似
}
```

**验收标准**：
- [ ] 6 个接口已定义。
- [ ] mock 实现已就位，无 ES 依赖。
- [ ] Controller 可调用，返回示例数据。

---

### 任务 B4：Controller 端点（0.5 人·天）
**责任人**：后端 API 层开发  
**方法签名冻结**：

```java
// controller/LlmLogQueryController.java
@RestController
@RequestMapping("/api/v1/llm-logs")
public class LlmLogQueryController {
    @PostMapping("/query/timeseries")
    public ApiResponse<TimeSeriesResponse> timeseries(@RequestBody TimeSeriesRequest request) {
        LogQueryFilter filter = request.getFilter();
        filter.setTenantId(getCurrentTenantId());
        filter.validate();
        TimeSeriesResponse result = llmLogQueryService.timeseries(...);
        return ApiResponse.success(result);
    }
    
    @PostMapping("/query/topn")
    public ApiResponse<TopNResponse> topn(@RequestBody TopNRequest request) { }
    
    @PostMapping("/query/distribution")
    public ApiResponse<DistributionResponse> distribution(...) { }
    
    @PostMapping("/query/records")
    public ApiResponse<PageResponse<LogRecord>> records(...) { }
    
    @GetMapping("/query/trace/{traceId}")
    public ApiResponse<TraceDetailResponse> traceDetail(@PathVariable String traceId) { }
    
    @PostMapping("/query/export")
    public ApiResponse<String> export(@RequestBody ExportRequest request) { }
}

// controller/LlmAlertController.java
@RestController
@RequestMapping("/api/v1/llm-alerts")
public class LlmAlertController {
    @GetMapping("/rules")
    public ApiResponse<List<AlertRuleVO>> listRules(...) { }
    
    @PostMapping("/rules")
    public ApiResponse<Long> createRule(@RequestBody AlertRuleRequest request) { }
    
    @PutMapping("/rules/{ruleId}")
    public ApiResponse<Void> updateRule(@PathVariable Long ruleId, ...) { }
    
    @GetMapping("/events")
    public ApiResponse<PageResponse<AlertEventVO>> listAlertEvents(...) { }
}
```

**验收标准**：
- [ ] 8 个端点已定义。
- [ ] 可通过 curl/postman 调用。
- [ ] 返回值格式与契约一致。
- [ ] 租户隔离逻辑就位。

---

## 前端 Team（1 人·天）

### 任务 F1：路由与布局骨架（0.5 人·天）
**责任人**：前端主程  
**核心组件**：

```typescript
// src/router/index.ts
const llmObservabilityRoutes = [
  {
    path: '/llm-observability',
    component: LlmObservabilityLayout,
    children: [
      { path: 'overview', component: OverviewPage },
      { path: 'troubleshoot', component: TroubleshootPage },
      { path: 'audit', component: AuditPage },
      { path: 'alerts', component: AlertCenterPage }
    ]
  }
]

// src/views/LlmObservability/LlmObservabilityLayout.vue
<template>
  <div class="llm-observability-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="SRE 总览" name="overview">
        <OverviewPage />
      </el-tab-pane>
      <el-tab-pane label="研发排障" name="troubleshoot">
        <TroubleshootPage />
      </el-tab-pane>
      <el-tab-pane label="审计视图" name="audit">
        <AuditPage />
      </el-tab-pane>
      <el-tab-pane label="告警中心" name="alerts">
        <AlertCenterPage />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

// 占位符页面
// src/views/LlmObservability/OverviewPage.vue
<template>
  <div class="overview-page">
    <h2>SRE 总览</h2>
    <p>待实现...</p>
  </div>
</template>
```

**验收标准**：
- [ ] 4 个路由可访问。
- [ ] 占位符页面正常渲染。
- [ ] 选项卡切换正常。
- [ ] TypeScript 无类型错误。

---

### 任务 F2：API 客户端与 mock（0.5 人·天）
**责任人**：前端集成开发  
**核心文件**：

```typescript
// src/api/llmLog.ts
export const llmLogApi = {
  queryTimeseries: (data: TimeSeriesRequest) => 
    api.post('/api/v1/llm-logs/query/timeseries', data),
  
  queryTopN: (data: TopNRequest) => 
    api.post('/api/v1/llm-logs/query/topn', data),
  
  queryRecords: (data: LogQueryFilter) => 
    api.post('/api/v1/llm-logs/query/records', data),
  
  getTraceDetail: (traceId: string) => 
    api.get(`/api/v1/llm-logs/query/trace/${traceId}`)
}

// src/composables/useLlmLogQuery.ts
export const useLlmLogQuery = () => {
  const loading = ref(false)
  const error = ref<string | null>(null)
  
  const queryTimeseries = async (params: TimeSeriesRequest) => {
    loading.value = true
    try {
      return await llmLogApi.queryTimeseries(params)
    } catch (e) {
      error.value = (e as Error).message
    } finally {
      loading.value = false
    }
  }
  
  return { loading, error, queryTimeseries }
}

// src/mocks/llmLog.ts （mock 数据）
export const mockTimeSeriesResponse: TimeSeriesResponse = {
  buckets: [
    { ts: '2026-05-07T10:00:00Z', value: 120, group: 'default' },
    { ts: '2026-05-07T10:05:00Z', value: 125, group: 'default' }
  ]
}

// vite.config.ts 配置 mock
import { createMockServer } from 'vitest'

export default defineConfig({
  plugins: [
    // 本地开发时自动 mock API
  ]
})
```

**验收标准**：
- [ ] 6 个 API 接口已定义。
- [ ] mock 数据已就位。
- [ ] 无 TypeScript 类型错误。
- [ ] Swagger OpenAPI 文件可导入。

---

## 运维 Team（0.5 人·天）

### 任务 O1：ES 部署与初始化（0.5 人·天）
**责任人**：运维/SRE  
**执行清单**：

```bash
# 1. 启动 ES 容器（Docker）
docker run -d \
  -e discovery.type=single-node \
  -e xpack.security.enabled=true \
  -e ELASTIC_PASSWORD=password \
  -p 9200:9200 \
  docker.elastic.co/elasticsearch/elasticsearch:7.17.0

# 2. 执行初始化脚本
bash init-es.sh

# 3. 验证
curl -u elastic:password http://localhost:9200/_cat/indices
curl -u elastic:password http://localhost:9200/_ilm/policy
curl -u elastic:password http://localhost:9200/_ingest/pipeline
```

**验收标准**：
- [ ] ES 集群状态 green。
- [ ] 4 个索引模板已创建。
- [ ] 1 个 ILM 策略已创建。
- [ ] 1 个 ingest pipeline 已创建。
- [ ] 初始 3 个索引已创建（runtime/access/audit）。

---

## 跨功能同步

### 下午 3 点同步会
- 后端：展示 6 个 API 端点 (Postman collection)
- 前端：展示 4 个路由与 mock 集成
- 运维：确认 ES 就绪，share DSL 查询模板
- 遗留问题：... (如有)

---

## Day1 End-of-Day 验收清单
- [ ] 后端 6 个 API 可调用（mock 数据）
- [ ] 前端 4 个路由可访问
- [ ] ES 集群部署完成
- [ ] 无 P0 阻塞问题
- [ ] Day2 开工物料准备（例如 ES 查询模板）
