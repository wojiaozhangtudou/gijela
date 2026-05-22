# gijela：从自研 Java LLM 工具链到企业级 AI 工作流平台的完整实践

> 本文介绍 gijela 单仓工程的完整模块体系，涵盖自研 LLM SDK、MCP 客户端、Skill 机制、聊天服务联调、工作流执行引擎以及企业后台骨架的技术细节。

---

## 一、仓库定位与整体思路

很多 Java 项目在接入大模型时的方式是：找一个现成 SDK（比如 LangChain4j 或 Spring AI），写几行 `ChatClient.chat()` 就结束了。这样当然能跑，但它会带来几个长期问题：

- 无法灵活控制底层 HTTP 行为（超时、重试、流式、批处理）
- 工具调用、MCP 协议、Skill 注册机制全部依赖外部 SDK 的版本迭代
- 可观测性和审计能力几乎没有，出了问题无从追踪
- 模块边界模糊，能力无法在不同业务服务之间复用

gijela 的思路是：**自己做一套能力层，然后用真实的业务服务来验证它**。

整体分三层展开：

```
┌─────────────────────────────────────────────────┐
│              业务验证与承载层                      │
│  gijela-core-chat  │  gijela-core-chat-flow       │
│  gijela-core-pistil（企业后台骨架）                │
├─────────────────────────────────────────────────┤
│              LLM 能力层                           │
│         gijela-core-llm（自研 SDK）               │
├─────────────────────────────────────────────────┤
│              基础与安全层                         │
│  gijela-core-common  │  gijela-core-security-common│
└─────────────────────────────────────────────────┘
```

前端对应三个 Vue 3 子应用：`gijela-bloom-pistil`、`gijela-bloom-chat`、`gijela-bloom-chat-flow`。

---

## 二、基础与安全层

### 2.1 gijela-core-common

所有模块共用的基础设施，保证"响应格式统一、异常处理一致"。

核心内容：
- 统一响应体 `ApiResponse<T>`，包含 code / message / data 三字段
- 全局异常处理，覆盖业务异常、参数校验异常、安全异常等
- 错误码枚举，与各模块的 `ServiceException` 关联

没什么高深之处，但是它的存在决定了整个仓库的接口风格是否一致。

### 2.2 gijela-core-security-common

认证鉴权的共用实现，所有需要登录与权限控制的模块（目前主要是 pistil）都引入这个模块。

关键类：

**`JwtUtil`**：JWT 签发与解析的工具类，统一配置 secret 和过期时间，通过 `JwtProperties` 从配置文件绑定。

**`JwtAuthenticationFilter`**：Spring Security 过滤器链的核心节点，在每次请求时从 Header 提取 Bearer Token，解析后构造 `JwtAuthUser`，写入 `SecurityContextHolder`。

**`TokenVersionValidator`**：解决 JWT 无状态的"已登出 token 仍有效"问题，在 Redis 中存储每个用户当前有效的 token 版本号，每次请求时与 JWT 中的版本比对，版本不匹配则直接拒绝。

**`AuthoritiesProvider` / `RedisAuthoritiesProvider`**：权限加载的 SPI，`RedisAuthoritiesProvider` 实现从 Redis 缓存中加载用户权限集，避免每次请求都查数据库。

**`CustomMethodSecurityExpressionRoot` / `CustomMethodSecurityExpressionHandler`**：扩展了 Spring Security 的方法级鉴权，支持自定义 `@PreAuthorize` 表达式，例如 `hasAuthority('sys:user:list')` 这类细粒度权限字符串。

**`MethodSecurityConfig`**：启用方法安全配置，注册自定义表达式处理器。

配置示例（`application.yml`）：
```yaml
gijela:
  security:
    jwt:
      secret: your-secret-key
      expiration: 86400000  # 24h
```

---

## 三、自研 LLM 工具链：gijela-core-llm

这是整个仓库的技术核心，采用 Maven 聚合模块结构，包含 6 个子模块：

```
gijela-core-llm/
├── gijela-core-llm-sdk-core                 # 抽象与模型
├── gijela-core-llm-sdk-openai-compatible    # OpenAI 兼容调用
├── gijela-core-llm-sdk-plugin               # 插件 SPI
├── gijela-core-llm-sdk-observability        # 可观测性
├── gijela-core-llm-sdk-skill                # Skill 机制
└── gijela-core-llm-sdk-mcp                  # MCP 客户端
```

### 3.1 sdk-core：统一抽象层

定义了整个 SDK 的领域对象和接口契约，其他所有模块向上依赖它，但它本身不依赖任何具体实现。

核心接口：

```java
// 同步调用接口
public interface LlmClient {
    ChatResponse chat(ChatRequest request);
}

// 流式调用接口
public interface StreamingLlmClient {
    void streamChat(ChatRequest request, Consumer<ChatResponse> handler);
}

// 工具执行器（Tool Call 回调）
public interface ToolExecutor {
    ToolResult execute(ToolContext context);
}
```

核心模型：

```java
// 请求体
public class ChatRequest {
    private List<ChatMessage> messages;
    private List<Map<String, Object>> tools;
    private Map<String, Object> metadata;  // 携带运行时选项
}

// 响应体
public class ChatResponse {
    private String content;
    private List<ToolCall> toolCalls;
    private TokenUsage usage;  // prompt_tokens / completion_tokens / total_tokens
    private String finishReason;
}

// Token 用量
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {}
```

事件系统：`LlmEvent` + `LlmEventType` + `LlmEventListener`，用于在调用前后广播事件，供可观测性模块订阅。

错误体系：`LlmException` + `LlmErrorCode`，统一封装模型调用错误、超时错误、工具调用失败等场景。

### 3.2 sdk-openai-compatible：OpenAI 兼容调用实现

基于 OkHttp 实现的核心 HTTP 客户端，实现 `LlmClient` 和 `StreamingLlmClient` 接口。

**为什么不用 RestTemplate / WebClient？**  
流式输出（SSE）在 OkHttp 中实现更简洁，可以精确控制 readTimeout（流式时需要设为 0），同时 OkHttp 的连接池和超时配置更直接。

**`OpenAiCompatibleClient` 的核心链路**：

```java
@Override
public ChatResponse chat(ChatRequest request) {
    OpenAiRuntimeOptions runtimeOptions = OpenAiRuntimeOptions.fromMetadata(request.metadata());
    String model = resolveModel(request);
    LlmObservationInterceptor observation = runtimeOptions.observationOrDefault();
    long start = observation.before(model);

    // 1. 构造 OpenAI 格式请求
    Map<String, Object> payload = requestMapper.toOpenAiRequest(request, properties);
    
    // 2. 执行 chat completion
    Map<String, Object> firstRaw = executeChatCompletion(payload);
    
    // 3. 如果有 tool_calls，自动循环执行并续写
    Map<String, Object> finalRaw = maybeContinueWithToolResults(payload, firstRaw, runtimeOptions);
    
    // 4. 响应映射
    ChatResponse response = responseMapper.toChatResponse(finalRaw);
    
    // 5. 可观测性记录
    long latencyMs = observation.after(start);
    recordSuccess(runtimeOptions, model, latencyMs);
    return response;
}
```

亮点是 `maybeContinueWithToolResults`：当模型返回 `tool_calls` 时，会自动调用注册的 `ToolExecutor`，将工具结果拼入消息列表，继续发请求，直到模型返回最终文本（支持多轮 tool loop）。

**`OpenAiRequestMapper`**：把内部 `ChatRequest` 映射为 OpenAI 格式 JSON，负责处理 `system` / `user` / `assistant` / `tool` 角色转换。

**`OpenAiResponseMapper`**：把 OpenAI 响应 JSON 映射为内部 `ChatResponse`，包括 tool_calls 解析、usage 字段提取。

**`SseEventParser`**：处理流式输出，逐行解析 `data: {...}` 格式的 SSE 响应，提取增量 content delta。

**`OpenAiRuntimeOptions`**：运行时选项，从 `ChatRequest.metadata()` 中提取，支持在每次调用时覆盖模型名、temperature、max_tokens 等参数，而无需改全局配置。

配置示例：
```yaml
gijela:
  llm:
    openai:
      base-url: https://api.openai.com/v1
      api-key: sk-xxxx
      model: gpt-4o
      connect-timeout-seconds: 10
      read-timeout-seconds: 120
```

### 3.3 sdk-observability：可观测性

提供三个核心能力：

**`LlmMetricsCollector`**：指标收集接口，默认实现记录调用次数、成功/失败率、响应延迟，可扩展接入 Prometheus / Micrometer。

**`LlmAuditLogger`**：审计日志接口，在每次模型调用前后记录请求摘要（模型名、消息数、token 用量、延迟），用于成本分析和合规审计。

**`BudgetEvent` / `BudgetEventPublisher`**：预算事件机制，当单次调用 token 超出阈值时发布 `BudgetEvent`，上层可订阅并做限流/告警处理。

**`LlmObservationInterceptor`**：拦截器抽象，在 `OpenAiCompatibleClient` 中嵌入，`before()` 记录开始时间戳，`after()` 计算延迟并触发指标上报。

这个模块的设计原则是：**全部接口化，不强依赖具体指标框架**，业务侧可以选择接入 Micrometer 或自定义实现。

### 3.4 sdk-skill：Skill 机制

Skill 是比 Tool Call 更高层的抽象——它描述了一个可被模型调用的"能力单元"，包含完整的名称、描述、参数 schema、执行逻辑。

核心类：

**`SkillManifest`**：Skill 描述对象，包含 name / description / parameters（JSON Schema 格式），可由 `SKILL.md` 文件解析生成，也可由 `SkillProvider` 编程式注册。

**`SkillRegistry`**：Skill 注册表，Spring Bean，维护当前可用的全部 Skill，`OpenAiCompatibleClient` 在构造 tools 参数时从这里获取。

**`SkillProvider`**：Skill 提供者接口，实现类通过 `provide()` 返回一批 `SkillManifest`，Spring 会自动扫描所有实现并注册。

**`LocalSkillPackageLoader`**：从本地文件系统（`skills/` 目录）扫描并加载 `SKILL.md` 文件，解析 Skill 定义。

**`SkillManifestParser`**：解析 `SKILL.md` 文件，提取 Skill 名称、描述和参数 JSON Schema。

**`ScriptSkillProvider`**：支持脚本化 Skill，允许通过外部脚本（如 shell 命令）定义 Skill 执行逻辑。

**`TimeNowSkillProvider` / `EchoSkillProvider`**：内置的两个示例 Skill，前者返回当前时间，后者将输入原样返回，用于快速验证 Skill 链路是否通畅。

**`SkillSchemaBuilder`**：从 `SkillManifest` 生成符合 OpenAI Function Calling 规范的 JSON Schema，供 `tools` 参数使用。

`SKILL.md` 格式示例（来自 `skills/time-now/SKILL.md`）：
```markdown
# time-now

返回当前时间（ISO 8601 格式）。

## Parameters

无参数。
```

### 3.5 sdk-mcp：MCP 客户端实现

MCP（Model Context Protocol）是 Anthropic 提出的工具协议，允许 LLM 通过标准化接口调用外部工具服务。这个模块实现了完整的 MCP Client 侧能力。

**传输层（三种 Transport）**：

```
McpTransport（接口）
├── StreamableHttpTransport  # HTTP POST + Streamable HTTP（新 MCP 标准）
├── SseTransport             # HTTP GET SSE 长连接（旧版 MCP）
└── StdioTransport           # 本地进程 stdin/stdout（本地 MCP Server）
```

- `StreamableHttpTransport`：通过 OkHttp 向 MCP Server 发送 HTTP POST，支持流式响应。
- `SseTransport`：建立 SSE 长连接，监听服务端推送，通过 OkHttp 的 `readTimeout(Duration.ZERO)` 保持连接不超时。
- `StdioTransport`：启动本地子进程（命令行 MCP Server），通过 stdin/stdout 通信，支持命令白名单 `stdioCommandWhitelist` 防止任意命令执行。

**`McpJsonRpcClient`**：JSON-RPC 2.0 协议实现，封装了 MCP 的三个核心操作：
- `initialize`：握手，交换 protocol 版本和能力
- `tools/list`：获取 MCP Server 暴露的工具列表
- `tools/call`：调用指定工具并获取结果

**`McpSession`**：会话管理，维护与单个 MCP Server 的连接状态，包括初始化结果 `McpInitializeResult`。

**`McpRpcCodec`**：JSON-RPC 消息的编解码器，处理 request/response/notification 三种消息类型。

**`McpSkillSync`**：MCP 与 Skill 体系的桥接，通过 `McpToolBindingSource` 获取需要同步的 MCP Server 配置，调用 `tools/list` 获取工具清单，自动转换为 `SkillManifest` 并注册到 `SkillRegistry`，之后模型就能通过 Skill 机制调用 MCP 工具。

**`McpSdkAutoConfiguration`** 的自动装配逻辑：
```java
@AutoConfiguration(after = SkillSdkAutoConfiguration.class)  // 依赖 Skill 先初始化
@EnableConfigurationProperties(McpSdkProperties.class)
public class McpSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpJsonRpcClient mcpJsonRpcClient(McpSdkProperties mcp) {
        // 三种 Transport 注册到 McpClientFactory
        // 各自配置独立的超时（SSE 连接不设 readTimeout）
    }

    @Bean
    @ConditionalOnBean(McpToolBindingSource.class)  // 业务侧需要提供绑定配置
    @ConditionalOnMissingBean
    public McpSkillSync mcpSkillSync(...) { ... }
}
```

配置示例：
```yaml
gijela:
  llm:
    mcp:
      connect-timeout-seconds: 10
      read-timeout-seconds: 60
      call-timeout-seconds: 120
      allow-stdio: true
      stdio-command-whitelist:
        - "node"
        - "python"
      stdio-startup-timeout-seconds: 10
```

### 3.6 sdk-plugin：插件 SPI

提供热插拔式的能力扩展机制：

**`Plugin`**：插件接口，定义 `PluginManifest` 元信息（名称、版本、描述）和 `PluginCapability` 能力集。

**`PluginLifecycle`**：插件生命周期接口，包含 `onLoad()` / `onUnload()` 钩子，允许插件在加载/卸载时做初始化和清理。

**`PluginRegistry`**：插件注册中心，维护所有已加载插件，支持按能力类型查询、动态注册和注销。

**`PluginState`**：插件状态枚举（`LOADED` / `ACTIVE` / `ERROR` / `UNLOADED`），用于管控插件生命周期。

**`PluginCapability`**：能力标记接口，不同类型的插件实现不同的 Capability 子接口，注册表通过 Capability 类型做能力发现。

---

## 四、聊天联调服务：gijela-core-chat

这个模块不是 Demo，而是专门为验证 `gijela-core-llm` 能否在真实 BFF 场景中正确工作而建立的联调服务。

### 4.1 Controller 层

- **`ChatController`**：核心聊天接口，接收前端请求，通过 `OpenAiChatAdapter` 调用底层 LLM，支持流式（SSE）和非流式两种响应模式。
- **`ChatModelConfigController`**：模型配置管理接口，支持动态管理多个 LLM 接入配置（不同模型、不同 base-url）。
- **`ChatPromptController`**：Prompt 模板管理，支持系统提示词的增删改查与版本管理。
- **`SkillAdminController`**：Skill 管理接口，列举当前已注册的 Skill，用于前端展示和测试调用。
- **`McpAdminController`**：MCP 管理接口，查看当前绑定的 MCP Server、工具列表、同步状态。
- **`ChatHealthController`**：健康检查与依赖检测接口。

### 4.2 适配器层（adapter/）

**`OpenAiChatAdapter`**：在 Chat 模块的业务上下文中对 `OpenAiCompatibleClient` 做二次封装：
- 注入 `ChatAuditService` 记录业务侧会话审计
- 处理 `LlmEvent` 事件回调（`LlmEventType.TOOL_CALL_START` / `TOOL_CALL_END` 等）
- 将 `ChatMessage` 列表与 Redis 中的历史上下文合并
- 支持多轮工具调用的消息序列拼接

### 4.3 LLM 层（llm/）

独立于 adapter 的 LLM 能力抽象层，包含 RAG、知识检索、附件处理等能力的协议定义，具体实现通过各 repository 注入。

### 4.4 存储层（repository/）

- **MySQL + MyBatis-Plus**：持久化会话、消息、Prompt 配置等业务数据
- **Redis**：存储会话上下文（`conversation_id → message list`），支持 TTL 自动过期
- **Qdrant**：向量数据库，支持 RAG 场景的语义检索
- **Elasticsearch**：全文检索或可观测性扩展
- **Neo4j**：图谱能力验证

### 4.5 依赖基础设施

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gijela_chat
  data:
    redis:
      host: localhost
      port: 6379

gijela:
  llm:
    openai:
      base-url: https://api.openai.com/v1
      api-key: sk-xxxx
      model: gpt-4o
  qdrant:
    host: localhost
    port: 6334
```

---

## 五、工作流执行引擎：gijela-core-chat-flow

这个模块的目标是验证：**LLM 能力能否从"单次调用"升级为"流程化、可追踪"的执行体系**。

### 5.1 数据模型

```java
// 工作流定义（元信息）
@TableName("chatflow_workflow")
public class WorkflowEntity {
    private Long id;
    private String name;
    private String description;
    private String status;     // DRAFT / PUBLISHED
    private Long publishedVersionId;
}

// 版本化的工作流定义（包含节点/边 JSON）
@TableName("chatflow_workflow_version")
public class WorkflowVersionEntity {
    private Long workflowId;
    private Integer version;
    private String graphJson;   // 节点和边的 JSON 序列化
    private String status;      // DRAFT / PUBLISHED
}

// 运行记录
@TableName("chatflow_workflow_run")
public class WorkflowRunEntity {
    private Long workflowId;
    private Long versionId;
    private String runMode;     // DEBUG / PRODUCTION
    private String status;      // PENDING / RUNNING / SUCCESS / FAILED
    private String inputJson;
    private String outputJson;
    private Long startTime;
    private Long endTime;
}

// 节点级运行记录
@TableName("chatflow_workflow_run_node")
public class WorkflowRunNodeEntity {
    private Long runId;
    private String nodeId;
    private String nodeType;    // START / LLM / END
    private String status;
    private String inputJson;
    private String outputJson;
    private Long startTime;
    private Long endTime;
}
```

### 5.2 工作流引擎（WorkflowEngine）

核心执行器，负责：
1. 解析 `graphJson`，构建节点有向图
2. 按拓扑顺序遍历节点，依次调用对应的 `NodeExecutor`
3. 维护 `RunContext`（节点间数据传递）
4. 记录每个节点的执行结果到 `WorkflowRunNodeEntity`
5. 异常时标记节点和整体运行状态为 `FAILED`

```java
public class WorkflowEngine {
    private final NodeExecutorRegistry registry;

    public WorkflowEngineResult execute(WorkflowVersionEntity version, RunContext context) {
        // 1. 解析 graphJson
        // 2. 拓扑排序
        // 3. 逐节点执行
        for (NodeDefinition node : sortedNodes) {
            NodeExecutor executor = registry.getExecutor(node.getType());
            NodeExecuteResult result = executor.execute(node, context);
            context.putNodeResult(node.getId(), result);
            recordNodeResult(runId, node, result);
            if (!result.isSuccess()) break;
        }
        return buildEngineResult(context);
    }
}
```

### 5.3 节点执行器（NodeExecutor SPI）

```java
public interface NodeExecutor {
    String supportType();          // 声明支持的节点类型
    NodeExecuteResult execute(NodeDefinition node, RunContext context);
}
```

三个内置实现：

**`StartNodeExecutor`**：解析用户输入，初始化 `RunContext`，填入 `input` 变量。

**`LlmNodeExecutor`**：核心节点，从节点配置中取出 prompt 模板，替换上下文变量，调用 `OpenAiCompatibleClient.chat()`，将响应写入 `RunContext`。支持从节点配置覆盖模型参数（model / temperature / max_tokens）。

**`EndNodeExecutor`**：从 `RunContext` 取出 `output` 变量，构建最终 `WorkflowEngineResult`。

**`NodeExecutorRegistry`**：扫描所有 `NodeExecutor` Spring Bean，按 `supportType()` 建立映射，工作流引擎通过此 Registry 动态分派。

### 5.4 API 层

**`WorkflowController`**：
- `POST /workflows` — 创建工作流
- `PUT /workflows/{id}/draft` — 保存草稿（不影响已发布版本）
- `POST /workflows/{id}/publish` — 发布工作流（创建新版本）
- `GET /workflows/{id}` — 获取工作流详情（含草稿 JSON）
- `POST /workflows/{id}/validate` — 校验工作流合法性（节点连通性、必填参数）

**`ChatflowConversationController`**：
- `POST /chatflow/completions` — 基于工作流发起对话（触发一次完整的 Engine 执行）
- `GET /chatflow/sessions/{sessionId}/messages` — 获取会话消息历史

**`WorkflowController`** 的调试运行接口：
- `POST /workflows/{id}/debug-run` — 调试模式运行（同步返回，不保存正式运行记录）

### 5.5 版本与草稿机制

每次发布会生成一个新的 `WorkflowVersionEntity`，版本号递增。草稿修改不影响已发布版本，正式运行始终使用最新发布版本，这样保证了**在线运行稳定性**与**设计迭代自由**不相互干扰。

---

## 六、企业后台：gijela-core-pistil

### 6.1 模块定位

提供标准企业后台的骨架能力：用户、角色、菜单、部门、岗位、审计日志。为将来 AI 能力接入正式业务系统提供承载环境，而不是每次从零搭。

### 6.2 Controller 层

| Controller | 核心接口 |
|---|---|
| `AuthController` | 登录、登出、刷新 Token、获取当前用户信息与权限 |
| `UserController` | 用户增删改查、重置密码、批量操作、分页列表 |
| `RoleController` | 角色增删改查、菜单权限分配、用户角色关联 |
| `MenuController` | 菜单树查询、动态路由生成、菜单增删改 |
| `DeptController` | 部门树管理（递归结构） |
| `PostController` | 岗位管理 |
| `AuditLogController` | 审计日志分页查询、导出 |

### 6.3 RBAC 权限模型

```
User → Role（多对多）
Role → Menu（多对多，Menu 上挂 permission 字符串，如 sys:user:list）
```

鉴权流程：
1. 登录时将用户权限字符串集合写入 Redis（key: `auth:authorities:{userId}`）
2. `JwtAuthenticationFilter` 在每次请求时从 Redis 加载权限集
3. 方法上标注 `@PreAuthorize("hasAuthority('sys:user:list')")`
4. `CustomMethodSecurityExpressionRoot` 处理自定义表达式逻辑

### 6.4 动态菜单

前端通过 `/menus/routes` 接口获取当前用户有权限的路由树，不依赖前端静态路由配置，菜单增删改后前端无需发布即可生效。

### 6.5 审计日志

通过 Spring AOP 在 Controller 层切面，记录每次 API 操作的：
- 请求 URL、HTTP 方法
- 操作用户、操作时间
- 请求参数摘要（脱敏）
- 操作结果（成功/失败）

---

## 七、前端体系：gijela-bloom

三个 Vue 3 子应用，技术栈一致：Vue 3 + TypeScript + Vite + Pinia + Axios + Element Plus。

### 7.1 gijela-bloom-pistil（管理后台，端口 5173）

- 登录页 → JWT 存 localStorage → Axios 拦截器自动携带 Authorization Header
- 动态路由：登录后请求 `/menus/routes`，动态 `addRoute` 注入路由树
- 权限指令：`v-permission="'sys:user:list'"` 控制按钮级别的显示/隐藏
- Pinia Store：`userStore`（用户信息）、`permissionStore`（权限集合）、`menuStore`（菜单树）

### 7.2 gijela-bloom-chat（聊天联调，端口 5174）

- SSE 流式渲染：使用 `EventSource` 或 `fetch + ReadableStream` 消费流式 API
- Markdown 渲染：支持代码块高亮
- Skill 面板：展示当前可用 Skill 列表，支持手动触发测试调用
- MCP 状态展示：查看已连接的 MCP Server 和工具列表

### 7.3 gijela-bloom-chat-flow（工作流编排，端口 5175）

- **Vue Flow** 集成：可视化节点画布，支持拖拽添加节点、连线
- 节点配置面板：点击节点弹出配置抽屉，LLM 节点可配置 prompt 模板、模型参数
- 草稿自动保存：防抖 + 定时保存 draft
- 调试模式：单步运行，实时显示每个节点的输入/输出
- 运行历史：展示历次运行的节点执行 timeline 和最终结果

---

## 八、基础设施与 Docker 编排

所有第三方依赖均有 docker-compose 编排：

```yaml
# docker/docker-compose-mysql-elasticsearch-qdrant-neo4j-redis-ik.yml
services:
  mysql-8:
    image: mysql:8.4.7
    ports: ["3306:3306"]
  redis:
    image: redis:7.2
    ports: ["6379:6379"]
  elasticsearch:
    image: elasticsearch:8.19.4
    ports: ["9200:9200"]
  kibana:
    image: kibana:8.19.4
    ports: ["5601:5601"]
  qdrant:
    image: qdrant/qdrant
    ports: ["6333:6333", "6334:6334"]
  neo4j:
    image: neo4j:latest
    ports: ["7474:7474", "7687:7687"]
```

对象存储使用 RustFS（S3 兼容），包含自动创建 bucket 的 init 容器：

```yaml
# docker/docker-compose-rustfs.yml
services:
  rustfs:
    image: rustfs/rustfs
    ports: ["9000:9000", "9001:9001"]
  rustfs-init:
    image: minio/mc    # 复用 mc 客户端做 bucket 创建
    depends_on: [rustfs]
    entrypoint: ["/bin/sh", "-c", "mc alias set ... && mc mb ..."]
```

---

## 九、技术选型对比与决策

| 决策点 | 选择 | 替代方案 | 选择原因 |
|---|---|---|---|
| LLM HTTP 客户端 | OkHttp | RestTemplate / WebClient | 流式控制更精细，连接池配置更直接，SSE readTimeout 需设为 0 |
| LLM 框架 | 自研 | LangChain4j / Spring AI | 深度理解 MCP/Skill 内部机制，不受外部版本迭代约束 |
| 向量数据库 | Qdrant | Milvus / PGVector | 轻量，Docker 部署简单，gRPC + HTTP 双协议 |
| 工作流节点 | 自研引擎 | Flowable / Activiti | 专为 LLM 场景设计，无 BPMN 包袱 |
| 对象存储 | RustFS | MinIO | S3 兼容，Rust 实现性能较好，适合本地开发 |
| 前端状态管理 | Pinia | Vuex | Vue 3 官方推荐，TypeScript 支持更好 |
| 可视化流程 | Vue Flow | X6 / ReactFlow | Vue 生态原生，集成成本低 |

---

## 十、模块依赖关系总结

```
gijela-bloom-pistil ──────────────────────────────────► gijela-core-pistil
                                                              │
gijela-bloom-chat ─────────────────────────────────────► gijela-core-chat
                                                              │
gijela-bloom-chat-flow ──────────────────────────────► gijela-core-chat-flow
                                                              │
                                               ┌─────────────┤
                                               │             │
                                    gijela-core-llm     gijela-core-common
                                    ├── sdk-core         gijela-core-security-common
                                    ├── sdk-openai
                                    ├── sdk-skill
                                    ├── sdk-mcp
                                    ├── sdk-plugin
                                    └── sdk-observability
```

---

## 十一、总结

gijela 不是一个做完了的产品，而是一套**工程化实践路径**的验证：

1. **`gijela-core-llm`**：先把 Java 侧的大模型调用基础设施做扎实——协议抽象、HTTP 调用、Tool Call、Skill 注册、MCP 客户端、可观测性，都有清晰的模块边界。

2. **`gijela-core-chat`**：把 SDK 放进真实 BFF 服务去跑，验证会话、RAG、MCP、Skill、附件、多种存储的协同是否正常——这是 SDK 的完整性保障。

3. **`gijela-core-chat-flow`**：把 LLM 能力推进到流程化执行，WorkflowEngine + NodeExecutor SPI + 版本草稿机制，构建了最小但完整的工作流引擎闭环。

4. **`gijela-core-pistil`**：提供标准企业后台骨架，JWT + Redis Token 版本机制 + RBAC + 动态菜单 + 审计日志，为 AI 能力接入正式系统做好承载准备。

> 项目地址：
> - GitHub：https://github.com/wojiaozhangtudou/gijela
> - Gitee：https://gitee.com/zhangjq123/gijela
