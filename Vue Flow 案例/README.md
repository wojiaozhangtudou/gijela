# Vue Flow 案例（当前 Demo 说明）

> 本文档描述的是当前 `Vue Flow 案例/demo` 的**实际功能**与交互，不再保留早期骨架版本说明。

---

## 1. 当前 Demo 实现了什么

当前 demo 已形成完整可操作闭环：

1. 左侧节点库拖拽到画布创建节点（`start` / `llm` / `end`）
2. 节点四方向连接点（上/下/左/右）
3. 连线创建、选中、删除
4. 连线端点可拖拽修改（edge update）
5. 右侧节点配置编辑（LLM 参数）
6. 手动新增连线（源节点/目标节点）
7. Dagre 自动布局（LR 方向）
8. Definition 导出为 JSON 文件
9. 从 JSON 文件加载 Definition
10. JSON 预览弹窗
11. 加载结果使用页面内 Toast 提示（非浏览器 `alert`）

---

## 2. 目录与关键文件

- [Vue Flow 案例/demo/src/views/FlowEditor.vue](demo/src/views/FlowEditor.vue)
- [Vue Flow 案例/demo/src/components/FlowNode.vue](demo/src/components/FlowNode.vue)
- [Vue Flow 案例/demo/src/types/flow.ts](demo/src/types/flow.ts)

---

## 3. 快速启动（PowerShell）

```powershell
Set-Location .\Vue Flow 案例\demo
pnpm install
pnpm dev
```

> Vite 默认端口通常是 `5173`，若占用会自动切换到 `5174`/`5175`。

---

## 4. 当前画布交互说明

### 4.1 浮动工具按钮（画布右上角）

- 💾 保存并下载：下载当前流程 JSON
- 🧾 查看 JSON：弹窗预览当前流程 JSON
- 📂 加载文件：选择 JSON 文件并加载到画布
- ✨ 自动布局：按 Dagre 规则自动排布节点

### 4.2 连线规则

- 禁止自环（`source === target`）
- 同 `source -> target` 连线去重
- 支持连接点（handle）持久化：`sourceHandle` / `targetHandle`
- 连线创建严格依赖 `@connect` 事件（不做补边兜底）

### 4.3 右侧面板

- 选中节点：展示并可编辑参数（LLM 支持 `modelKey/temperature/maxTokens/prompt`）
- 选中连线：显示源/目标信息，可删除当前连线
- 提供连线日志和手动新增连线

---

## 5. Definition 格式（当前）

```json
{
  "nodes": [
    {
      "id": "llm-1",
      "type": "llm",
      "position": { "x": 360, "y": 180 },
      "config": {
        "modelKey": "qwen-plus",
        "temperature": 0.7,
        "maxTokens": 2000,
        "prompt": "{{query}}"
      }
    }
  ],
  "edges": [
    {
      "id": "e-1",
      "sourceNodeId": "start-1",
      "targetNodeId": "llm-1",
      "sourceHandle": "right",
      "targetHandle": "left"
    }
  ]
}
```

> 加载仅支持新格式：`edges` 必须使用 `sourceNodeId/targetNodeId` 字段。

---

## 6. 加载机制（稳定性说明）

为避免 Vue Flow 在中间态渲染导致异常，加载流程采用“分阶段应用”：

1. 清空 edges 和 nodes
2. 等待渲染周期
3. 先设置 nodes
4. 再设置 edges
5. `fitView` 归位

加载状态通过右上角 toast 展示（成功/失败）。

---

## 7. 已知设计取舍

- 这是 demo，不接后端 API；保存使用文件下载
- 节点类型仅 `start/llm/end`
- 连接规则目前是基础规则，未做复杂业务约束（如类型矩阵）

---

## 8. 可继续增强方向

1. 撤销/重做（快照栈）
2. 更严格的连接规则引擎
3. 节点模板与 schema
4. 执行调试面板（输入输出快照）
5. 自动保存与脏状态提示
