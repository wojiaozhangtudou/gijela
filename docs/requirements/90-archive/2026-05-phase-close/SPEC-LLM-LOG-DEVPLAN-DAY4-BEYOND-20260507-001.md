# SPEC-LLM-LOG-DEVPLAN-DAY4+-20260507-001

> 目的：Day4 及之后规划（全量上线、监控优化、数据探索）。

## 阶段总览
- **Duration**: Day4+ = 1 周持续运维 + 数据沉淀
- **Team**: 后端 1 人、前端 0.5 人、运维 0.5 人、数据分析 0.5 人
- **Goal**: 全量上线验证、告警微调、数据积累、性能基准建立
- **依赖**：Day3 灰度上线成功、无 P0 阻塞

---

## 第一阶段：全量上线（Day4）

### O3：从灰度（10%）扩大到全量（100%）

**时间线**：
```
Day3 18:00   -> 灰度 10% 开启，进入观测期
Day3 20:00   -> 灰度 30%（如无异常，自动扩大）
Day4 09:00   -> 全量 100%（发布公告 + 变更管理）
Day4 12:00   -> 第一轮稳定性检查
```

**扩大策略**：
```bash
# Stage 1: 10% -> 30%
kubectl patch configmap llm-observability-enabled \
  --type merge -p '{"data":{"grayRatio":"30"}}'

# Stage 2: 30% -> 50%
kubectl patch configmap llm-observability-enabled \
  --type merge -p '{"data":{"grayRatio":"50"}}'

# Stage 3: 50% -> 100%
kubectl patch configmap llm-observability-enabled \
  --type merge -p '{"data":{"grayRatio":"100"}}'
```

**每个阶段监控 30 分钟**：
- 错误率 < 1%（vs 基线 0.1%）
- 查询延迟 P95 < 500ms
- 告警误报率 < 5%

**回滚触发条件**（任一满足立即回滚）：
- 查询延迟 P95 > 1000ms（连续 5 分钟）
- 错误率 > 5%（连续 3 分钟）
- P0 告警 > 3 个/小时（假告警）

### B10：灰度数据分析与调优

**任务**（后端 0.5 人·天）：

```java
/**
 * 灰度期间的关键指标收集与分析
 */
@Component
public class GrayPhaseAnalytics {
    
    @Autowired
    private RestHighLevelClient esClient;
    
    /**
     * 1. 查询性能基准（获取 P50、P95、P99）
     */
    public QueryPerformanceBaseline getPerformanceBaseline() throws IOException {
        SearchRequest request = new SearchRequest("llm-access-*");
        
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.aggregation(
            AggregationBuilders
                .percentiles("latency_percentiles")
                .field("latencyMs")
                .percentiles(50, 95, 99)
        );
        
        request.source(sourceBuilder);
        
        SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
        Percentiles percentiles = response.getAggregations().get("latency_percentiles");
        
        return QueryPerformanceBaseline.builder()
            .p50(percentiles.percentile(50.0))
            .p95(percentiles.percentile(95.0))
            .p99(percentiles.percentile(99.0))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 2. 告警假阳率分析
     */
    public AlertAccuracyMetrics analyzeAlertAccuracy() {
        // 统计：触发的告警中有多少被人工确认为假告警
        QueryWrapper<LlmAlertEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("created_at", ">=", LocalDateTime.now().minusHours(1));
        
        List<LlmAlertEvent> events = eventMapper.selectList(wrapper);
        
        long falsPositives = events.stream()
            .filter(e -> "false_positive".equals(e.getReviewStatus()))
            .count();
        
        return AlertAccuracyMetrics.builder()
            .totalAlerts(events.size())
            .falsePositives(falsPositives)
            .accuracyRate((double) (events.size() - falsPositives) / events.size() * 100)
            .build();
    }
    
    /**
     * 3. 脱敏覆盖度检查
     */
    public DesensitizationCoverage checkDesensitizationCoverage() throws IOException {
        SearchRequest request = new SearchRequest("llm-runtime-*");
        
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.aggregation(
            AggregationBuilders.filter("has_apikey",
                QueryBuilders.existsQuery("apiKey"))
                .subAggregation(
                    AggregationBuilders.filter("redacted_apikey",
                        QueryBuilders.wildcardQuery("apiKey", "sk-****"))
                )
        );
        
        SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
        
        Filter hasApikey = response.getAggregations().get("has_apikey");
        Filter redactedApikey = hasApikey.getAggregations().get("redacted_apikey");
        
        return DesensitizationCoverage.builder()
            .totalRecords(hasApikey.getDocCount())
            .redactedRecords(redactedApikey.getDocCount())
            .coverageRate((double) redactedApikey.getDocCount() / hasApikey.getDocCount() * 100)
            .build();
    }
}
```

**产出**：
- [ ] 查询性能基准：P95 < 500ms （确认可支撑全量）
- [ ] 告警准确率 > 95%
- [ ] 脱敏覆盖度 = 100%

---

## 第二阶段：稳定期运维（Day5-Day7）

### O4：生产监控与告警微调

**告警优化**（运维 0.5 人·天）：

```yaml
# 基于灰度数据反馈调整告警阈值
灰度期间测得的指标:
  平均 QPS: 100/min
  P95 延迟: 450ms（vs 预期 500ms）
  错误率: 0.2%（vs 预期 1%）
  工具失败率: 2%（vs 预期 5%）
  首 Token 平均: 800ms

调整建议:
  1. P95 延迟告警阈值: 3000ms -> 1500ms（基于基准的 3 倍）
  2. 工具失败告警阈值: 10% -> 5%（提高灵敏度）
  3. 新增告警: 首 Token P95 > 2000ms（critical）
  4. 新增告警: ES 写入延迟 > 1000ms（warning）

新告警规则:
  - 首 Token P95 超时告警（新）
  - ES 写入延迟告警（新）
  - 日志采集丢失率 > 0.1%（新）
```

**DB 迁移脚本**（运维执行）：
```sql
-- V10__llm_alert_rules_tuning.sql
UPDATE llm_alert_rule SET threshold = 1500 WHERE name = 'P95 延迟告警';
UPDATE llm_alert_rule SET threshold = 5 WHERE name = '工具调用失败告警';

INSERT INTO llm_alert_rule (name, metric_type, condition, threshold, window_minutes, severity, notify_channels, cooldown_minutes, enabled, tenant_id, created_at, updated_at)
VALUES 
  ('首 Token P95 超时告警', 'firstTokenP95', 'gt', 2000, 10, 'critical', 'wechat,dingding,email', 15, 1, 'default', NOW(), NOW()),
  ('ES 写入延迟告警', 'esWriteLatency', 'gt', 1000, 5, 'warning', 'wechat,email', 10, 1, 'default', NOW(), NOW()),
  ('日志采集丢失率', 'collectionLossRate', 'gt', 0.1, 5, 'warning', 'wechat', 10, 1, 'default', NOW(), NOW());
```

### B11：查询缓存与性能优化

**任务**（后端 0.5 人·天）：

```java
/**
 * 基于灰度观测结果的缓存策略优化
 */
@Component
public class LlmLogQueryCacheOptimizer {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    /**
     * 缓存命中率分析
     */
    public CacheHitRateReport analyzeCacheHitRate() {
        // 统计：最近 1 小时内的缓存命中率
        String key = "cache:query:hitrate:1h";
        String hitCount = redisTemplate.opsForValue().get(key + ":hits");
        String missCount = redisTemplate.opsForValue().get(key + ":misses");
        
        long hits = Long.parseLong(hitCount != null ? hitCount : "0");
        long misses = Long.parseLong(missCount != null ? missCount : "0");
        
        double hitRate = (double) hits / (hits + misses) * 100;
        
        return CacheHitRateReport.builder()
            .hitCount(hits)
            .missCount(misses)
            .hitRate(hitRate)
            .recommendation(hitRate < 30 ? "降低缓存过期时间，增加热数据覆盖" : "缓存命中率良好")
            .build();
    }
    
    /**
     * 热查询识别与预热
     */
    public List<String> identifyHotQueries() {
        // 获取最频繁的 Top 10 查询模式
        Set<String> hotQueries = redisTemplate.opsForZSet()
            .reverseRange("stats:query:frequency", 0, 9);
        
        return new ArrayList<>(hotQueries);
    }
    
    /**
     * 针对热查询的缓存预热
     */
    public void preWarmCacheForHotQueries() {
        List<String> hotQueries = identifyHotQueries();
        
        for (String queryPattern : hotQueries) {
            // 解析查询模式，重新执行 ES 查询，缓存结果
            TimeSeriesRequest request = TimeSeriesRequest.fromPattern(queryPattern);
            TimeSeriesResponse response = queryService.timeseries(request);
            
            String cacheKey = "cache:query:" + queryPattern;
            redisTemplate.opsForValue().set(
                cacheKey, 
                JsonUtil.toJson(response),
                5,  // 5 分钟过期
                TimeUnit.MINUTES
            );
        }
    }
}
```

**缓存策略调整**：
```java
// Day1 定义的缓存策略（5 分钟固定）需要根据灰度数据动态调整
public class QueryCacheConfig {
    
    /**
     * 根据查询类型动态设置缓存时间
     */
    public int calculateCacheTTL(TimeSeriesRequest request) {
        int interval = parseInterval(request.getInterval());
        
        // 规则：缓存时间 = interval * 2
        // - 1m 查询 -> 缓存 2m（对于实时性要求高的查询）
        // - 30m 查询 -> 缓存 1h（对于汇总查询，可以更久）
        if (interval <= 5) {
            return 2;   // 2 分钟
        } else if (interval <= 30) {
            return 30;  // 30 分钟
        } else {
            return 120; // 2 小时
        }
    }
}
```

### F4：前端数据可视化深化

**任务**（前端 0.5 人·天）：

```vue
<!-- src/views/dashboard/OptimizationPage.vue -->
<!-- 展示灰度期间的优化建议与性能趋势 -->

<template>
  <div class="optimization-container">
    <!-- 1. 基准指标卡片 -->
    <el-row :gutter="20">
      <el-col :xs="24" :sm="12" :md="6">
        <MetricCard
          title="P95 延迟"
          :value="`${performanceBaseline.p95}ms`"
          unit="相比预期 ↓12%"
          color="success"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <MetricCard
          title="查询命中率"
          :value="`${cacheHitRate.toFixed(2)}%`"
          unit="较昨日 ↑8%"
          color="info"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <MetricCard
          title="告警准确率"
          :value="`${alertAccuracy.toFixed(2)}%`"
          unit="假阳 3 个"
          color="warning"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <MetricCard
          title="脱敏覆盖"
          :value="`${desensitizationCoverage.toFixed(2)}%`"
          unit="无缺失"
          color="success"
        />
      </el-col>
    </el-row>

    <!-- 2. 性能趋势图 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>查询延迟分布（灰度期间）</span>
              <el-radio-group v-model="latencyChartTimeRange" size="small">
                <el-radio-button label="1h">1小时</el-radio-button>
                <el-radio-button label="6h">6小时</el-radio-button>
                <el-radio-button label="1d">1天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <v-chart :option="latencyDistributionChartOption" style="height: 300px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>告警准确率趋势</span>
              <el-button type="text" icon="Refresh" @click="refreshAlertTrend" />
            </div>
          </template>
          <v-chart :option="alertAccuracyTrendOption" style="height: 300px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 3. 优化建议 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24">
        <el-card>
          <template #header>
            <span>优化建议（基于灰度数据）</span>
          </template>
          <el-table :data="optimizationSuggestions" stripe>
            <el-table-column prop="title" label="优化项" width="200" />
            <el-table-column prop="currentValue" label="当前值" width="150" />
            <el-table-column prop="recommendedValue" label="建议值" width="150" />
            <el-table-column prop="impact" label="预期收益" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  text
                  size="small"
                  @click="applyOptimization(row.id)"
                >
                  应用
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import VChart from 'vue-echarts';
import { useQuery } from '@/composables/useQuery';
import { queryApi } from '@/api/llm-logs';

const { executeQuery, loading } = useQuery();

// 状态
const performanceBaseline = ref({
  p50: 200,
  p95: 450,
  p99: 800,
});

const cacheHitRate = ref(42.5);
const alertAccuracy = ref(97.2);
const desensitizationCoverage = ref(100);
const latencyChartTimeRange = ref('1h');

const optimizationSuggestions = ref([
  {
    id: 1,
    title: 'P95 延迟告警阈值',
    currentValue: '3000ms',
    recommendedValue: '1500ms',
    impact: '告警更及时，避免延迟恶化',
  },
  {
    id: 2,
    title: '缓存预热频率',
    currentValue: '5分钟',
    recommendedValue: '3分钟',
    impact: '热查询命中率 +15%',
  },
  {
    id: 3,
    title: '告警冷却时间',
    currentValue: '10分钟',
    recommendedValue: '5分钟',
    impact: '及时发现短期问题',
  },
]);

// 图表配置
const latencyDistributionChartOption = computed(() => ({
  title: {
    text: '查询延迟分布',
  },
  xAxis: {
    type: 'category',
    data: ['<100ms', '100-200ms', '200-300ms', '300-500ms', '500-1000ms', '>1000ms'],
  },
  yAxis: {
    type: 'value',
  },
  series: [
    {
      data: [45, 120, 180, 200, 50, 5],
      type: 'bar',
      itemStyle: {
        color: '#67C23A',
      },
    },
  ],
  tooltip: {
    trigger: 'axis',
  },
}));

const alertAccuracyTrendOption = computed(() => ({
  title: {
    text: '告警准确率趋势',
  },
  xAxis: {
    type: 'time',
  },
  yAxis: {
    type: 'value',
    max: 100,
  },
  series: [
    {
      name: '准确率',
      type: 'line',
      smooth: true,
      data: [
        ['2026-05-07 14:00:00', 92],
        ['2026-05-07 15:00:00', 94],
        ['2026-05-07 16:00:00', 96],
        ['2026-05-07 17:00:00', 97],
        ['2026-05-07 18:00:00', 97.2],
      ],
    },
  ],
  tooltip: {
    trigger: 'axis',
  },
}));

// 方法
const refreshAlertTrend = async () => {
  // 重新加载告警趋势数据
};

const applyOptimization = async (id: number) => {
  const suggestion = optimizationSuggestions.value.find(s => s.id === id);
  // 调用后端 API 应用优化
  await queryApi.applyOptimization(id, suggestion.recommendedValue);
  ElMessage.success('优化已应用');
};

onMounted(async () => {
  // 加载基准指标
  const baseline = await queryApi.getPerformanceBaseline();
  performanceBaseline.value = baseline.data;
});
</script>

<style scoped>
.optimization-container {
  padding: 20px;
}
</style>
```

### Q2：灰度缺陷修复与验证

**任务**（QA 0.5 人·天）：
- [ ] 收集灰度期间的 bug 反馈
- [ ] P0 缺陷（阻塞完全上线）立即修复 + 灰度重新验证
- [ ] P1 缺陷记录到 backlog，Day7+ 处理
- [ ] 更新 E2E 测试用例（补充灰度发现的边界情况）

---

## 第三阶段：持续优化（Day7+）

### 后续工作项

| 优先级 | 工作项 | 所有者 | 时间 | 目标 |
|--------|--------|--------|------|------|
| P0 | 性能基准建立 | 后端/运维 | Day7 | 确立稳定期性能指标（P95, QPS 等） |
| P1 | 告警规则微调 | 运维 | Day7+ | 基于 1 周数据，调整 3-5 个规则 |
| P1 | 数据积累分析 | 数据分析 | Day7+ | 生成"LLM 日志使用概览"（周度） |
| P2 | 缓存策略优化 | 后端 | Day10+ | 基于命中率，调整 TTL 策略 |
| P2 | 脱敏规则扩展 | 后端 | Day14+ | 新增正则规则（用户手机号等） |
| P3 | 国际化支持 | 前端 | Day14+ | 看板支持中英文切换 |
| P3 | 深度分析功能 | 前端 | Day21+ | 支持自定义查询 + 报表导出 |

### B12：周度性能报告生成（定期任务）

```java
@Component
public class WeeklyPerformanceReportGenerator {
    
    @Scheduled(cron = "0 0 9 ? * MON")  // 每周一 9:00
    public void generateWeeklyReport() {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusWeeks(1);
        
        PerformanceReport report = PerformanceReport.builder()
            .period("Weekly")
            .startTime(startTime)
            .endTime(endTime)
            .avgQps(queryAvgQps(startTime, endTime))
            .p95Latency(queryP95Latency(startTime, endTime))
            .errorRate(queryErrorRate(startTime, endTime))
            .alertTriggerCount(queryAlertTriggerCount(startTime, endTime))
            .falseAlertRate(queryFalseAlertRate(startTime, endTime))
            .build();
        
        // 保存到数据库
        reportMapper.insert(report);
        
        // 发送到钉钉或邮件
        notificationClient.sendReport(report);
        
        log.info("Weekly report generated: {}", report);
    }
}
```

---

## 验收清单（Day4+ 总体）

### Day4 验收
- [ ] 灰度从 10% 扩大到 100%
- [ ] 无 P0 新 bug
- [ ] 查询性能基准确立
- [ ] 告警准确率 >= 95%

### Day5-Day7 验收
- [ ] 告警规则已微调
- [ ] 缓存命中率 > 40%
- [ ] 前端优化建议页面上线
- [ ] 周度报告自动生成

### Day7+ 持续验收
- [ ] 月度性能报告自动生成
- [ ] 告警规则人工误报率 < 3%
- [ ] 新功能需求纳入 backlog

---

## 总体项目里程碑

```
Day1   ✅ 基础设施 + API 框架
Day2   ✅ ES 查询集成 + 前端集成
Day3   ✅ 告警上线 + 灰度验证
Day4   ⏳ 全量上线 + 性能基准
Day5-7 ⏳ 稳定期 + 微调优化
Day7+  ⏳ 持续运维 + 数据沉淀

总耗时: 2 周规划 + 1 周开发 + 1 周稳定 = **4 周上线到稳定**
