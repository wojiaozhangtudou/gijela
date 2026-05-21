# gijela-core-llm-sdk-openai-compatible

## 模块说明
- 提供 OpenAI 兼容协议实现，统一走 `OkHttp`。
- 支持：
  - 同步 `chat()`
  - 流式 `stream()`（SSE）
  - Function Calling（工具执行）
  - 自动续跑（工具结果回传后二次补全）

## 最小依赖
- 依赖 `gijela-core-llm-sdk-core`
- `okhttp` / `jackson` / `slf4j`

## 快速开始

### 1) 构建客户端
```java
OkHttpClient okHttpClient = new OkHttpClient();
OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties(
    "https://your-openai-compatible-host/v1",
    "sk-xxx",
    "gpt-4o-mini",
    3,
    60,
    65
);
OpenAiCompatibleClient client = new OpenAiCompatibleClient(okHttpClient, properties);
```

### 2) 同步调用
```java
ChatRequest request = new ChatRequest(
    null,
    List.of(new ChatMessage("user", "你好")),
    0.2,
    256,
    Map.of()
);
ChatResponse response = client.chat(request);
```

### 3) 流式调用
```java
AutoCloseable closeable = client.stream(request, new LlmEventListener() {
    @Override
    public void onEvent(LlmEvent event) {
        // 事件类型：START / DELTA / TOOL_CALL / TOOL_RESULT / ERROR / DONE
    }

    @Override
    public void onError(Throwable throwable) {
        // 流式链路异常
    }
});

// 业务结束时务必关闭
closeable.close();
```

## Function Calling 用法
通过 `ChatRequest.metadata` 传入工具定义与执行器：

建议优先复用常量类，避免字符串拼写错误：
- `OpenAiMetadataKeys`

- `tools`：OpenAI 兼容 `tools` 数组
- `tool_choice`：OpenAI 兼容 `tool_choice`
- `toolExecutor`：`ToolExecutor` 实例
- `toolContext`：可选，`ToolContext`
- `tenantId`：可选，未提供 `toolContext` 时用于构造默认上下文
- `requestId`：可选，用于审计关联
- `autoToolContinue`：可选，默认 `true`
  - `true`：工具执行后自动续跑
  - `false`：只返回首轮结果，不自动二次补全
- `metricsCollector`：可选，`LlmMetricsCollector`
    - 成功记录 `recordSuccess(model, latencyMs)`
    - 失败记录 `recordFailure(model, errorCode)`
- `auditLogger`：可选，`LlmAuditLogger`
    - 记录 `record(tenantId, requestId, action, result)`
- `observationInterceptor`：可选，`LlmObservationInterceptor`
    - 控制耗时起止点（默认使用内置实现）
- `budgetEventPublisher`：可选，`BudgetEventPublisher`
    - 失败时发布预算事件（`tenantId/level/message`）
- `budgetEventLevel`：可选，预算事件级别（默认 `WARN`）

示例：
```java
ToolExecutor executor = (call, context) -> new ToolResult(
    call.id(),
    true,
    Map.of("echo", call.arguments()),
    null
);

Map<String, Object> metadata = Map.of(
    OpenAiMetadataKeys.TOOLS, List.of(/* OpenAI tools */),
    OpenAiMetadataKeys.TOOL_CHOICE, "auto",
    OpenAiMetadataKeys.TOOL_EXECUTOR, executor,
    OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, true
);

ChatRequest req = new ChatRequest(
    null,
    List.of(new ChatMessage("user", "帮我调用工具")),
    0.2,
    256,
    metadata
);
```

## 运行时选项（推荐实践）
- 客户端内部会将 `metadata` 解析为类型化运行时选项（`OpenAiRuntimeOptions`），统一承载：
    - 工具执行：`toolExecutor` / `toolContext` / `autoToolContinue`
    - 可观测：`metricsCollector` / `auditLogger` / `observationInterceptor`
    - 治理扩展：`tenantId` / `requestId` / `budgetEventPublisher` / `budgetEventLevel`
- 推荐调用方始终使用 `OpenAiMetadataKeys` 常量写入 `metadata`，避免 key 拼写错误。
- 当未显式提供 `toolContext` 时，客户端会基于 `tenantId + metadata attributes` 构造默认 `ToolContext`。

示例（显式传入 `toolContext`，优先使用自定义上下文）：
```java
ToolContext context = new ToolContext("tenant-custom", Map.of("scope", "finance"));

Map<String, Object> metadata = Map.of(
    OpenAiMetadataKeys.TOOL_EXECUTOR, executor,
    OpenAiMetadataKeys.TOOL_CONTEXT, context,
    OpenAiMetadataKeys.TENANT_ID, "tenant-fallback"
);
```

示例（关闭自动续跑，仅执行首轮工具调用）：
```java
Map<String, Object> metadata = Map.of(
    OpenAiMetadataKeys.TOOL_EXECUTOR, executor,
    OpenAiMetadataKeys.AUTO_TOOL_CONTINUE, false,
    OpenAiMetadataKeys.TENANT_ID, "tenant-001",
    OpenAiMetadataKeys.REQUEST_ID, "req-001"
);
```

## 注意事项
- 目前为单一 OpenAI 兼容入口（固定 `baseUrl/apiKey/model`）。
- HTTP 客户端禁止混用，统一 `OkHttp`。
- 流式场景请确保在结束时调用 `close()`，避免连接泄漏。
