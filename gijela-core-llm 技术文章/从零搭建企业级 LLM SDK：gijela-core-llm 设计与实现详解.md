# 从零搭建企业级 LLM SDK：gijela-core-llm 设计与实现详解

> 本文基于真实项目 gijela（企业级 AI 管理后台）的 `gijela-core-llm` 模块，从架构设计、核心接口、HTTP 通信、Tool Calling、流式输出、可观测性等维度，系统梳理一套轻量且完整的 LLM SDK 的工程化落地路径。

---

## 一、为什么要自建 LLM SDK？

当前 Java 生态主流的 LLM 集成方式是 Spring AI，功能齐全但抽象层重、依赖链长。对于需要精细控制 HTTP 超时、自定义鉴权头、多租户路由、可观测性埋点的企业后台来说，Spring AI 反而带来了不必要的复杂度。

`gijela-core-llm` 的选择是：

- **零 Spring AI 依赖**，仅 OkHttp + Jackson 做 HTTP 层
- **接口先行**，`sdk-core` 定义抽象，`sdk-openai-compatible` 提供实现
- **可插拔观测**，`sdk-observability` 以可选组合方式接入，不污染主链路
- **MCP / Skill / Plugin** 三套扩展机制，支持 Tool Calling 和外部工具集成

---

## 二、模块全景

```
gijela-core-llm/
├── gijela-core-llm-sdk-core            # 核心接口与模型定义
├── gijela-core-llm-sdk-openai-compatible # OpenAI 兼容客户端实现
├── gijela-core-llm-sdk-observability   # 观测层（指标/审计/预算）
├── gijela-core-llm-sdk-skill           # Skill 注册表
├── gijela-core-llm-sdk-mcp             # MCP JSON-RPC 客户端
└── gijela-core-llm-sdk-plugin          # 插件状态机
```

各模块依赖关系如下：

```
sdk-openai-compatible
    ├── sdk-core          (接口契约)
    └── sdk-observability (可选观测)

sdk-mcp
    └── sdk-core

sdk-skill
    └── sdk-core
```

> **设计原则**：业务模块（如 `gijela-core-chat`）只依赖 `sdk-core` 接口，运行期注入具体实现，做到面向接口编程。

---

## 三、OpenAI 标准 RESTful API：基础知识

在介绍 `gijela-core-llm` 的实现之前，我们先了解其所适配的 OpenAI 标准 API 规范。下面通过纯 `curl` 命令演示各类 API 的调用方式，这些标准正是 SDK 后续实现的基础。

### 3.1 基础配置

假设部署了兼容 OpenAI API 的服务，以下示例中使用的参数：

```bash
# OpenAI 官方 API
API_BASE_URL="https://api.openai.com/v1"
API_KEY="sk-xxxxxxxxxxxxxxxxxxxx"

# 或国内兼容服务（例）
# API_BASE_URL="https://api.qwen.aliyun.com/v1"
# API_KEY="sk-xxxxxxx"

MODEL="gpt-4o"  # 或 "qwen-plus"、"claude-3.5-sonnet" 等
```

### 3.2 基础对话（同步）

**请求：**

```bash
curl -X POST "$API_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {
        "role": "system",
        "content": "你是一个有用的助手。"
      },
      {
        "role": "user",
        "content": "请用中文介绍一下 RESTful API 的设计原则。"
      }
    ],
    "temperature": 0.7,
    "max_tokens": 500
  }'
```

**响应 JSON：**

```json
{
  "id": "chatcmpl-8WPvW9wqd5fQD9Z8nW5d9X4p",
  "object": "chat.completion",
  "created": 1699564800,
  "model": "gpt-4o",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "RESTful API 的设计原则包括：\n\n1. **统一接口** - 基于资源的设计，使用标准 HTTP 方法 (GET, POST, PUT, DELETE)\n2. **无状态** - 每个请求包含所有必要的信息，服务器不维护客户端上下文\n3. **可缓存** - 正确使用 HTTP 缓存机制提高性能\n4. **分层系统** - 客户端不直接连接最终服务器，可通过中间层\n5. **按需代码** - 可选的，允许服务器扩展客户端功能\n6. **统一资源标识** - 每个资源有唯一 URI，与操作相分离"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 28,
    "completion_tokens": 156,
    "total_tokens": 184
  }
}
```

**关键字段说明：**

| 字段 | 说明 |
|------|------|
| `id` | 唯一请求标识，用于追踪和审计 |
| `created` | Unix 时间戳，响应生成时间 |
| `finish_reason` | `"stop"` = 正常结束，`"length"` = 达到 max_tokens，`"tool_calls"` = 调用工具 |
| `usage` | Token 用量统计，对应 embedding 和成本计算 |

### 3.3 流式对话（SSE）

**请求：** 添加 `"stream": true`

```bash
curl -X POST "$API_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "写一首短诗"}
    ],
    "stream": true
  }'
```

**流式响应 (SSE 格式)：**

```
data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"春"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"风"},"finish_reason":null}]}

data: [DONE]
```

### 3.4 Function Calling / Tool Calling

#### 3.4.1 第一轮请求（携带工具定义）

**请求 JSON：**

```bash
curl -X POST "$API_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {
        "role": "system",
        "content": "你是一个天气查询助手。用户问天气时，请调用 get_weather 工具。"
      },
      {
        "role": "user",
        "content": "北京今天的天气怎么样？"
      }
    ],
    "tools": [
      {
        "type": "function",
        "function": {
          "name": "get_weather",
          "description": "获取指定城市的天气信息",
          "parameters": {
            "type": "object",
            "properties": {
              "city": {
                "type": "string",
                "description": "城市名称，例如：北京、上海"
              },
              "days": {
                "type": "integer",
                "description": "查询天数，默认1天",
                "default": 1
              }
            },
            "required": ["city"]
          }
        }
      }
    ],
    "tool_choice": "auto"
  }'
```

**第一轮响应（模型要求调工具）：**

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "choices": [
    {
      "message": {
        "role": "assistant",
        "tool_calls": [
          {
            "id": "call_1a2b3c4d5e6f",
            "function": {
              "name": "get_weather",
              "arguments": "{\"city\": \"北京\", \"days\": 1}"
            }
          }
        ]
      },
      "finish_reason": "tool_calls"
    }
  ]
}
```

#### 3.4.2 第二轮请求（工具结果回传）

业务层执行工具后回传结果，发送第二轮请求（省略详细 curl，见第 10.4.2 节）。

### 3.5 文本向量化（Embedding）

**请求：**

```bash
curl -X POST "$API_BASE_URL/embeddings" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-small",
    "input": "如何在 Spring Boot 中配置 Redis 连接池？"
  }'
```

**响应 JSON：**

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.00231231, -0.00932729, ...]
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 11,
    "total_tokens": 11
  }
}
```

### 3.6 错误响应示例

**认证错误 (401)：**

```json
{
  "error": {
    "message": "Incorrect API key provided",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

**限流 (429)：**

```json
{
  "error": {
    "message": "Rate limit exceeded. Please retry after 60 seconds.",
    "type": "rate_limit_error",
    "code": "rate_limit_exceeded"
  }
}
```

> 本章只展示核心概念和基础调用。完整的 curl 示例、Bash 脚本、Postman 集合可见第十章附录（10.7-10.8 节）。

---

## 四、sdk-core：极简的接口契约

### 4.1 核心模型

```java
// 统一聊天请求
public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        Integer maxTokens,
        Map<String, Object> metadata   // 运行时扩展参数（tools、toolExecutor、tenantId 等）
) {}

// 统一聊天响应
public record ChatResponse(
        String id,
        String content,
        String finishReason,
        TokenUsage usage
) {}

// 消息
public record ChatMessage(String role, String content) {}

// Token 用量
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {}
```

这套模型刻意保持精简——**不绑定任何厂商字段**。厂商特有能力（如 `tool_choice`、`response_format`）通过 `metadata` 传递，由具体实现层解析。

### 4.2 两个客户端接口

```java
// 同步接口
public interface LlmClient {
    ChatResponse chat(ChatRequest request);
}

// 流式接口
public interface StreamingLlmClient {
    AutoCloseable stream(ChatRequest request, LlmEventListener listener);
}
```

`StreamingLlmClient` 返回 `AutoCloseable`，调用方可随时 `close()` 取消流式输出——这在用户主动中断对话时非常重要。

### 4.3 Tool 相关类型

```java
// 工具调用请求（模型输出）
public record ToolCall(String id, String name, String arguments) {}

// 工具执行结果（业务层回填）
public record ToolResult(
        String toolCallId,
        boolean success,
        Map<String, Object> result,
        String errorMessage
) {}

// 工具执行器（由业务方实现）
@FunctionalInterface
public interface ToolExecutor {
    ToolResult execute(ToolCall call, ToolContext context);
}
```

### 4.4 统一错误码

```java
public enum LlmErrorCode {
    NETWORK_ERROR,   // 网络异常
    TIMEOUT,         // 超时
    RATE_LIMITED,    // 限流 (429)
    AUTH_ERROR,      // 鉴权失败 (401/403)
    MODEL_ERROR,     // 模型侧错误 (5xx)
    TOOL_ERROR,      // 工具执行异常
    UNKNOWN          // 未知
}
```

---

## 五、sdk-openai-compatible：OpenAI 兼容客户端的实现

本章介绍 SDK 如何基于上述 OpenAI 标准 API（第三章）进行封装和适配。`sdk-openai-compatible` 是整个 SDK 的核心实现层，支持：

- 同步对话（`chat()`）
- 流式对话 SSE（`stream()`）
- Function Calling / Tool Calling 自动循环
- 超时自定义
- 可观测性埋点

### 5.1 客户端初始化

```java
public class OpenAiCompatibleClient implements LlmClient, StreamingLlmClient {

    private final OkHttpClient okHttpClient;
    private final OpenAiCompatibleProperties properties;
    private final ObjectMapper objectMapper;
    private final OpenAiRequestMapper requestMapper;
    private final OpenAiResponseMapper responseMapper;

    public OpenAiCompatibleClient(OkHttpClient okHttpClient,
                                  OpenAiCompatibleProperties properties) {
        this.okHttpClient = applyTimeouts(okHttpClient, properties);
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.requestMapper = new OpenAiRequestMapper();
        this.responseMapper = new OpenAiResponseMapper();
    }
}
```

`OpenAiCompatibleProperties` 持有 `baseUrl`、`apiKey`、超时等配置，`applyTimeouts()` 在 OkHttp 上叠加属性级超时覆盖。

### 5.2 同步 chat() 的完整链路

```java
@Override
public ChatResponse chat(ChatRequest request) {
    OpenAiRuntimeOptions runtimeOptions = OpenAiRuntimeOptions.fromMetadata(request.metadata());
    String model = resolveModel(request);
    LlmObservationInterceptor observation = runtimeOptions.observationOrDefault();

    long start = observation.before(model);   // 观测开始
    try {
        // 1. 序列化请求
        Map<String, Object> payload = requestMapper.toOpenAiRequest(request, properties);

        // 2. 首次 HTTP 调用
        Map<String, Object> firstRaw = executeChatCompletion(payload);

        // 3. 如果模型返回了 tool_calls，自动执行并回传结果
        Map<String, Object> finalRaw = maybeContinueWithToolResults(payload, firstRaw, runtimeOptions);

        // 4. 反序列化响应
        ChatResponse response = responseMapper.toChatResponse(finalRaw);

        long latencyMs = observation.after(start);  // 观测结束
        recordSuccess(runtimeOptions, model, latencyMs);
        audit(runtimeOptions, "chat", "SUCCESS");
        return response;
    } catch (SocketTimeoutException e) {
        throw new LlmException(LlmErrorCode.TIMEOUT, "模型调用超时", e);
    } catch (IOException e) {
        throw new LlmException(LlmErrorCode.NETWORK_ERROR, "模型调用网络异常", e);
    }
}
```

核心要点：**整体调用链路保持线性**，`maybeContinueWithToolResults()` 负责 Tool Calling 自动循环，对业务调用方透明。

### 5.3 HTTP 层：executeChatCompletion()

```java
private Map<String, Object> executeChatCompletion(Map<String, Object> payload) throws IOException {
    String payloadJson = objectMapper.writeValueAsString(payload);
    Request httpRequest = new Request.Builder()
            .url(buildChatCompletionUrl(properties.baseUrl()))   // {baseUrl}/chat/completions
            .header("Authorization", "Bearer " + properties.apiKey())
            .header("Content-Type", "application/json")
            .post(RequestBody.create(payloadJson, JSON))
            .build();

    try (Response response = okHttpClient.newCall(httpRequest).execute()) {
        String bodyText = response.body() == null ? "" : response.body().string();
        if (!response.isSuccessful()) {
            LlmErrorCode code = mapHttpStatusToErrorCode(response.code());
            throw new LlmException(code, "上游模型调用失败, status=" + response.code());
        }
        return objectMapper.readValue(bodyText, new TypeReference<>() {});
    }
}

private static String buildChatCompletionUrl(String baseUrl) {
    String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return trimmed + "/chat/completions";
}

private static LlmErrorCode mapHttpStatusToErrorCode(int code) {
    if (code == 401 || code == 403) return LlmErrorCode.AUTH_ERROR;
    if (code == 429)                return LlmErrorCode.RATE_LIMITED;
    if (code >= 500)                return LlmErrorCode.MODEL_ERROR;
    return LlmErrorCode.UNKNOWN;
}
```

**HTTP 请求报文示例：**

```http
POST https://api.openai.com/v1/chat/completions
Authorization: Bearer sk-xxxx
Content-Type: application/json

{
  "model": "gpt-4o",
  "messages": [
    {"role": "system", "content": "你是一个助手"},
    {"role": "user",   "content": "今天天气怎么样？"}
  ],
  "temperature": 0.7,
  "max_tokens": 1024
}
```

**HTTP 响应报文示例：**

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "model": "gpt-4o",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "根据你的位置，今天..."
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 25,
    "completion_tokens": 47,
    "total_tokens": 72
  }
}
```

### 5.4 Tool Calling 自动循环

当模型决定调用工具时，响应中的 `finish_reason` 为 `"tool_calls"`，`message.tool_calls` 数组非空：

```java
private Map<String, Object> maybeContinueWithToolResults(
        Map<String, Object> originalPayload,
        Map<String, Object> firstRaw,
        OpenAiRuntimeOptions runtimeOptions) throws IOException {

    if (!runtimeOptions.autoToolContinue()) return firstRaw;

    ToolExecutor executor = runtimeOptions.toolExecutor();
    if (executor == null) return firstRaw;

    Map<String, Object> assistantMessage = extractAssistantMessage(firstRaw);
    List<Map<String, Object>> toolCalls = extractToolCalls(assistantMessage);
    if (toolCalls.isEmpty()) return firstRaw;

    // 构造下一轮消息：原消息 + assistant tool_calls + tool 结果
    List<Map<String, Object>> nextMessages = new ArrayList<>(extractPayloadMessages(originalPayload));
    nextMessages.add(assistantMessage);

    ToolContext context = resolveToolContext(runtimeOptions);
    for (Map<String, Object> toolCallMap : toolCalls) {
        ToolResult toolResult = executeToolCall(toolCallMap, executor, context);
        nextMessages.add(buildToolResultMessage(toolResult));  // role: "tool"
    }

    // 第二次请求：把工具结果还给模型，拿最终回答
    Map<String, Object> secondPayload = new HashMap<>(originalPayload);
    secondPayload.put("messages", nextMessages);
    return executeChatCompletion(secondPayload);
}
```

**Tool Calling 完整报文流程：**

**第一轮请求（带工具定义）：**
```json
{
  "model": "gpt-4o",
  "messages": [{"role": "user", "content": "北京今天气温？"}],
  "tools": [{
    "type": "function",
    "function": {
      "name": "get_weather",
      "description": "获取城市天气",
      "parameters": {
        "type": "object",
        "properties": {
          "city": {"type": "string"}
        },
        "required": ["city"]
      }
    }
  }]
}
```

**第一轮响应（模型要求调工具）：**
```json
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": null,
      "tool_calls": [{
        "id": "call_abc",
        "type": "function",
        "function": {
          "name": "get_weather",
          "arguments": "{\"city\": \"北京\"}"
        }
      }]
    },
    "finish_reason": "tool_calls"
  }]
}
```

**第二轮请求（工具结果回传）：**
```json
{
  "messages": [
    {"role": "user",      "content": "北京今天气温？"},
    {"role": "assistant", "content": null, "tool_calls": [...]},
    {"role": "tool",      "tool_call_id": "call_abc", "content": "{\"temperature\": 28, \"unit\": \"°C\"}"}
  ]
}
```

**第二轮响应（最终回答）：**
```json
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "北京今天气温 28°C，天气晴朗。"
    },
    "finish_reason": "stop"
  }]
}
```

### 5.5 流式输出（SSE）

流式调用在独立线程中处理 SSE 事件流，通过 `LlmEventListener` 回调业务层：

```java
@Override
public AutoCloseable stream(ChatRequest request, LlmEventListener listener) {
    Map<String, Object> payload = requestMapper.toOpenAiRequest(request, properties, true); // stream=true
    StreamSession session = new StreamSession();

    Thread worker = new Thread(() -> {
        try {
            Map<String, Object> currentPayload = payload;
            while (!session.cancelled()) {
                // 单轮 SSE 流式处理
                StreamCycleResult cycleResult = streamOnce(currentPayload, runtimeOptions, listener, session);
                // 如果有 tool_calls，构造下一轮继续
                Map<String, Object> nextPayload = buildNextStreamPayloadIfNeeded(currentPayload, runtimeOptions, cycleResult);
                if (nextPayload == null) break;
                currentPayload = nextPayload;
            }
        } catch (SocketTimeoutException e) {
            listener.onError(new LlmException(LlmErrorCode.TIMEOUT, "流式调用超时", e));
        } catch (IOException e) {
            listener.onError(new LlmException(LlmErrorCode.NETWORK_ERROR, "流式读取失败", e));
        }
    }, "llm-openai-stream");

    worker.setDaemon(true);
    worker.start();

    // 返回取消句柄
    return () -> {
        session.cancel();
        worker.interrupt();
        session.closeCurrent();
    };
}
```

**SSE 流式响应数据格式：**

```
data: {"id":"chatcmpl-x","choices":[{"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"id":"chatcmpl-x","choices":[{"delta":{"content":"北京"},"finish_reason":null}]}

data: {"id":"chatcmpl-x","choices":[{"delta":{"content":"今天"},"finish_reason":null}]}

data: {"id":"chatcmpl-x","choices":[{"delta":{"content":"气温 28°C"},"finish_reason":null}]}

data: {"id":"chatcmpl-x","choices":[{"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

SDK 内部的 `SseEventParser` 会解析上述格式，映射为以下事件类型（`LlmEventType`）：

| 事件类型 | 说明 |
|----------|------|
| `START` | 流开始，携带 id |
| `DELTA` | 文本增量片段 |
| `TOOL_CALL` | 工具调用片段（流式 function arguments 分片） |
| `TOOL_RESULT` | 工具执行结果（内部循环使用） |
| `ERROR` | 流处理异常 |
| `DONE` | 流结束 |

---

## 六、sdk-observability：轻量可观测性

### 6.1 设计思路

可观测性组件全部通过 `OpenAiRuntimeOptions`（从 `ChatRequest.metadata` 解析）**按请求注入**，无 Spring Bean 依赖：

```java
// 从 metadata 读取运行时选项
OpenAiRuntimeOptions runtimeOptions = OpenAiRuntimeOptions.fromMetadata(request.metadata());

// 可选组件
LlmObservationInterceptor observation = runtimeOptions.observationOrDefault(); // 耗时统计
LlmMetricsCollector metrics = runtimeOptions.metricsCollector();               // 指标上报
LlmAuditLogger auditLogger = runtimeOptions.auditLogger();                     // 审计日志
BudgetEventPublisher budgetPublisher = runtimeOptions.budgetEventPublisher();  // 预算告警
```

### 6.2 LlmObservationInterceptor：耗时统计骨架

```java
public class LlmObservationInterceptor {

    public long before(String model) {
        return System.currentTimeMillis();  // 记录开始时间戳
    }

    public long after(long start) {
        return System.currentTimeMillis() - start;  // 返回耗时毫秒数
    }
}
```

设计为**可继承的骨架类**，业务层可重写以接入 Micrometer、Prometheus、SkyWalking 等。

### 6.3 LlmMetricsCollector：指标上报接口

```java
public interface LlmMetricsCollector {
    void recordSuccess(String model, long latencyMs);
    void recordFailure(String model, String errorCode);
    void recordTokenUsage(String model, int promptTokens, int completionTokens);
}
```

实现示例（基于 Micrometer）：

```java
public class MicrometerMetricsCollector implements LlmMetricsCollector {
    private final MeterRegistry registry;

    @Override
    public void recordSuccess(String model, long latencyMs) {
        registry.timer("llm.request", "model", model, "status", "success")
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordFailure(String model, String errorCode) {
        registry.counter("llm.error", "model", model, "error", errorCode).increment();
    }
}
```

### 6.4 LlmAuditLogger：审计日志

```java
public interface LlmAuditLogger {
    void log(String operation, String status, Map<String, Object> context);
}
```

主链路中的审计调用点：

```java
audit(runtimeOptions, "chat", "SUCCESS");    // 成功
audit(runtimeOptions, "chat", "FAILED:TIMEOUT");  // 失败
```

### 6.5 BudgetEvent：Token 预算控制

```java
public record BudgetEvent(
        String tenantId,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long timestampMs
) {}

public interface BudgetEventPublisher {
    void publish(BudgetEvent event);
}
```

每次成功调用后，SDK 会发布 `BudgetEvent`，业务层可据此做多租户 Token 配额控制与成本核算。

---

## 七、Embedding 接口落地：在 gijela-core-chat 中的实现

> 特别说明：Embedding 能力当前实现在 `gijela-core-chat` 的 `KnowledgeSearchGateway`，
> 而非 `gijela-core-llm` SDK 层。这是一个有意识的**架构分层选择**：
> RAG 检索流水线（Embedding → Qdrant）作为知识库领域能力内聚在 chat 模块，
> SDK 层专注于对话模型的通用 HTTP 抽象。

### 7.1 Embedding 接口规范（OpenAI 兼容）

**请求报文：**

```http
POST {baseUrl}/embeddings
Authorization: Bearer {apiKey}
Content-Type: application/json

{
  "model": "text-embedding-3-small",
  "input": "如何配置 Redis 连接池？"
}
```

**响应报文：**

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.0023064255, -0.009327292, 0.015797347, ...]
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 8,
    "total_tokens": 8
  }
}
```

`embedding` 数组的长度即为向量维度，`text-embedding-3-small` 默认 1536 维，`text-embedding-ada-002` 为 1536 维，国产模型（如 BGE）通常为 768 或 1024 维。

### 7.2 embedQueryWithConfig()：核心调用实现

```java
// 位于 KnowledgeSearchGateway.java
private List<Double> embedQueryWithConfig(ChatModelConfig config, String text) {
    String embeddingUrl = buildEmbeddingUrl(config.getBaseUrl());

    Map<String, Object> reqBody = Map.of(
            "model", config.getModel(),
            "input", text
    );

    Request.Builder builder = new Request.Builder()
            .url(embeddingUrl)
            .post(RequestBody.create(writeJson(reqBody), JSON));
    if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
        builder.header("Authorization", "Bearer " + config.getApiKey());
    }

    try (Response response = okHttpClient.newCall(builder.build()).execute()) {
        String responseBody = response.body() == null ? "" : response.body().string();
        if (!response.isSuccessful()) {
            throw new RuntimeException("embedding failed, status=" + response.code()
                    + ", body=" + clip(responseBody));
        }

        // 解析 data[0].embedding
        Map<String, Object> root = objectMapper.readValue(responseBody, new TypeReference<>() {});
        List<?> dataList = (List<?>) root.get("data");
        Map<?, ?> itemMap = (Map<?, ?>) dataList.get(0);
        List<?> embList = (List<?>) itemMap.get("embedding");

        List<Double> vector = new ArrayList<>(embList.size());
        for (Object v : embList) {
            vector.add(v instanceof Number n ? n.doubleValue()
                                             : Double.parseDouble(String.valueOf(v)));
        }
        return vector;
    } catch (IOException e) {
        throw new RuntimeException("embedding request failed", e);
    }
}

private String buildEmbeddingUrl(String baseUrl) {
    String trimmed = baseUrl.trim();
    if (trimmed.endsWith("/embeddings")) return trimmed;
    if (trimmed.endsWith("/"))           return trimmed + "embeddings";
    return trimmed + "/embeddings";
}
```

### 7.3 多租户模型配置解析

```java
// resolveEmbeddingConfig：先按 tenantId 查，找不到 fallback 到 "default"
private ChatModelConfig resolveEmbeddingConfig(String tenantId, String embeddingModel) {
    String resolvedTenant = normalizeTenant(tenantId);

    // 优先查租户自己的 EMBEDDING 配置
    ChatModelConfig byTenant = queryEmbeddingConfig(resolvedTenant, embeddingModel);
    if (byTenant != null) return byTenant;

    // fallback：查 default 租户配置
    if (!"default".equals(resolvedTenant)) {
        ChatModelConfig byDefault = queryEmbeddingConfig("default", embeddingModel);
        if (byDefault != null) return byDefault;
    }

    throw new IllegalStateException(
        "未找到可用的 EMBEDDING 模型配置，请先在模型配置页维护（tenant=" + resolvedTenant + "）");
}
```

这套多租户 fallback 机制让 SaaS 场景下的模型配置管理变得灵活：
- 租户可以有自己的 embedding 模型（私有部署的 BGE、Jina 等）
- 未配置的租户自动使用平台默认模型
- 找不到配置时抛出明确的业务异常，不会静默失败

### 7.4 向量维度探测接口

**HTTP 接口：**

```http
GET /knowledge/embedding/dimension?probeText=测试探针&embeddingModel=text-embedding-3-small
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "model": "text-embedding-3-small",
    "dimension": 1536,
    "probeText": "测试探针"
  }
}
```

**实现链路：**

```
ChatController.detectEmbeddingDimension()
    → KnowledgeIngestionService.detectEmbeddingDimension()
        → KnowledgeSearchGateway.detectEmbeddingDimension(tenantId, probeText, embeddingModel)
            → resolveEmbeddingConfig() 查模型配置
            → embedQueryWithConfig() 发起 embedding 请求
            → 返回 {model, dimension: vector.size(), probeText}
```

这个接口的核心价值在于：创建 Qdrant 向量集合前，**必须提前确定维度**，否则集合参数无法设置正确。

### 7.5 模型连通性测试（testEmbedding）

在后台添加 Embedding 类型模型配置时，会自动触发连通性测试：

```java
private void testEmbedding(ChatModelConfig entity, Map<String, Object> result) throws IOException {
    Map<String, Object> reqBody = Map.of(
            "model", entity.getModel(),
            "input", "test"
    );
    String embeddingUrl = buildEmbeddingUrl(entity.getBaseUrl());

    // ... OkHttp 请求 ...

    // 解析并回填维度信息
    List<?> embList = (List<?>) itemMap.get("embedding");
    result.put("dimension", embList.size());  // 顺便探测并记录维度
}
```

这样，当管理员在界面上保存模型配置时，系统会：
1. 验证 baseUrl + apiKey 能否正常访问
2. 同步将向量维度写入 `result`，供后续 Qdrant 集合初始化使用

---

## 八、RAG 完整流水线

将上述所有能力串联起来，`gijela-core-chat` 中的 RAG（检索增强生成）流程如下：

```
用户问题
    │
    ▼
KnowledgeSearchGateway.search(tenantId, query)
    │
    ├─ 1. embedQuery(tenantId, query, null)
    │       └─ resolveEmbeddingConfig()    → 查 DB 得到 baseUrl/apiKey/model
    │           └─ embedQueryWithConfig()  → POST {baseUrl}/embeddings
    │               └─ 返回 List<Double> vector（1536 维）
    │
    ├─ 2. Qdrant /points/search
    │       └─ POST {qdrantUrl}/collections/{collection}/points/search
    │           Body: {vector: [...], limit: 5, filter: {tenant_id: "xxx"}}
    │           返回 Top-K 相关文档片段
    │
    └─ 3. 返回命中列表 → 注入 system prompt → 调 LlmClient.chat()
```

---

## 九、架构演进展望

当前 Embedding 实现在 `gijela-core-chat` 的原因是 RAG 链路的完整性——嵌入查询和向量搜索是强耦合的同一链路，分拆到 SDK 层会引入不必要的远程调用或跨模块依赖。

但如果后续需要在多个模块（如 `gijela-core-chat-flow`）复用 Embedding 能力，可以考虑：

1. **提取 EmbeddingClient 接口到 sdk-core**
   ```java
   public interface EmbeddingClient {
       List<Double> embed(String model, String text);
       int detectDimension(String model, String probeText);
   }
   ```

2. **在 sdk-openai-compatible 中添加 OpenAiEmbeddingClient 实现**
   - 复用现有 OkHttp、超时、鉴权逻辑
   - 统一 URL 构建（`{baseUrl}/embeddings`）

3. **`KnowledgeSearchGateway` 注入 `EmbeddingClient` 接口**
   - 解耦模型 HTTP 细节与 RAG 检索逻辑

该演进建议按需推进：**当确实出现第二个需要 Embedding 能力的模块时再抽象**，避免过度设计。

---

## 十、附录：OpenAI 标准 API 完整 curl 实战

本附录展示 OpenAI 标准 API 的完整 curl 命令、Bash 脚本和 Postman 导入示例。这些是第三章内容的扩展和补充。

### 10.1 基础配置

假设部署了兼容 OpenAI API 的服务，以下示例中使用的参数：

```bash
# OpenAI 官方 API
API_BASE_URL="https://api.openai.com/v1"
API_KEY="sk-xxxxxxxxxxxxxxxxxxxx"

# 或国内兼容服务（例）
# API_BASE_URL="https://api.qwen.aliyun.com/v1"
# API_KEY="sk-xxxxxxx"

MODEL="gpt-4o"  # 或 "qwen-plus"、"claude-3.5-sonnet" 等
```

### 10.2 基础对话（同步）

**请求：**

```bash
curl -X POST "$API_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {
        "role": "system",
        "content": "你是一个有用的助手。"
      },
      {
        "role": "user",
        "content": "请用中文介绍一下 RESTful API 的设计原则。"
      }
    ],
    "temperature": 0.7,
    "max_tokens": 500
  }'
```

**响应 JSON：**

```json
{
  "id": "chatcmpl-8WPvW9wqd5fQD9Z8nW5d9X4p",
  "object": "chat.completion",
  "created": 1699564800,
  "model": "gpt-4o",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "RESTful API 的设计原则包括：\n\n1. **统一接口** - 基于资源的设计，使用标准 HTTP 方法 (GET, POST, PUT, DELETE)\n2. **无状态** - 每个请求包含所有必要的信息，服务器不维护客户端上下文\n3. **可缓存** - 正确使用 HTTP 缓存机制提高性能\n4. **分层系统** - 客户端不直接连接最终服务器，可通过中间层\n5. **按需代码** - 可选的，允许服务器扩展客户端功能\n6. **统一资源标识** - 每个资源有唯一 URI，与操作相分离"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 28,
    "completion_tokens": 156,
    "total_tokens": 184
  }
}
```

**关键字段说明：**

| 字段 | 说明 |
|------|------|
| `id` | 唯一请求标识，用于追踪和审计 |
| `created` | Unix 时间戳，响应生成时间 |
| `finish_reason` | `"stop"` = 正常结束，`"length"` = 达到 max_tokens，`"tool_calls"` = 调用工具 |
| `usage` | Token 用量统计，对应 embedding 和成本计算 |

### 10.3 流式对话（SSE）

**请求：** 添加 `"stream": true`

```bash
curl -X POST "$API_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "写一首短诗"}
    ],
    "stream": true
  }'
```

**流式响应 (SSE 格式)：**

```
data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"春"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"风"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"拂"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"过"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"山"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"川\n花"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"开"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"遍"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"野\n鸟"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"语"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"声"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"啼"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{"content":"春"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxxxx","choices":[{"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

**Python 解析流式响应示例：**

```python
import requests
import json

url = f"{API_BASE_URL}/chat/completions"
headers = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}
payload = {
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "写一首短诗"}],
    "stream": True
}

with requests.post(url, json=payload, headers=headers, stream=True) as response:
    for line in response.iter_lines():
        if line:
            line = line.decode('utf-8')
            if line.startswith('data: '):
                data_str = line[6:]  # 移除 "data: " 前缀
                if data_str == '[DONE]':
                    break
                try:
                    data = json.loads(data_str)
                    delta_content = data['choices'][0]['delta'].get('content', '')
                    if delta_content:
                        print(delta_content, end='', flush=True)
                except:
                    pass
```

### 10.4 Function Calling / Tool Calling

#### 10.4.1 第一轮请求（携带工具定义）

**请求 JSON：**

```bash
curl -X POST "$API_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {
        "role": "system",
        "content": "你是一个天气查询助手。用户问天气时，请调用 get_weather 工具。"
      },
      {
        "role": "user",
        "content": "北京今天的天气怎么样？"
      }
    ],
    "tools": [
      {
        "type": "function",
        "function": {
          "name": "get_weather",
          "description": "获取指定城市的天气信息",
          "parameters": {
            "type": "object",
            "properties": {
              "city": {
                "type": "string",
                "description": "城市名称，例如：北京、上海"
              },
              "days": {
                "type": "integer",
                "description": "查询天数，默认1天",
                "default": 1
              }
            },
            "required": ["city"]
          }
        }
      }
    ],
    "tool_choice": "auto"
  }'
```

**第一轮响应（模型要求调工具）：**

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1699564800,
  "model": "gpt-4o",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": null,
        "tool_calls": [
          {
            "id": "call_1a2b3c4d5e6f",
            "type": "function",
            "function": {
              "name": "get_weather",
              "arguments": "{\"city\": \"北京\", \"days\": 1}"
            }
          }
        ]
      },
      "finish_reason": "tool_calls"
    }
  ],
  "usage": {
    "prompt_tokens": 98,
    "completion_tokens": 32,
    "total_tokens": 130
  }
}
```

#### 10.4.2 第二轮请求（工具结果回传）

业务层执行工具（这里假设返回结果为 `{"temperature": 28, "weather": "晴朗", "humidity": 65}`），然后发送第二轮请求：

```bash
curl -X POST "$API_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {
        "role": "system",
        "content": "你是一个天气查询助手。用户问天气时，请调用 get_weather 工具。"
      },
      {
        "role": "user",
        "content": "北京今天的天气怎么样？"
      },
      {
        "role": "assistant",
        "content": null,
        "tool_calls": [
          {
            "id": "call_1a2b3c4d5e6f",
            "type": "function",
            "function": {
              "name": "get_weather",
              "arguments": "{\"city\": \"北京\", \"days\": 1}"
            }
          }
        ]
      },
      {
        "role": "tool",
        "tool_call_id": "call_1a2b3c4d5e6f",
        "name": "get_weather",
        "content": "{\"temperature\": 28, \"weather\": \"晴朗\", \"humidity\": 65}"
      }
    ],
    "tools": [
      {
        "type": "function",
        "function": {
          "name": "get_weather",
          "description": "获取指定城市的天气信息",
          "parameters": {
            "type": "object",
            "properties": {
              "city": {"type": "string", "description": "城市名称"},
              "days": {"type": "integer", "description": "查询天数", "default": 1}
            },
            "required": ["city"]
          }
        }
      }
    ]
  }'
```

**第二轮响应（模型最终回答）：**

```json
{
  "id": "chatcmpl-def456",
  "object": "chat.completion",
  "created": 1699564801,
  "model": "gpt-4o",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "北京今天的天气很不错！气温约 28°C，天气晴朗，湿度 65%。非常适合外出活动，建议你做好防晒措施。"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 156,
    "completion_tokens": 68,
    "total_tokens": 224
  }
}
```

### 10.5 文本向量化（Embedding）

**请求：**

```bash
curl -X POST "$API_BASE_URL/embeddings" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-small",
    "input": "如何在 Spring Boot 中配置 Redis 连接池？"
  }'
```

**响应 JSON：**

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [
        0.00231231,
        -0.00932729,
        0.01579734,
        0.00412313,
        -0.01203102,
        0.00934812,
        -0.00182934,
        0.01834918,
        0.00412893,
        -0.00521034,
        ...  // 共 1536 维
      ]
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 11,
    "total_tokens": 11
  }
}
```

**批量 Embedding（多个文本）：**

```bash
curl -X POST "$API_BASE_URL/embeddings" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-small",
    "input": [
      "Redis 性能优化",
      "MySQL 索引设计",
      "Spring Cloud 微服务架构"
    ]
  }'
```

**批量响应：**

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.002..., -0.009..., ...]
    },
    {
      "object": "embedding",
      "index": 1,
      "embedding": [0.001..., 0.005..., ...]
    },
    {
      "object": "embedding",
      "index": 2,
      "embedding": [-0.003..., 0.012..., ...]
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 22,
    "total_tokens": 22
  }
}
```

### 10.6 错误响应

#### 10.6.1 认证错误 (401)

**请求**（apiKey 错误或过期）：

```bash
curl -X POST "$API_BASE_URL/chat/completions" \
  -H "Authorization: Bearer invalid-key" \
  -H "Content-Type: application/json" \
  -d '{"model": "gpt-4o", "messages": [{"role": "user", "content": "hello"}]}'
```

**响应：**

```json
{
  "error": {
    "message": "Incorrect API key provided. You passed invalid-key, but we expected something else.",
    "type": "invalid_request_error",
    "param": null,
    "code": "invalid_api_key"
  }
}
```

#### 10.6.2 模型不存在 (400)

**请求**（model 不存在）：

```bash
curl -X POST "$API_BASE_URL/chat/completions" \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{"model": "non-existent-model", "messages": [{"role": "user", "content": "hi"}]}'
```

**响应：**

```json
{
  "error": {
    "message": "The model `non-existent-model` does not exist",
    "type": "invalid_request_error",
    "param": "model",
    "code": "model_not_found"
  }
}
```

#### 10.6.3 超时 (529 或读超时)

当请求在指定时间内未收到响应时（通常由客户端超时或服务器限流导致）：

```json
{
  "error": {
    "message": "Request timed out",
    "type": "server_error",
    "code": "timeout"
  }
}
```

#### 10.6.4 限流 (429)

**响应：**

```json
{
  "error": {
    "message": "Rate limit exceeded. Please retry after 60 seconds.",
    "type": "rate_limit_error",
    "code": "rate_limit_exceeded"
  }
}
```

**响应头中会包含：**

```
Retry-After: 60
X-RateLimit-Limit-Requests: 1000
X-RateLimit-Limit-Tokens: 100000
X-RateLimit-Remaining-Requests: 0
X-RateLimit-Remaining-Tokens: 0
X-RateLimit-Reset-Requests: 2024-01-15T10:30:00Z
X-RateLimit-Reset-Tokens: 2024-01-15T10:31:00Z
```

### 10.7 完整 Bash 脚本示例

**文件：`call-openai-api.sh`**

```bash
#!/bin/bash

# OpenAI API 配置
API_BASE_URL="${API_BASE_URL:-https://api.openai.com/v1}"
API_KEY="${API_KEY:-sk-xxxxxxxxxxxxxxxxxxxx}"
MODEL="${MODEL:-gpt-4o}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 函数：打印分隔线
print_section() {
    echo -e "${BLUE}========== $1 ==========${NC}"
}

# 函数：执行 curl 并美化 JSON 输出
call_api() {
    local method=$1
    local endpoint=$2
    local data=$3
    local stream=${4:-false}
    
    print_section "API 请求: $method $endpoint"
    
    local curl_cmd="curl -s -X $method \"$API_BASE_URL$endpoint\" \
        -H \"Authorization: Bearer $API_KEY\" \
        -H \"Content-Type: application/json\""
    
    if [ -n "$data" ]; then
        curl_cmd="$curl_cmd -d '$data'"
    fi
    
    if [ "$stream" = "true" ]; then
        echo "▶ 流式响应:"
        eval "$curl_cmd"
    else
        echo "▶ 请求体:"
        echo "$data" | jq . 2>/dev/null || echo "$data"
        echo ""
        echo "▶ 响应体:"
        eval "$curl_cmd" | jq . 2>/dev/null
    fi
    echo ""
}

# 1. 基础对话
print_section "1. 基础对话测试"
call_api POST "/chat/completions" '{
  "model": "'$MODEL'",
  "messages": [
    {"role": "user", "content": "你好，请简要介绍一下 REST API"}
  ],
  "temperature": 0.7,
  "max_tokens": 200
}'

# 2. 流式对话
print_section "2. 流式对话测试"
call_api POST "/chat/completions" '{
  "model": "'$MODEL'",
  "messages": [
    {"role": "user", "content": "用 JSON 格式列出 HTTP 的 5 个主要方法"}
  ],
  "stream": true
}' true

# 3. Embedding
print_section "3. 文本向量化测试"
call_api POST "/embeddings" '{
  "model": "text-embedding-3-small",
  "input": "Spring Boot 微服务架构"
}'

# 4. Tool Calling（第一轮）
print_section "4. Tool Calling - 第一轮请求"
call_api POST "/chat/completions" '{
  "model": "'$MODEL'",
  "messages": [
    {"role": "user", "content": "上海今天的天气如何？"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "获取城市天气",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {"type": "string"}
          },
          "required": ["city"]
        }
      }
    }
  ]
}'

echo -e "${GREEN}✓ 所有测试完成${NC}"
```

**运行脚本：**

```bash
# 方式 1：直接运行
bash call-openai-api.sh

# 方式 2：指定 API 配置
API_BASE_URL="https://api.qwen.aliyun.com/v1" \
API_KEY="sk-xxxxx" \
MODEL="qwen-plus" \
bash call-openai-api.sh

# 方式 3：保存输出到文件
bash call-openai-api.sh | tee api-test-$(date +%s).log
```

### 10.8 Postman 导入示例

如果使用 Postman，可直接导入以下集合定义：

```json
{
  "info": {
    "name": "OpenAI Compatible API",
    "version": "1.0"
  },
  "item": [
    {
      "name": "Chat - 基础对话",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Authorization",
            "value": "Bearer {{API_KEY}}",
            "type": "text"
          },
          {
            "key": "Content-Type",
            "value": "application/json",
            "type": "text"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"model\": \"{{MODEL}}\",\n  \"messages\": [\n    {\"role\": \"user\", \"content\": \"Hello, how are you?\"}\n  ],\n  \"temperature\": 0.7\n}"
        },
        "url": {
          "raw": "{{BASE_URL}}/chat/completions",
          "host": ["{{BASE_URL}}"],
          "path": ["chat", "completions"]
        }
      }
    },
    {
      "name": "Embeddings - 文本向量化",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Authorization",
            "value": "Bearer {{API_KEY}}",
            "type": "text"
          },
          {
            "key": "Content-Type",
            "value": "application/json",
            "type": "text"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"model\": \"text-embedding-3-small\",\n  \"input\": \"The quick brown fox jumps over the lazy dog\"\n}"
        },
        "url": {
          "raw": "{{BASE_URL}}/embeddings",
          "host": ["{{BASE_URL}}"],
          "path": ["embeddings"]
        }
      }
    }
  ],
  "variable": [
    {
      "key": "BASE_URL",
      "value": "https://api.openai.com/v1",
      "type": "string"
    },
    {
      "key": "API_KEY",
      "value": "sk-xxxxxxxxxxxxxxxxxxxx",
      "type": "string"
    },
    {
      "key": "MODEL",
      "value": "gpt-4o",
      "type": "string"
    }
  ]
}
```

---

## 十一、总结

| 能力 | 实现位置 | 技术栈 |
|------|----------|--------|
| 同步对话 | `sdk-openai-compatible` | OkHttp + Jackson |
| 流式对话 SSE | `sdk-openai-compatible` | OkHttp SSE + 独立线程 |
| Tool Calling 自动循环 | `sdk-openai-compatible` | 多轮 HTTP 请求 |
| 可观测性（耗时/指标/审计/预算）| `sdk-observability` | 骨架类 + 接口，按需注入 |
| Embedding（文本向量化）| `gijela-core-chat` | OkHttp，OpenAI `/embeddings` |
| 向量检索 | `gijela-core-chat` | Qdrant REST API |
| 多租户模型配置 | `gijela-core-chat` | MySQL + fallback 策略 |
| MCP 工具集成 | `sdk-mcp` | JSON-RPC 2.0 |
| Skill 注册 | `sdk-skill` | 注册表模式 |

整套 SDK 的核心思想是：**以尽量少的依赖和清晰的接口，封装 LLM 调用链路中最复杂的部分（Tool Calling、SSE、可观测性），使业务层聚焦于 `LlmClient.chat()` 与 `StreamingLlmClient.stream()` 两个入口**。

---

## 十二、工程位置与仓库地址

为方便读者直接落地阅读和对照代码，下面给出本文相关工程位置：

- SDK 总目录：`gijela-core/gijela-core-llm/`
- 核心抽象（接口与模型）：`gijela-core/gijela-core-llm/gijela-core-llm-sdk-core/`
- OpenAI 兼容实现：`gijela-core/gijela-core-llm/gijela-core-llm-sdk-openai-compatible/`
- 可观测性实现：`gijela-core/gijela-core-llm/gijela-core-llm-sdk-observability/`
- Embedding 与 RAG 业务落地：`gijela-core/gijela-core-chat/`

仓库地址：

- GitHub：https://github.com/wojiaozhangtudou/gijela
- Gitee：https://gitee.com/zhangjq123/gijela
