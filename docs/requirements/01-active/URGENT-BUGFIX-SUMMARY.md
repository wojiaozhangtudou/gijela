# 🔴 Phase 1 代码审查 - 紧急修复清单

**审查日期**：2026-05-19 16:00  
**发现问题数**：3 个（2 个 P0 + 1 个 P1）  
**预计修复时间**：2-4 小时  
**建议**：⛔ 暂停集成测试与 Phase 2，立即修复这些 Bug

---

## 一、问题摘要

| 问题ID | 标题 | 优先级 | 状态 | 预估工作量 |
|--------|------|--------|------|-----------|
| BUG-20260519-002 | 字段名不一致（query vs question） | 🔴 P0 | new | 30 分钟 |
| BUG-20260519-003 | 缺少系统变量注入（sys.conversation_id） | 🔴 P0 | new | 15 分钟 |
| BUG-20260519-004 | 异常处理不完整 | 🟠 P1 | new | 30 分钟 |

---

## 二、快速修复指南

### 修复 1：字段名不一致 + 系统变量注入（合并修复）

**文件**：`gijela-core-chat-flow/src/main/java/com/gijela/morpheus/chatflow/service/impl/ChatflowConversationServiceImpl.java`

**行号**：197-199（completion 方法内）

**当前代码**（错误）：
```java
Map<String, Object> inputs = new LinkedHashMap<>();
inputs.put("question", content);                    // ❌ 应为 "query"
inputs.put("history", buildHistoryJson(...));      // ❌ 应为 "chat_history"
inputs.putAll(readMap(session.getWorkflowInputs())); // 用户输入
```

**修复后代码**（正确）：
```java
Map<String, Object> inputs = new LinkedHashMap<>();
// 系统变量（自动注入）
inputs.put("sys.query", content);
inputs.put("sys.chat_history", buildHistoryJson(session.getSessionId(), DEFAULT_HISTORY_LIMIT));
inputs.put("sys.conversation_id", session.getSessionId());
// 用户输入（可覆盖系统变量）
inputs.putAll(readMap(session.getWorkflowInputs()));
```

**影响范围**：同一个改动，同时解决 BUG-20260519-002 和 BUG-20260519-003

---

### 修复 2：异常处理不完整

**文件**：`gijela-core-chat-flow/src/main/java/com/gijela/morpheus/chatflow/controller/ChatflowConversationController.java`

**行号**：82-92（completion 方法）

**当前代码**（不完整）：
```java
@PostMapping("/completions")
public ApiResponse<ChatflowCompletionVO> completion(@RequestBody @Valid ChatflowCompletionDTO dto) {
    try {
        return ApiResponse.ok(conversationService.completion(dto));
    } catch (NoSuchElementException ex) {
        return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
    } catch (IllegalArgumentException ex) {
        return ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage(), null);
    } catch (IllegalStateException ex) {
        return ApiResponse.fail(ErrorCode.ILLEGAL_STATE.getCode(), ex.getMessage(), null);
    }
    // ❌ 缺少其他异常捕获！
}
```

**修复后代码**（完整）：
```java
@PostMapping("/completions")
public ApiResponse<ChatflowCompletionVO> completion(@RequestBody @Valid ChatflowCompletionDTO dto) {
    try {
        return ApiResponse.ok(conversationService.completion(dto));
    } catch (NoSuchElementException ex) {
        return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
    } catch (IllegalArgumentException ex) {
        return ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage(), null);
    } catch (IllegalStateException ex) {
        return ApiResponse.fail(ErrorCode.ILLEGAL_STATE.getCode(), ex.getMessage(), null);
    } catch (JsonProcessingException ex) {
        logger.error("工作流输入序列化失败", ex);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(), "数据处理失败", null);
    } catch (TimeoutException ex) {
        logger.warn("工作流执行超时");
        return ApiResponse.fail(ErrorCode.TIMEOUT.getCode(), "工作流执行超时（60秒）", null);
    } catch (Exception ex) {
        logger.error("工作流执行异常", ex);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(), "服务内部错误", null);
    }
}
```

**新增导入**（检查是否需要）：
```java
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.concurrent.TimeoutException;
```

---

## 三、验证步骤

### 第 1 步：修改代码
- 修改 ChatflowConversationServiceImpl.java（修复 1）
- 修改 ChatflowConversationController.java（修复 2）

### 第 2 步：编译验证
```bash
cd d:\workspace\gijela\gijela-core\gijela-core-chat-flow
mvn clean compile
```
✅ 应该显示：`BUILD SUCCESS`

### 第 3 步：前端影响检查
```bash
cd d:\workspace\gijela\gijela-bloom\gijela-bloom-chat-flow
pnpm build
```
✅ 应该显示：`built in X.XXs`（前端不受后端改动影响）

### 第 4 步：集成测试（待编写）
- 创建测试工作流，验证接收到正确的字段：`sys.query`、`sys.chat_history`、`sys.conversation_id`
- 测试超时异常：验证返回 timeout 错误码而不是 500
- 测试 JSON 异常：传入无法序列化的输入，验证返回正确错误码

---

## 四、关键提醒

### ⚠️ 不要遗漏的地方

1. **Import 检查**：确保添加了 JsonProcessingException 和 TimeoutException 的导入
2. **字段名的 sys. 前缀**：注意不是 `query` 而是 `sys.query`
3. **conversation_id 来源**：使用 `session.getSessionId()`，不是其他值
4. **用户输入优先级**：确保 `putAll(readMap(...))` 在系统变量之后，以便用户可覆盖

### 🟢 修复完成的标志

- ✅ mvn compile 成功
- ✅ 工作流能接收 `sys.query` 等字段
- ✅ 异常被正确捕获（不返回 500）
- ✅ 日志正确记录（logger.error/warn）

---

## 五、详细文档位置

| 文档 | 用途 |
|------|------|
| [CODE-REVIEW-20260519-001.md](../01-active/CODE-REVIEW-20260519-001.md) | 完整的代码审查报告，包含根本原因分析 |
| [BUG-20260519-002.md](../04-bugs/BUG-20260519-002.md) | Bug 详情 + 修复方案 |
| [BUG-20260519-003.md](../04-bugs/BUG-20260519-003.md) | Bug 详情 + 修复方案 |
| [BUG-20260519-004.md](../04-bugs/BUG-20260519-004.md) | Bug 详情 + 修复方案 + ErrorCode 映射 |

---

## 六、修复后的后续计划

修复完成 + 编译验证后：

1. **提交 PR**：代码改动提交审查
2. **集成测试**：编写工作流集成测试验证修复
3. **Phase 2 启动**：如无其他问题，可启动 Phase 2 开发（系统变量优化等）

---

## 七、联系方式

- 有疑问？查看详细 Bug 报告
- 修复卡住？检查 CODE-REVIEW-20260519-001.md 中的根本原因分析
- 需要 import 帮助？参考其他 controller 文件中的导入方式

---

**提醒**：这些是代码审查中**意外发现**的问题，不是架构设计问题。架构设计本身是正确的，只是实现缺了一些细节。修复后可立即进入集成测试阶段。

**修复截止**：建议在今天 18:00 前完成，明天可启动集成测试。
