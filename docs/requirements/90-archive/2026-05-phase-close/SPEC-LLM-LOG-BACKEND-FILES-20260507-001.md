# SPEC-LLM-LOG-BACKEND-FILES-20260507-001

> 目的：给后端聚合查询服务提供文件级落地清单（基于 gijela-core-chat 模块）。

## 1. 目录结构
```
gijela-core/gijela-core-chat/src/main/java/com/gijela/morpheus/chat/
├── domain/
│   ├── dto/
│   │   ├── llmlog/
│   │   │   ├── LogQueryFilter.java               # 统一查询条件
│   │   │   ├── TimeSeriesRequest.java
│   │   │   ├── TimeSeriesResponse.java
│   │   │   ├── TopNRequest.java
│   │   │   ├── TopNResponse.java
│   │   │   ├── DistributionRequest.java
│   │   │   ├── DistributionResponse.java
│   │   │   ├── TraceDetailResponse.java
│   │   │   └── ExportRequest.java
│   │   └── ...（现有）
│   └── vo/
│       ├── llmlog/
│       │   ├── LogRecord.java
│       │   ├── TimeBucket.java
│       │   └── AlertRuleVO.java
│       └── ...（现有）
├── service/
│   ├── LlmLogQueryService.java                  # 查询接口定义
│   ├── LlmAlertService.java                     # 告警规则服务
│   └── impl/
│       ├── LlmLogQueryServiceImpl.java
│       ├── LlmAlertServiceImpl.java
│       └── ...（现有）
├── mapper/
│   ├── LlmAlertEventMapper.java                 # 告警事件表
│   └── ...（现有）
├── controller/
│   ├── LlmLogQueryController.java               # REST API 端点
│   └── LlmAlertController.java
├── infra/
│   ├── es/
│   │   ├── EsClientConfig.java                  # ES 客户端配置
│   │   ├── EsQueryBuilder.java                  # DSL 模板化生成
│   │   ├── EsIndexManager.java                  # 索引管理
│   │   └── EsDesensitizer.java                  # 脱敏规则引擎
│   └── cache/
│       └── LogQueryCache.java                   # 缓存管理（可选 Redis）
└── ...（现有）
```

## 2. 核心文件详细说明

### 2.1 DTO 层

**LogQueryFilter.java**
```java
@Data
@Builder
public class LogQueryFilter {
    // 必填
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    
    // 业务过滤
    private String appCode = "chat";
    private String modelRoute;
    private List<String> provider;
    private List<String> model;
    private List<String> status;
    private List<String> errorCode;
    private List<String> toolName;
    
    // 上下文
    private String sessionId;
    private String traceId;
    private String userId;
    private String env = "prod";
    
    // 搜索
    private String keyword;
    
    // 分页与排序
    private Integer pageNo = 1;
    private Integer pageSize = 50;
    private String sortField = "@timestamp";
    private String sortOrder = "desc";
    
    // 内部注入（不接受前端传值）
    private String tenantId;
    
    public void validate() {
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("startAt/endAt required");
        }
        if (endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("endAt must after startAt");
        }
        Duration duration = Duration.between(startAt, endAt);
        if (duration.toHours() > 24) {
            throw new IllegalArgumentException("Max time window: 24h");
        }
        if (pageSize > 200) {
            throw new IllegalArgumentException("Max pageSize: 200");
        }
    }
}
```

**TimeSeriesResponse.java**
```java
@Data
public class TimeSeriesResponse {
    private List<TimeBucket> buckets;
    
    @Data
    @Builder
    public static class TimeBucket {
        private LocalDateTime ts;
        private Long value;
        private String group;  // 可选分组
        private Map<String, Object> metadata;  // 扩展字段
    }
}
```

**TopNResponse.java**
```java
@Data
public class TopNResponse {
    private List<TopNItem> items;
    
    @Data
    @Builder
    public static class TopNItem {
        private String key;
        private Long value;
        private Double ratio;  // 占比
    }
}
```

### 2.2 Service 接口

**LlmLogQueryService.java**
```java
public interface LlmLogQueryService {
    /**
     * 时序查询
     * @param filter 查询条件
     * @param metric 指标（qps/errorRate/p95Latency）
     * @param interval 时间粒度（1m/5m/1h）
     * @param groupBy 分组维度（可选）
     */
    TimeSeriesResponse timeseries(LogQueryFilter filter, String metric, String interval, List<String> groupBy);
    
    /**
     * TopN 查询
     */
    TopNResponse topn(LogQueryFilter filter, String metric, String dimension, Integer limit);
    
    /**
     * 分布查询（百分位）
     */
    DistributionResponse distribution(LogQueryFilter filter, String metric, List<String> dimensions);
    
    /**
     * 原始记录查询
     */
    PageResponse<LogRecord> records(LogQueryFilter filter);
    
    /**
     * Trace 下钻
     */
    TraceDetailResponse traceDetail(String traceId);
    
    /**
     * 导出
     */
    String export(LogQueryFilter filter, List<String> columns, String format);
}
```

**LlmAlertService.java**
```java
public interface LlmAlertService {
    /**
     * 触发告警规则（定时调用）
     */
    void triggerRules();
    
    /**
     * 查询告警规则列表
     */
    List<AlertRuleVO> listRules(Integer status);
    
    /**
     * 创建告警规则
     */
    Long createRule(AlertRuleRequest request);
    
    /**
     * 更新告警规则
     */
    void updateRule(Long ruleId, AlertRuleRequest request);
    
    /**
     * 查询告警历史
     */
    PageResponse<AlertEventVO> listAlertEvents(AlertEventQuery query);
}
```

### 2.3 Repository 接口

**LlmAlertEventMapper.java**
```java
@Mapper
public interface LlmAlertEventMapper extends BaseMapper<LlmAlertEvent> {
    /**
     * 按规则 ID 与时间窗查询
     */
    List<LlmAlertEvent> selectByRuleIdAndTimeRange(
        @Param("ruleId") Long ruleId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );
}
```

### 2.4 Controller 端点

**LlmLogQueryController.java**
```java
@RestController
@RequestMapping("/api/v1/llm-logs")
public class LlmLogQueryController {
    @PostMapping("/query/timeseries")
    public ApiResponse<TimeSeriesResponse> timeseries(@RequestBody TimeSeriesRequest request) {
        // 校验与租户注入
        LogQueryFilter filter = request.getFilter();
        filter.setTenantId(getCurrentTenantId());
        filter.validate();
        
        TimeSeriesResponse result = llmLogQueryService.timeseries(
            filter, request.getMetric(), request.getInterval(), request.getGroupBy()
        );
        return ApiResponse.success(result);
    }
    
    @PostMapping("/query/topn")
    public ApiResponse<TopNResponse> topn(@RequestBody TopNRequest request) {
        // 同上
    }
    
    @PostMapping("/query/distribution")
    public ApiResponse<DistributionResponse> distribution(@RequestBody DistributionRequest request) {
        // 同上
    }
    
    @PostMapping("/query/records")
    public ApiResponse<PageResponse<LogRecord>> records(@RequestBody LogQueryFilter filter) {
        // 同上
    }
    
    @GetMapping("/query/trace/{traceId}")
    public ApiResponse<TraceDetailResponse> traceDetail(@PathVariable String traceId) {
        // 校验 traceId 与租户隔离
        TraceDetailResponse result = llmLogQueryService.traceDetail(traceId);
        return ApiResponse.success(result);
    }
    
    @PostMapping("/query/export")
    public ApiResponse<String> export(@RequestBody ExportRequest request) {
        // 异步导出，返回 jobId
    }
}
```

### 2.5 ES 基础设施

**EsClientConfig.java**
```java
@Configuration
public class EsClientConfig {
    @Bean
    public RestClient restClient(LlmLogProperties props) {
        return RestClient.builder(
            new HttpHost(props.getEsHost(), props.getEsPort(), "http")
        ).build();
    }
    
    @Bean
    public RestHighLevelClient elasticsearchClient(RestClient restClient) {
        return new RestHighLevelClient(restClient);
    }
}
```

**EsQueryBuilder.java**
```java
@Component
public class EsQueryBuilder {
    /**
     * 构造 timeseries DSL
     */
    public SearchRequest buildTimeSeriesDsl(LogQueryFilter filter, String metric, String interval) {
        // 返回 SearchRequest，包含 range + aggregation + date_histogram
    }
    
    /**
     * 构造 topn DSL
     */
    public SearchRequest buildTopNDsl(LogQueryFilter filter, String metric, String dimension) {
        // 返回 SearchRequest
    }
    
    /**
     * 模板化查询条件
     */
    private BoolQuery buildFilterQuery(LogQueryFilter filter) {
        // 租户隔离、时间范围、业务条件
    }
}
```

## 3. 依赖与配置

**pom.xml** 添加：
```xml
<!-- Elasticsearch -->
<dependency>
    <groupId>org.elasticsearch.client</groupId>
    <artifactId>elasticsearch-rest-high-level-client</artifactId>
    <version>7.17.0</version>
</dependency>

<!-- 异步导出（可选） -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
    <version>3.3.0</version>
</dependency>
```

**application-dev.yml**：
```yaml
llm-log:
  es:
    host: localhost
    port: 9200
    username: elastic
    password: password
  alert:
    enabled: true
    schedule-cron: "0 */5 * * * ?"  # 每 5 分钟触发一次
```

## 4. 数据库迁移（MySQL）

**V9__llm_alert_tables.sql**：
```sql
CREATE TABLE `llm_alert_rule` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `name` VARCHAR(255) NOT NULL,
  `metric` VARCHAR(64) NOT NULL,
  `threshold` DECIMAL(10, 2),
  `condition` VARCHAR(64),  -- gt/lt/eq
  `window_minutes` INT DEFAULT 5,
  `enabled` TINYINT DEFAULT 1,
  `notify_channels` VARCHAR(255),
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE `llm_alert_event` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `rule_id` BIGINT,
  `triggered_at` DATETIME,
  `metric_value` DECIMAL(10, 2),
  `alert_level` VARCHAR(16),  -- info/warning/critical
  `status` VARCHAR(32),  -- open/ack/resolved
  `message` TEXT,
  `foreign_key(rule_id)` REFERENCES `llm_alert_rule`(`id`)
) ENGINE=InnoDB;
```

## 5. 验收清单（可开工条件）
- [ ] ES 配置已部署（模板 + ILM + pipeline）。
- [ ] ES client 依赖已引入。
- [ ] DTO/VO 类已定义。
- [ ] Service 接口已定义，impl 骨架已创建。
- [ ] Controller 端点已定义，暂返回 mock 数据。
- [ ] 告警数据表已创建。
- [ ] 前端 mock API 已就位（返回示例 JSON）。
