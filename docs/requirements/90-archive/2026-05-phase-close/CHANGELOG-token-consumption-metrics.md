# Token 消费指标完整实现

## 版本
- **实施日期**: 2026-05-08
- **状态**: ✅ 完成并通过测试

## 概述
完整实现了大模型流式对话中 token 消费信息的透传、展示和统计聚合，从后端 SDK → 中间层 → 前端 UI 形成完整链路。

## 核心改动

### 后端（Java / Spring Boot）

#### 1. SDK 层 - Token 信息定义和解析
- **LlmEvent.java**: 新增 `TokenUsage usage` 字段，定义 `promptTokens`, `completionTokens`, `totalTokens`
- **SseEventParser.java**: 
  - 新增 `parseUsage()` 方法解析 OpenAI SSE chunk 中的 usage 信息
  - 维护 `latestUsage` 状态变量
  - 在 DONE 事件时挂载最终 usage 数据
- **OpenAiRequestMapper.java**: 流式模式下添加 `stream_options.include_usage=true` 请求参数

#### 2. 聊天编排层 - Token 透传和日志落库
- **ChatEventVO.java**: 新增 `ChatUsageDTO usage` 字段（第 8 个参数）
- **OpenAiChatAdapter.java**: 
  - 在 `mapEvent()` DONE 事件处理中调用 `toUsage()` 转换
  - 所有 ChatEventVO 构造加 usage 参数
- **ChatOrchestratorServiceImpl.java**: 
  - stream done/error 事件时透传 usage 到日志发布
  - `publishRuntimeLog()` 和 `publishAccessLog()` 接收 usage 参数

#### 3. 查询聚合层 - Token 指标统计
- **EsQueryBuilder.java** (`appendMetricAggregation()` 方法):
  ```java
  case "totalTokens" -> histogram.subAggregation(AggregationBuilders.sum("metric_value").field(field));
  ```
  对时间窗口内所有请求的 totalTokens 字段进行求和聚合

#### 4. 测试修复
- **ChatOrchestratorServiceImplTest.java**: 修复 11 处 ChatEventVO 构造器调用，补齐 usage 参数
- **ChatControllerTest.java**: 修复 1 处 ChatEventVO 构造器调用

### 前端（Vue3 / TypeScript）

#### 1. 状态管理
- **store/chat.ts**: 
  - 新增 `pendingUsage` ref 管理待发送消息的 token 信息
  - `consumeEvent()` 中同步更新 usage
  - 返回体暴露 pendingUsage

#### 2. 聊天气泡显示
- **ChatWindow.vue**:
  - props 新增 `pendingUsage?: ChatUsage | null`
  - 新增 `formatUsage()` 函数生成可读格式: "Tokens：总 X / 输入 Y / 输出 Z"
  - 在 message-meta 中显示 usage 信息

#### 3. SRE 看板统计指标
- **Overview.vue**:
  - 新增 `totalTokensSeries` ref 存储时序数据
  - KPI 卡片数组扩展为 5 项，第 5 项为 "消费 Token 数"
  - `loadData()` 中添加 `queryTimeseries({metric: 'totalTokens', interval: '1h'})` 调用
  - 新增 `totalTokensSum()` 函数计算总消费量
  - 错误处理中清空 totalTokensSeries

#### 4. 告警规则支持
- **Alerts.vue**:
  - 指标类型下拉框新增选项: `<el-option label="消费 Token 数" value="totalTokens" />`

## 数据流

```
┌─────────────────────┐
│ OpenAI 流式响应      │
│ (usage 信息)        │
└──────────┬──────────┘
           │
           ↓
┌─────────────────────┐
│ SseEventParser 解析 │
│ (latestUsage 追踪)  │
└──────────┬──────────┘
           │
           ↓
┌──────────────────────────────┐
│ ChatEventVO 构造             │
│ (携带 usage 字段)            │
└──────────┬───────────────────┘
           │
           ├─→ [前端 Store]
           │   └─→ pendingUsage 更新
           │
           ├─→ [聊天 UI]
           │   └─→ ChatWindow 显示 token
           │
           └─→ [日志系统]
               └─→ AccessLog / RuntimeLog
                   └─→ ES 索引 (totalTokens 字段)
                       └─→ SRE 看板聚合
                           └─→ Overview KPI 第 5 项
```

## 验证清单

- ✅ 后端编译通过 (`mvn clean compile -DskipTests`)
- ✅ 73 个单元测试通过
- ✅ JAR 打包成功
- ✅ 前端编译通过 (`pnpm build`)
- ✅ 测试文件已修复

## 启动验证

### 后端启动
```bash
cd D:\workspace\gijela\gijela-core\gijela-core-chat
java -Dspring.profiles.active=dev -jar target/gijela-core-chat-1.0.0.jar
```

### 前端启动
```bash
cd D:\workspace\gijela\gijela-bloom\gijela-bloom-pistol
pnpm dev
```

## 预期行为

1. **聊天页**：每条消息显示 `Tokens：总 X / 输入 Y / 输出 Z`
2. **SRE 看板 Overview**：KPI 卡片显示 "消费 Token 数" 和历史趋势图
3. **告警规则**：可以基于 "消费 Token 数" 指标创建告警

## 技术要点

- **流式协议**: 需显式启用 `stream_options.include_usage=true` 获取 token 统计
- **数据聚合**: 后端使用 ES sum aggregation 对时间窗口内的 totalTokens 求和
- **容错处理**: 若 usage 缺失，前端使用默认值，日志记录仍正常进行
- **性能**: Token 信息透传无额外网络开销（随流式数据包一起发送）

## 后续优化空间

1. 告警通知模板中显示 token 消费量
2. 用户侧查看 token 消费统计报告
3. token 消费成本计算和账单生成
4. token 配额管理和限流

---
**实施者**: GitHub Copilot  
**验证状态**: 后端编译 + 测试 ✅ | 前端构建 ✅
