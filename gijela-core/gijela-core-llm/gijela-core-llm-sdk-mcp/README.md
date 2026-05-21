# gijela-core-llm-sdk-mcp

LLM MCP（Model Context Protocol）SDK：JSON-RPC 客户端、三种 transport（streamable HTTP / SSE / stdio）、MCP 工具→Skill 同步桥的可复用模块。

## 1. 模块定位

- **角色**：MCP 协议客户端 + MCP 工具到 SkillRegistry 的反向同步桥。
- **依赖**：`gijela-core-llm-sdk-core` + `gijela-core-llm-sdk-skill` + OkHttp 4 (with okhttp-sse) + Jackson；**不依赖任何业务模块**。
- **被依赖**：`gijela-core-chat`（业务侧消费方，提供 `McpToolBindingSource` 实现）。

依赖方向严格单向：

```
sdk-core ← sdk-skill ← sdk-mcp ← chat
```

## 2. 配置项

YAML 前缀：`gijela.llm.mcp`

| 字段 | 默认值 | 含义 |
|---|---|---|
| `connect-timeout-seconds` | `5` | HTTP 连接超时 |
| `read-timeout-seconds` | `30` | HTTP 读超时 |
| `call-timeout-seconds` | `30` | HTTP 整体调用超时 |
| `allow-stdio` | `false` | 是否允许 stdio transport（生产建议关闭） |
| `stdio-command-whitelist` | `[]` | stdio 命令白名单（空 = 任意命令，仅 `allow-stdio=true` 时生效） |
| `max-tools-exposed` | `128` | 单租户暴露给 LLM 的最大 MCP 工具数量上限 |
| `sync-cron` | （空） | 兜底定时刷新 cron 表达式（空 = 不定时刷新） |

示例 `application.yml`：

```yaml
gijela:
  llm:
    mcp:
      connect-timeout-seconds: 5
      read-timeout-seconds: 60
      call-timeout-seconds: 60
      allow-stdio: false
      max-tools-exposed: 256
```

> 业务侧专属字段（如 `cipherKey`、`allowPrivateNetwork`）保留在业务自己的 properties 中，不属于本 SDK。

## 3. 自动装配

通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册：

`McpSdkAutoConfiguration`（在 `SkillSdkAutoConfiguration` 之后装配）提供：

| Bean | 条件 | 说明 |
|---|---|---|
| `McpJsonRpcClient` | 默认 | 三 transport 复合 JSON-RPC 客户端 |
| `McpSkillSync` | `@ConditionalOnBean(McpToolBindingSource.class)` | MCP 工具 → SkillRegistry 同步器 |

业务侧**只需提供** `McpToolBindingSource` Bean（数据源 SPI），SDK 自动完成余下装配。

## 4. 关键 API

| 类型 | 路径 | 说明 |
|---|---|---|
| `McpJsonRpcClient` | `com.gijela.morpheus.llm.sdk.mcp.client.McpJsonRpcClient` | JSON-RPC 客户端门面（initialize / listTools / callTool） |
| `McpEndpointConfig` | `com.gijela.morpheus.llm.sdk.mcp.client.McpEndpointConfig` | endpoint 配置（http / sse / stdio 工厂方法） |
| `McpClientException` | `com.gijela.morpheus.llm.sdk.mcp.client.McpClientException` | 客户端统一异常 |
| `McpTransport` | `com.gijela.morpheus.llm.sdk.mcp.client.McpTransport` | transport SPI |
| `StreamableHttpTransport` / `SseTransport` / `StdioTransport` | `com.gijela.morpheus.llm.sdk.mcp.client.transport` | 三种 transport 实现 |
| `McpToolBindingSource` | `com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBindingSource` | **业务侧实现的 SPI**：返回 `List<McpToolBinding>` |
| `McpToolBinding` | `com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBinding` | 单个 MCP 工具绑定（含 endpoint + schema） |
| `McpSkillSync` | `com.gijela.morpheus.llm.sdk.mcp.skill.McpSkillSync` | 同步入口：`syncAll()` / `initOnStartup()` |
| `McpToolSkillProvider` | `com.gijela.morpheus.llm.sdk.mcp.skill.McpToolSkillProvider` | MCP 工具执行 → Skill 适配器 |

## 5. 业务侧集成模式

### 5.1 提供数据源（必需）

```java
public class MyMcpServerService implements McpServerService, McpToolBindingSource {
    @Override
    public List<McpToolBinding> loadActiveBindings(String tenantId) {
        // 从 DB / 配置 / 远程读取启用中的 MCP servers，
        // 拉出 tools_cache_json，解析成 McpToolBinding 列表
        return ...;
    }
}
```

### 5.2 暴露 SPI Bean（必需）

```java
@Bean
McpToolBindingSource mcpToolBindingSource(McpServerService svc) {
    return (McpToolBindingSource) svc; // SDK 通过此 Bean 拉取数据
}
```

### 5.3 启动触发首次同步（推荐）

```java
@Bean
ApplicationRunner mcpSkillBootstrap(McpSkillSync sync) {
    return args -> sync.initOnStartup();
}
```

### 5.4 业务变更时主动同步

在 create / update / delete / toggle / refreshTools 等业务动作完成后调用 `mcpSkillSync.syncAll()`，使新工具立即对 LLM 可见。

## 6. transport 选择

| transport | 适用场景 | 配置 |
|---|---|---|
| `streamable_http` | 生产首选，HTTP/2 长连接 | `endpoint=https://...`, `auth-type=bearer` |
| `sse` | 兼容旧 MCP server | `endpoint=https://.../sse`, 同上 |
| `stdio` | 本地开发 / 内置工具 | `command + args + env`，**生产默认禁用** |

## 7. 测试基线

模块测试（`mvn -pl gijela-core-llm-sdk-mcp test`）：

- `McpJsonRpcClientTest` × 8
- `StdioTransportTest` × 6
- `McpSkillSyncTest` × 6
- `McpToolSkillProviderTest` × 8

合计 **28 / 28 通过**。

## 8. 变更追溯

- **来源**：从 `gijela-core-chat/adapter/mcp/{client,skill}` 拆出（ARCH-REVIEW-20260501-002 / 003）。
- **冻结决策**：ARCH-REVIEW-20260501-003。
- **生产级 SDK 拆分阶段 3**：完成于 2026-05-01，`yml` 前缀由 `chat.mcp.*` 迁移为 `gijela.llm.mcp.*`，包路径由 `com.gijela.morpheus.chat.adapter.mcp.*` 迁移为 `com.gijela.morpheus.llm.sdk.mcp.*`，无历史兼容。
