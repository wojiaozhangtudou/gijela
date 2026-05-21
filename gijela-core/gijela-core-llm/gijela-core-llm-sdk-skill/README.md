# gijela-core-llm-sdk-skill

LLM Skill SDK：技能注册、技能契约、本地技能加载、技能执行的可复用模块。

## 1. 模块定位

- **角色**：LLM 工具/技能（Tool / Skill）抽象层。
- **依赖**：仅依赖 `gijela-core-llm-sdk-core` 与 `gijela-core-llm-sdk-observability`；**不依赖任何业务模块**。
- **被依赖**：`gijela-core-llm-sdk-mcp`（在此之上提供 MCP 工具→Skill 适配）、`gijela-core-chat`（业务侧消费方）。

依赖方向严格单向：

```
sdk-core ← sdk-skill ← sdk-mcp ← chat
```

## 2. 配置项

YAML 前缀：`gijela.llm.skill`

| 字段 | 默认值 | 含义 |
|---|---|---|
| `enabled` | `true` | 是否启用 SkillRegistry 自动装配 |
| `local-package-path` | `skills/` | 本地技能包扫描路径（相对 classpath / 工作目录） |
| `max-active-skills` | `64` | SkillRegistry 中允许同时激活的最大技能数量 |
| `default-timeout-millis` | `30000` | 单次 Skill 调用默认超时（毫秒） |

示例 `application.yml`：

```yaml
gijela:
  llm:
    skill:
      enabled: true
      local-package-path: skills/
      max-active-skills: 128
      default-timeout-millis: 60000
```

## 3. 自动装配

通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册：

- `SkillSdkAutoConfiguration` — 提供 `SkillRegistry` Bean（基于 `SkillSdkProperties`）。

业务侧只需引入依赖，**无需手动声明 `@ComponentScan` 或 Bean**。

## 4. 关键 API

| 类型 | 路径 | 说明 |
|---|---|---|
| `SkillRegistry` | [com.gijela.morpheus.llm.sdk.skill.SkillRegistry] | 技能注册中心，支持注册 / 注销 / 查找 / 替换分组 |
| `SkillProvider` | [com.gijela.morpheus.llm.sdk.skill.SkillProvider] | 技能执行 SPI |
| `ToolCall` / `ToolResult` / `ToolContext` | `com.gijela.morpheus.llm.sdk.skill.*` | 工具调用契约（冻结：SPEC-20260430-004） |
| `LocalSkillPackageLoader` | `com.gijela.morpheus.llm.sdk.skill` | 本地 SKILL.md 包加载器 |
| `SkillManifestParser` | `com.gijela.morpheus.llm.sdk.skill` | SKILL.md frontmatter 解析（冻结：SPEC-20260430-005） |

## 5. 注册模式

业务侧典型用法：

```java
@Bean
ApplicationRunner skillBootstrap(SkillRegistry registry) {
    return args -> {
        registry.register(new MyLocalSkillProvider());
        // 或者批量替换某 source 分组
        registry.replaceBySource("mcp", List.of(...));
    };
}
```

## 6. 测试基线

模块测试（`mvn -pl gijela-core-llm-sdk-skill test`）：

- `LocalSkillPackageLoaderTest` × 1
- `SkillManifestParserTest` × 4
- `SkillRegistryLocalJavaSkillTest` × 1
- `SkillRegistryReplaceMcpTest` × 6

合计 **12 / 12 通过**。

## 7. 变更追溯

- **来源**：从 `gijela-core-chat` 拆出（ARCH-REVIEW-20260501-002 / 003）。
- **冻结决策**：ARCH-REVIEW-20260501-003。
- **生产级 SDK 拆分阶段 2**：完成于 2026-05-01，`yml` 前缀由 `chat.skill.*` 迁移为 `gijela.llm.skill.*`，无历史兼容。
