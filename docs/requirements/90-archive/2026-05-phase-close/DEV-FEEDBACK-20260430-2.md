# 开发反馈补充 - 20260430-2 后端对话引用支持

**会话周期**：REQ-20260427-002 后续 - 后端引用字段集成

---

## 一、后端对话 API 引用字段补充

### 已完成修改

为支持前端显示知识库引用，已在后端以下三个关键类型中添加 `references` 字段：

#### 1. ChatCompletionResponse（同步对话响应）
**文件**: `gijela-core-chat/src/main/java/.../domain/dto/ChatCompletionResponse.java`

```java
public record ChatCompletionResponse(
    String sessionId,
    String content,
    String finishReason,
    ChatUsageDTO usage,
    List<Map<String, Object>> references  // 新增：引用的知识片段列表
) {
}
```

**用途**: `/api/v1/chat/completions` 同步接口返回引用信息

#### 2. ChatHistoryMessageResponse（历史消息查询）
**文件**: `gijela-core-chat/src/main/java/.../domain/dto/ChatHistoryMessageResponse.java`

```java
public record ChatHistoryMessageResponse(
    String role,
    String content,
    LocalDateTime createdAt,
    List<Map<String, Object>> references  // 新增：存储的引用信息
) {
}
```

**用途**: `/api/v1/chat/sessions/{sessionId}/messages` 获取历史消息时返回引用

#### 3. ChatEventVO（流式事件对象）
**文件**: `gijela-core-chat/src/main/java/.../domain/vo/ChatEventVO.java`

```java
public record ChatEventVO(
    String type,
    String sessionId,
    String content,
    Map<String, Object> toolCall,
    Map<String, Object> toolResult,
    String error,
    List<Map<String, Object>> references  // 新增：流式事件中的引用信息
) {
}
```

**用途**: `/api/v1/chat/stream` 流式 SSE 事件中返回引用

---

## 二、前后端数据格式对齐

### references 字段格式

```typescript
// 前端 TypeScript 类型（已在 src/types/chat.ts 定义）
references?: Array<{
  id?: string | number
  title?: string
  score?: number
  payload?: Record<string, unknown>
}>

// 后端 Java 类型（对应 Map<String, Object>）
List<Map<String, Object>> // 每个 Map 应包含上述字段
```

**示例数据**:
```json
{
  "references": [
    {
      "id": "vector_001",
      "title": "权限模型设计文档",
      "score": 0.92,
      "payload": {
        "content": "权限模型采用基于角色的访问控制（RBAC）...",
        "source": "knowledge_base",
        "updated_at": "2026-04-20"
      }
    },
    {
      "id": "vector_002",
      "title": "安全认证流程",
      "score": 0.88,
      "payload": { ... }
    }
  ]
}
```

---

## 三、集成路径

### 后端需要补充的实现

**1. ChatOrchestratorService 服务层**
- `complete()` 方法：调用模型后，若启用知识库检索，需将搜索结果映射为 `references`
- `stream()` 方法：流式返回时，在 ChatEventVO 中填充 `references` 字段

**2. 数据来源**
- 调用 `KnowledgeIngestionService.searchKnowledge()` 获取向量搜索结果
- 将 KnowledgeSearchResult 中的 hits 转换为 references Map 列表

**3. 存储与加载**
- 对话历史存储时，同步保存 references（Redis/数据库）
- 查询历史消息时，从存储中恢复 references

---

## 四、验证清单

| 项 | 状态 | 备注 |
|---|------|------|
| 前端 TypeScript 类型 | ✅ 完成 | ChatMessage.references 已定义 |
| 前端 UI 显示引用 | ✅ 完成 | ChatWindow 显示引用片段 + 右侧详情面板 |
| 后端响应类型 | ✅ 完成 | ChatCompletionResponse/ChatHistoryMessageResponse/ChatEventVO 已添加 references |
| 后端业务逻辑 | ⏳ 待实现 | 需在 ChatOrchestratorService 中填充 references 数据 |
| 后端编译 | ⏳ 验证中 | 新字段添加后重新编译检查 |
| 端到端测试 | ⏳ 待进行 | 后端启动后进行完整流程验证 |

---

## 五、后续实现建议

### 立即 (当前迭代)
1. ✅ 后端字段定义（已完成）
2. ⏳ 验证编译通过并打包
3. 在 ChatOrchestratorService 中实现 references 填充逻辑：
   ```java
   // 伪代码示例
   public ChatCompletionResponse complete(ChatContext context, ChatCompletionRequest request) {
       // 1. 构建聊天消息
       List<Message> messages = buildMessages(request);
       
       // 2. 调用知识库检索（可选）
       List<Map<String, Object>> references = null;
       if (enabledKnowledgeRetrieval) {
           KnowledgeSearchResult searchResult = knowledgeService.search(...);
           references = mapSearchResultToReferences(searchResult);
       }
       
       // 3. 调用模型
       String modelResponse = callModel(messages);
       
       // 4. 保存对话历史（含 references）
       saveMessage(role="assistant", content=modelResponse, references=references);
       
       // 5. 返回响应
       return new ChatCompletionResponse(..., references);
   }
   ```

4. 类似逻辑补充到 `stream()` 和历史查询方法

### 后续迭代
- 知识库引用的优化级别控制（自动/手动/禁用）
- 引用去重与排序
- 引用来源追踪与审计

---

## 六、影响范围

| 组件 | 改动 | 兼容性 |
|------|------|--------|
| 前端 | 无改动需要（已支持） | 完全兼容 |
| 后端 API 响应 | 新增可选字段 | 向后兼容（references 可为 null）|
| 数据库 | 无改动需要 | 无影响 |
| 缓存 (Redis) | 可能需调整序列化 | 需测试 |

---

## 七、已落盘位置

- **后端修改**:
  - [ChatCompletionResponse.java](../../gijela-core-chat/src/main/java/.../domain/dto/ChatCompletionResponse.java)
  - [ChatHistoryMessageResponse.java](../../gijela-core-chat/src/main/java/.../domain/dto/ChatHistoryMessageResponse.java)
  - [ChatEventVO.java](../../gijela-core-chat/src/main/java/.../domain/vo/ChatEventVO.java)

- **前端已完成**:
  - [src/types/chat.ts](../../gijela-bloom/gijela-bloom-chat/src/types/chat.ts)
  - [src/components/ChatWindow.vue](../../gijela-bloom/gijela-bloom-chat/src/components/ChatWindow.vue)
  - [src/views/ChatPage.vue](../../gijela-bloom/gijela-bloom-chat/src/views/ChatPage.vue)

---

## 总结

后端引用字段的类型定义已补充完整，前后端数据格式已对齐。剩余工作是在业务逻辑层（ChatOrchestratorService）实现引用数据的填充与流转，预计工作量小，逻辑清晰。
