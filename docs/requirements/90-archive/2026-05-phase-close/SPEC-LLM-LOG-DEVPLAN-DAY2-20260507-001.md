# SPEC-LLM-LOG-DEVPLAN-DAY2-20260507-001

> 目的：Day2 开工清单（ES 查询集成、脱敏、聚合实现）。

## 阶段总览
- **Duration**: Day2 = 1 整个工作日
- **Team**: 后端 2 人、前端 1 人
- **Goal**: ES 查询全链路就位、前端图表数据联动、脱敏验证通过
- **依赖**：Day1 产出（mock API、ES 集群、路由骨架）

## 后端 Team（2 人·天）

### 任务 B5：ES 查询 DSL 模板化生成（1 人·天）
**责任人**：后端 ES 集成开发  
**核心类**：

```java
// infra/es/EsQueryBuilder.java
@Component
public class EsQueryBuilder {
    
    /**
     * 构造时序查询 DSL
     */
    public SearchRequest buildTimeSeriesDsl(
        LogQueryFilter filter, 
        String metric,
        String interval,
        List<String> groupBy
    ) {
        // 1. 构造基础 bool query
        BoolQuery boolQuery = buildBaseQuery(filter);
        
        // 2. 构造聚合
        AggregationBuilder agg = AggregationBuilders
            .dateHistogram("time_buckets")
            .field("@timestamp")
            .calendarInterval(DateHistogramInterval.parse(interval));
        
        // 3. 添加指标聚合
        switch (metric) {
            case "qps" -> agg.subAggregation(
                AggregationBuilders.count("qps_count").field("traceId")
            );
            case "errorRate" -> {
                agg.subAggregation(
                    AggregationBuilders.count("total_count").field("traceId")
                );
                agg.subAggregation(
                    AggregationBuilders.filter("error_filter", 
                        QueryBuilders.matchQuery("status", "FAILED"))
                    .subAggregation(
                        AggregationBuilders.count("error_count").field("traceId")
                    )
                );
            }
            case "p95Latency" -> agg.subAggregation(
                AggregationBuilders.percentiles("latency_percentiles")
                    .field("latencyMs")
                    .percentiles(95.0)
            );
            // 其他指标...
        }
        
        // 4. 添加分组聚合
        if (groupBy != null && !groupBy.isEmpty()) {
            for (String dimension : groupBy) {
                agg.subAggregation(
                    AggregationBuilders.terms(dimension + "_group")
                        .field(dimension)
                        .size(100)
                );
            }
        }
        
        SearchRequest request = new SearchRequest("llm-runtime-*");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(boolQuery)
            .aggregation(agg)
            .size(0)  // 不需要原始文档
            .trackTotalHits(true);
        
        request.source(sourceBuilder);
        return request;
    }
    
    /**
     * 构造 TopN 查询 DSL
     */
    public SearchRequest buildTopNDsl(
        LogQueryFilter filter,
        String metric,
        String dimension,
        Integer limit
    ) {
        BoolQuery boolQuery = buildBaseQuery(filter);
        
        // 构造 terms 聚合
        AggregationBuilder agg = AggregationBuilders
            .terms(dimension + "_top")
            .field(dimension)
            .size(limit != null ? limit : 10);
        
        // 添加指标聚合（count）
        agg.subAggregation(
            AggregationBuilders.count("metric_value").field("traceId")
        );
        
        SearchRequest request = new SearchRequest("llm-runtime-*");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(boolQuery)
            .aggregation(agg)
            .size(0);
        
        request.source(sourceBuilder);
        return request;
    }
    
    /**
     * 构造记录查询 DSL
     */
    public SearchRequest buildRecordsDsl(LogQueryFilter filter) {
        BoolQuery boolQuery = buildBaseQuery(filter);
        
        SearchRequest request = new SearchRequest("llm-runtime-*");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(boolQuery)
            .size(filter.getPageSize())
            .from((filter.getPageNo() - 1) * filter.getPageSize())
            .sort("@timestamp", SortOrder.DESC);
        
        request.source(sourceBuilder);
        return request;
    }
    
    /**
     * 构造 Trace 查询 DSL
     */
    public SearchRequest buildTraceDsl(String traceId) {
        BoolQuery boolQuery = QueryBuilders.boolQuery()
            .must(QueryBuilders.matchQuery("traceId", traceId));
        
        SearchRequest request = new SearchRequest("llm-runtime-*");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(boolQuery)
            .size(100)
            .sort("eventTime", SortOrder.ASC);
        
        request.source(sourceBuilder);
        return request;
    }
    
    /**
     * 基础 bool query 构造
     */
    private BoolQuery buildBaseQuery(LogQueryFilter filter) {
        BoolQuery boolQuery = QueryBuilders.boolQuery();
        
        // 时间范围
        boolQuery.must(QueryBuilders.rangeQuery("@timestamp")
            .gte(filter.getStartAt())
            .lte(filter.getEndAt()));
        
        // 租户隔离
        boolQuery.must(QueryBuilders.matchQuery("tenantId", filter.getTenantId()));
        
        // 应用码
        if (filter.getAppCode() != null) {
            boolQuery.must(QueryBuilders.matchQuery("appCode", filter.getAppCode()));
        }
        
        // 模型路由
        if (filter.getModelRoute() != null) {
            boolQuery.must(QueryBuilders.matchQuery("modelRoute", filter.getModelRoute()));
        }
        
        // 提供商
        if (filter.getProvider() != null && !filter.getProvider().isEmpty()) {
            boolQuery.must(QueryBuilders.termsQuery("provider", filter.getProvider()));
        }
        
        // 状态
        if (filter.getStatus() != null && !filter.getStatus().isEmpty()) {
            boolQuery.must(QueryBuilders.termsQuery("status", filter.getStatus()));
        }
        
        // 错误码
        if (filter.getErrorCode() != null && !filter.getErrorCode().isEmpty()) {
            boolQuery.must(QueryBuilders.termsQuery("errorCode", filter.getErrorCode()));
        }
        
        // 工具名
        if (filter.getToolName() != null && !filter.getToolName().isEmpty()) {
            boolQuery.must(QueryBuilders.termsQuery("toolName", filter.getToolName()));
        }
        
        // 会话 ID
        if (filter.getSessionId() != null) {
            boolQuery.must(QueryBuilders.matchQuery("sessionId", filter.getSessionId()));
        }
        
        // 关键词搜索
        if (filter.getKeyword() != null) {
            boolQuery.must(QueryBuilders.multiMatchQuery(filter.getKeyword())
                .field("errorMsg")
                .field("toolName"));
        }
        
        return boolQuery;
    }
}
```

**验收标准**：
- [ ] 4 个 DSL 构造方法已实现。
- [ ] 租户隔离逻辑已落地。
- [ ] 无编译错误。
- [ ] 支持所有过滤条件组合。

---

### 任务 B6：脱敏与字段转换（0.5 人·天）
**责任人**：后端 ES 集成开发  
**核心类**：

```java
// infra/es/EsDesensitizer.java
@Component
public class EsDesensitizer {
    
    private static final List<String> SENSITIVE_FIELDS = List.of(
        "promptRaw", "contentRaw", "attachmentRaw",
        "authorization", "apiKey", "cookie"
    );
    
    private static final Map<String, Pattern> DESENSITIZATION_PATTERNS = Map.ofEntries(
        Map.entry("phone", Pattern.compile("\\b(1[3-9]\\d{9})\\b")),
        Map.entry("idCard", Pattern.compile("\\b([0-9]{6}[0-9]{8}|[0-9]{18})\\b")),
        Map.entry("apiKey", Pattern.compile("(Authorization|apiKey|Bearer)\\s*[:=]\\s*([\\w-]+)"))
    );
    
    /**
     * 脱敏搜索结果
     */
    public Map<String, Object> desensitize(Map<String, Object> source) {
        Map<String, Object> result = new HashMap<>(source);
        
        // 1. 移除敏感字段
        for (String field : SENSITIVE_FIELDS) {
            result.remove(field);
        }
        
        // 2. 脱敏文本字段
        for (String field : List.of("errorMsg", "promptPreview", "contentPreview")) {
            Object value = result.get(field);
            if (value instanceof String) {
                result.put(field, desensitizeText((String) value));
            }
        }
        
        return result;
    }
    
    /**
     * 脱敏文本
     */
    private String desensitizeText(String text) {
        String result = text;
        
        for (Map.Entry<String, Pattern> entry : DESENSITIZATION_PATTERNS.entrySet()) {
            Pattern pattern = entry.getValue();
            Matcher matcher = pattern.matcher(result);
            
            if (matcher.find()) {
                switch (entry.getKey()) {
                    case "phone" -> result = result.replaceAll(
                        "\\b(1[3-9]\\d{9})\\b", "[PHONE_REDACTED]");
                    case "idCard" -> result = result.replaceAll(
                        "\\b([0-9]{6}[0-9]{8}|[0-9]{18})\\b", "[ID_REDACTED]");
                    case "apiKey" -> result = result.replaceAll(
                        "(Authorization|apiKey|Bearer)\\s*[:=]\\s*([\\w-]+)", "$1: [REDACTED]");
                }
            }
        }
        
        return result;
    }
    
    /**
     * 审计日志脱敏（更严格）
     */
    public Map<String, Object> desensitizeForAudit(Map<String, Object> source) {
        Map<String, Object> result = desensitize(source);
        
        // 审计日志额外脱敏：隐藏用户标识
        if (!result.containsKey("userId") || ((String) result.get("userId")).equals("system")) {
            // 保留系统操作的用户 ID
        } else {
            result.put("userId", "[USER_MASKED]");
        }
        
        return result;
    }
}
```

**验收标准**：
- [ ] 脱敏规则已实现。
- [ ] 测试 100 条敏感样本，命中率 100%。
- [ ] 无脱敏漏洞。

---

### 任务 B7：聚合计算与响应转换（0.5 人·天）
**责任人**：后端 ES 集成开发  
**核心类**：

```java
// service/impl/LlmLogQueryServiceImpl.java
@Service
public class LlmLogQueryServiceImpl implements LlmLogQueryService {
    
    @Autowired
    private RestHighLevelClient esClient;
    
    @Autowired
    private EsQueryBuilder queryBuilder;
    
    @Autowired
    private EsDesensitizer desensitizer;
    
    @Autowired
    private LogQueryCache queryCache;
    
    @Override
    public TimeSeriesResponse timeseries(
        LogQueryFilter filter, 
        String metric, 
        String interval, 
        List<String> groupBy
    ) {
        // 1. 缓存查询
        String cacheKey = generateCacheKey("timeseries", filter, metric, interval);
        TimeSeriesResponse cached = queryCache.get(cacheKey, TimeSeriesResponse.class);
        if (cached != null) {
            return cached;
        }
        
        try {
            // 2. 构造查询
            SearchRequest request = queryBuilder.buildTimeSeriesDsl(filter, metric, interval, groupBy);
            
            // 3. 执行查询
            SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
            
            // 4. 解析聚合结果
            ParsedDateHistogram histogram = response.getAggregations()
                .get("time_buckets");
            
            List<TimeSeriesResponse.TimeBucket> buckets = new ArrayList<>();
            for (Histogram.Bucket bucket : histogram.getBuckets()) {
                Long ts = ((ZonedDateTime) bucket.getKey()).toInstant().toEpochMilli();
                
                // 获取指标值
                Long value = getMetricValueFromBucket(bucket, metric);
                
                buckets.add(TimeSeriesResponse.TimeBucket.builder()
                    .ts(new Date(ts))
                    .value(value)
                    .build());
            }
            
            TimeSeriesResponse result = TimeSeriesResponse.builder()
                .buckets(buckets)
                .build();
            
            // 5. 缓存结果（5 分钟）
            queryCache.put(cacheKey, result, Duration.ofMinutes(5));
            
            return result;
        } catch (IOException e) {
            throw new EsQueryException("Failed to query timeseries", e);
        }
    }
    
    @Override
    public PageResponse<LogRecord> records(LogQueryFilter filter) {
        try {
            SearchRequest request = queryBuilder.buildRecordsDsl(filter);
            SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
            
            List<LogRecord> records = new ArrayList<>();
            for (SearchHit hit : response.getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                
                // 脱敏
                source = desensitizer.desensitize(source);
                
                LogRecord record = LogRecord.builder()
                    .eventTime((String) source.get("eventTime"))
                    .traceId((String) source.get("traceId"))
                    .status((String) source.get("status"))
                    .errorCode((String) source.get("errorCode"))
                    .latencyMs(((Number) source.get("latencyMs")).longValue())
                    .build();
                
                records.add(record);
            }
            
            return PageResponse.of(
                response.getHits().getTotalHits().value,
                (int) Math.ceil((double) response.getHits().getTotalHits().value / filter.getPageSize()),
                records
            );
        } catch (IOException e) {
            throw new EsQueryException("Failed to query records", e);
        }
    }
    
    @Override
    public TraceDetailResponse traceDetail(String traceId) {
        try {
            SearchRequest request = queryBuilder.buildTraceDsl(traceId);
            SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
            
            List<TraceDetailResponse.TimelineEvent> timeline = new ArrayList<>();
            for (SearchHit hit : response.getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                source = desensitizer.desensitize(source);
                
                timeline.add(TraceDetailResponse.TimelineEvent.builder()
                    .ts((String) source.get("eventTime"))
                    .eventType((String) source.get("eventType"))
                    .status((String) source.get("status"))
                    .build());
            }
            
            return TraceDetailResponse.builder()
                .traceId(traceId)
                .timeline(timeline)
                .build();
        } catch (IOException e) {
            throw new EsQueryException("Failed to query trace", e);
        }
    }
    
    private Long getMetricValueFromBucket(Histogram.Bucket bucket, String metric) {
        return switch (metric) {
            case "qps" -> {
                Aggregations aggs = bucket.getAggregations();
                ParsedValueCount count = aggs.get("qps_count");
                yield count.getValue();
            }
            case "errorRate" -> {
                // 返回错误率（百分比）
                yield 0L;  // 简化示例
            }
            case "p95Latency" -> {
                Aggregations aggs = bucket.getAggregations();
                ParsedPercentiles percentiles = aggs.get("latency_percentiles");
                yield (long) percentiles.getPercentile(95.0);
            }
            default -> 0L;
        };
    }
    
    private String generateCacheKey(String method, LogQueryFilter filter, String... args) {
        return String.format("%s:%s:%s:%s:%s",
            method,
            filter.getStartAt(),
            filter.getEndAt(),
            String.join("_", args),
            filter.getTenantId()
        );
    }
}
```

**验收标准**：
- [ ] 4 个查询方法已实现。
- [ ] 结果脱敏生效。
- [ ] 缓存策略已应用。
- [ ] 无查询异常。

---

## 前端 Team（1 人·天）

### 任务 F3：图表组件实现与数据联动（1 人·天）
**责任人**：前端图表开发  
**核心组件**：

```vue
<!-- src/views/LlmObservability/OverviewPage.vue -->
<template>
  <div class="overview-page">
    <!-- KPI 卡片 -->
    <div class="kpi-row">
      <KpiCard
        title="QPS"
        :value="kpiData.qps"
        unit="req/s"
        :trend="kpiData.qpsTrend"
      />
      <KpiCard
        title="错误率"
        :value="kpiData.errorRate"
        unit="%"
        :trend="kpiData.errorRateTrend"
      />
      <KpiCard
        title="P95 延迟"
        :value="kpiData.p95Latency"
        unit="ms"
        :trend="kpiData.p95Trend"
      />
    </div>
    
    <!-- 趋势图表 -->
    <div class="charts-grid">
      <LineChart
        title="请求趋势"
        :data="requestTrendData"
        :loading="loading"
      />
      <LineChart
        title="错误趋势"
        :data="errorTrendData"
        :loading="loading"
      />
    </div>
    
    <!-- 自动刷新 -->
    <div class="refresh-control">
      <el-radio-group v-model="refreshInterval">
        <el-radio label="15">15s</el-radio>
        <el-radio label="30">30s</el-radio>
        <el-radio label="60">60s</el-radio>
      </el-radio-group>
      <el-button @click="refreshNow" :loading="loading">刷新</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useLlmLogQuery } from '@/composables/useLlmLogQuery'
import { useLlmLogFilter } from '@/composables/useLlmLogFilter'

const { loading, queryTimeseries } = useLlmLogQuery()
const { filter } = useLlmLogFilter()

const kpiData = ref({
  qps: 0,
  errorRate: 0,
  p95Latency: 0
})
const requestTrendData = ref([])
const errorTrendData = ref([])
const refreshInterval = ref('30')
let refreshTimer: any = null

const loadData = async () => {
  loading.value = true
  try {
    const qpsResult = await queryTimeseries({
      filter: filter.value,
      metric: 'qps',
      interval: '1m',
      groupBy: []
    })
    
    // 更新 KPI
    if (qpsResult.buckets.length > 0) {
      const latest = qpsResult.buckets[qpsResult.buckets.length - 1]
      kpiData.value.qps = latest.value || 0
    }
    
    // 更新趋势图
    requestTrendData.value = qpsResult.buckets.map(b => ({
      time: b.ts,
      value: b.value
    }))
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

const refreshNow = () => {
  loadData()
}

const setupAutoRefresh = () => {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = setInterval(() => {
    loadData()
  }, Number(refreshInterval.value) * 1000)
}

onMounted(() => {
  loadData()
  setupAutoRefresh()
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.overview-page {
  padding: 20px;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.refresh-control {
  display: flex;
  gap: 16px;
  align-items: center;
}
</style>
```

**验收标准**：
- [ ] KPI 卡片正常渲染。
- [ ] 折线图数据联动。
- [ ] 自动刷新功能正常。
- [ ] 加载状态提示。

---

## 跨功能同步

### 下午 2 点技术同步
- 后端：DSL 模板、脱敏示例、查询性能基准
- 前端：图表集成、缓存策略
- 遗留问题：... (如有)

---

## Day2 End-of-Day 验收清单
- [ ] ES 查询全链路就位（DSL + 脱敏 + 聚合）
- [ ] 前端图表与数据联动
- [ ] 缓存策略生效（查询延迟 < 500ms）
- [ ] 脱敏验证通过（100 条样本 100% 命中）
- [ ] 无 P0 阻塞问题
- [ ] Day3 开工物料准备（告警规则测试用例）
