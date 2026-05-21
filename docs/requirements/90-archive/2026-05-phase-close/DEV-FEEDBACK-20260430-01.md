# 开发反馈：知识库检索集成诊断

**日期**：2026-04-30  
**状态**：待验证  

---

## 问题描述

用户报告：前端对话窗口中**看不到知识库引用（references）**，即使后端返回了对话内容。

### 症状
- ✗ ChatWindow 组件中未显示 reference 框（蓝框）
- ✗ ChatPage 右侧 drawer 未弹出任何数据
- ✓ 但后端对话逻辑成功（用户收到对话回复）

---

## 问题分类

**类型**：实现风险 + 架构依赖  
**严重级别**：**Major**  
**位置**：
- 后端：`ChatOrchestratorServiceImpl.searchKnowledgeBase()` → `KnowledgeIngestionService.search()`
- 配置：`gijela-core-chat/src/main/resources/application.yml`
- 基础设施：Qdrant 向量数据库、Embedding 模型服务

---

## 根本原因分析

### ✅ 代码层面（已验证正确）
1. **类型定义**：ChatCompletionResponse、ChatEventVO、ChatHistoryMessageResponse 都正确包含 `references` 字段
2. **service 调用链**：
   ```
   ChatOrchestratorServiceImpl.complete()
     → searchKnowledgeBase(context, request)
       → KnowledgeIngestionService.search(context, query)
         → DefaultKnowledgeIngestionService.search()
           → KnowledgeSearchGateway.search(tenantId, query)
             → embedQuery() + Qdrant API call
   ```
3. **错误处理**：searchKnowledgeBase() 捕获所有异常，返回 null，**不阻塞对话**

### ⚠️ 配置和基础设施（待诊断）

#### 配置检查（`application.yml` 已配置）
```yaml
gijela:
  chat:
    retrieval:
      enabled: true                      # ✓ 检索已启用
      endpoint: http://127.0.0.1:6333   # Qdrant 地址
      collection: chat_knowledge         # Collection 名称
      top-k: 5
    embedding:
      base-url: http://127.0.0.1:9997/v1 # Embedding 服务地址
      model: bge-large-zh-v1.5
```

#### 可能的失败点
| 项目 | 状态 | 症状 | 检查方式 |
|-----|------|------|---------|
| **Qdrant 服务** | ? | Embedding 后无法连接 Qdrant | `curl http://127.0.0.1:6333/health` |
| **Embedding 服务** | ? | embedQuery() 调用失败，引发异常被捕获 | `curl http://127.0.0.1:9997/v1/embeddings` |
| **Chat_knowledge Collection** | ? | Qdrant 运行但无对应 collection | 登入 Qdrant Dashboard 检查 |
| **知识库数据** | ? | Collection 存在但为空（无搜索结果） | 查看 Qdrant collection 点数 |

---

## 现象推理

### 场景 1：Embedding 或 Qdrant 不可用
- `searchKnowledgeBase()` 捕获异常 → 返回 `null`
- `references = null` 在 done 事件中 
- 前端收到 `references: null` → 不显示任何引用
- **预期表现**：前端应优雅处理 null（现已实现）

### 场景 2：知识库无数据
- Embedding 和 Qdrant 都正常运行
- 但知识库中没有数据（collection 为空）
- `hits: []` 返回空数组
- 前端显示空引用（符合预期）

### 场景 3：Collection 不匹配
- Qdrant 中没有 `chat_knowledge` collection
- 搜索请求返回 HTTP 404 或其他错误
- 异常被捕获 → `references = null`

---

## 影响范围

- **功能影响**：知识库检索完全不可用，对话仍可进行（graceful degradation）
- **用户体验**：无法看到对话的知识来源，失去可信度增强功能
- **性能影响**：目前没有，因为异常被快速捕获

---

## 修正建议

### 方案 A：诊断和启动（立即执行）

1. **验证 Qdrant 服务**
   ```bash
   curl -v http://127.0.0.1:6333/health
   # 应返回 200 OK
   
   curl -H "api-key: <key>" http://127.0.0.1:6333/collections
   # 查看现有 collections，是否包含 chat_knowledge
   ```

2. **验证 Embedding 服务**
   ```bash
   curl -X POST http://127.0.0.1:9997/v1/embeddings \
     -H "Content-Type: application/json" \
     -d '{"model":"bge-large-zh-v1.5","input":"测试"}'
   # 应返回向量数组
   ```

3. **启动缺失的服务**
   - 若 Qdrant 未运行：`docker run -p 6333:6333 qdrant/qdrant`
   - 若 Embedding 未运行：需确认部署地址和启动方式

4. **初始化 Collection**
   ```bash
   curl -X PUT http://127.0.0.1:6333/collections/chat_knowledge \
     -H "api-key: <key>" \
     -H "Content-Type: application/json" \
     -d '{...}'  # collection config
   ```

### 方案 B：增强诊断日志（后续改进）

在 `searchKnowledgeBase()` 方法中添加**更详细的日志**（而非静默捕获异常）：

```java
private List<Map<String, Object>> searchKnowledgeBase(ChatContext context, Object request) {
    try {
        String query = extractUserQuery(request);
        if (query == null || query.isBlank()) {
            logger.debug("[knowledge] 无有效查询词，跳过检索");
            return null;
        }
        
        logger.debug("[knowledge] 开始检索：query={}, tenant={}", query, context.tenantId());
        Map<String, Object> searchResult = knowledgeIngestionService.search(context, query);
        
        if (searchResult == null || !searchResult.containsKey("hits")) {
            logger.warn("[knowledge] 检索结果异常：searchResult={}", searchResult);
            return null;
        }
        
        Object hits = searchResult.get("hits");
        logger.debug("[knowledge] 检索成功：hits={}", hits);
        
        if (hits instanceof List) {
            return (List<Map<String, Object>>) hits;
        }
        return null;
    } catch (Exception ex) {
        logger.error("[knowledge] 检索异常", ex);  // 记录完整 stack trace
        return null;  // Fail gracefully
    }
}
```

### 方案 C：前端优化（后续改进）

在 ChatWindow.vue 中添加：
- 若 `references` 为空，显示"无相关知识源"提示（可选）
- 或简单隐藏引用区块（当前已实现）

---

## 临时绕行方案

**现有实现已是绕行方案**：
- ✅ 异常捕获 → 不阻塞对话
- ✅ 返回 null → 前端优雅降级
- ✅ 功能可用但无增强

**用户可继续使用对话功能**，直到知识库服务启动。

---

## 决策和建议

| 优先级 | 项目 | 建议 | 所有者 |
|--------|------|------|--------|
| **P0** | 启动 Qdrant + Embedding | 确保基础设施就位 | DevOps/部署负责人 |
| **P0** | 初始化 chat_knowledge Collection | 通过 dashboard 或 API | DevOps |
| **P1** | 索引样本数据（设计模式等） | 用前端 indexKnowledge 接口导入 | 知识库管理员 |
| **P2** | 增强诊断日志 | searchKnowledgeBase() 添加结构化日志 | 后端开发 |
| **P3** | 前端 UX 优化 | 引用框支持"加载中"、"无数据"状态 | 前端开发 |

---

## 验证清单

- [ ] Qdrant 服务运行且健康
- [ ] Embedding 服务运行且可调用
- [ ] `chat_knowledge` collection 已创建
- [ ] 至少导入 1 条样本文档（如"设计模式"）
- [ ] 发送对话查询 → 检查后端日志是否有 embedQuery 调用
- [ ] 前端 ChatPage 查看是否显示引用框
- [ ] 点击引用框 → 右侧 drawer 显示详情

---

## 后续跟进

此反馈待以下条件满足后，可转为"已解决"：
1. ✅ 基础设施验证（Qdrant + Embedding 可用）
2. ✅ 集成测试通过（端到端对话 + 引用返回）
3. ✅ 前端显示验证（引用框和 drawer 正常工作）

