# 从 0 到 1 搭一个可用的 Vue Flow 工作流编排器（含下载/加载/自动布局）

> **关键词**：Vue Flow、流程编排、可视化编辑器、Vue3、TypeScript、Dagre、前端工程化

---

## 这是什么？从哪来的？

这个 Demo 脱胎于开源项目 **gijela**（一套 AI 智能体管理后台）中的工作流编排模块，经过剥离和精简后，整理成了可独立运行的最小案例。

**gijela 主仓**：
- 📦 GitHub：https://github.com/wojiaozhangtudou/gijela
- 📦 Gitee：https://gitee.com/zhangjq123/gijela

**Demo 在主仓的位置**：
- 原始编辑器：`gijela-bloom/gijela-bloom-pistol/src/views/FlowEditor.vue`
- 独立 Demo：`Vue Flow 案例/demo/src/views/FlowEditor.vue`

gijela 的完整工程除了这个编辑器之外，还包含后端（Spring Boot 多模块）、聊天应用、AI Skill 插件等，这个 Demo 只抽取了前端画布核心部分，**去掉了权限、状态管理、接口依赖**，让你可以直接看到最干净的 Vue Flow 实现方式。

---

## 这个案例能做什么？

当前版本已经是"可演示 + 可二开"的状态，功能包括：

1. 左侧节点库拖拽建点（start / llm / end 三种类型）
2. 四方向连接点（上/下/左/右），悬停显示
3. 连线创建、选中、删除、端点拖拽修改
4. 右侧面板显示节点/连线详情
5. Dagre 自动布局（从左到右方向）
6. 工作流 JSON 一键下载
7. 选择本地 JSON 文件加载
8. JSON 预览弹窗
9. 页面内 Toast 通知（替代浏览器 alert）

一句话：**不仅能画图，还能稳定保存/恢复图**。

---

## 本地启动

先 clone 仓库，然后在**仓库根目录**执行：

```powershell
# Windows / PowerShell，从仓库根目录开始
Set-Location ".\Vue Flow 案例\demo"
pnpm install
pnpm dev
```

浏览器打开 `http://localhost:5173` 即可看到效果。Node.js 建议 18+，包管理器用 pnpm。

---

## 踩过哪些坑？实现细节全记录

### 1）坐标错位 → 用 screenToFlowCoordinate 转换

**问题**：拖拽节点到画布后，位置经常偏，缩放画布后更明显。

**原因**：浏览器事件的坐标是屏幕坐标，Vue Flow 的画布有自己的坐标系（受缩放/平移影响），两者不能直接混用。

**解决方案**：

```typescript
const { screenToFlowCoordinate, addNode } = useVueFlow();

const onDrop = (event: DragEvent) => {
  if (!event.dataTransfer?.types.includes('biz-node-type')) return;

  const nodeType = event.dataTransfer.getData('biz-node-type') as BizNodeType;

  // 关键：屏幕坐标 → 画布坐标
  const position = screenToFlowCoordinate({
    x: event.clientX,
    y: event.clientY,
  });

  addNode({
    id: `node-${Date.now()}`,
    label: nodeType,
    position,
    type: 'biz-node',
    data: { type: nodeType },
  });
};
```

无论画布怎么缩放平移，落点都是精确的。

---

### 2）连线格式混乱 → 统一持久化格式

**问题**：Vue Flow 内部的 `Edge` 对象用 `source`/`target` 字段，但持久化到 JSON 时，如果直接把内部对象序列化，以后的格式就锁死在 Vue Flow 的实现细节上了，升级或迁移都很麻烦。

**解决方案**：定义独立的持久化类型，和 Vue Flow 的内部类型解耦：

```typescript
// types/flow.ts
export interface DefinitionEdge {
  id: string;
  sourceNodeId: string;  // ← 持久化字段，不用 source
  targetNodeId: string;  // ← 持久化字段，不用 target
  label?: string;
}

export interface WorkflowDefinition {
  nodes: DefinitionNode[];
  edges: DefinitionEdge[];
}
```

创建连线时做规则校验，再转成持久化格式：

```typescript
const onConnectEdge = (params: Connection) => {
  const { source, target } = params;

  // 禁止自环
  if (source === target) {
    showToast('❌ 不支持自环连接', 'error');
    return;
  }

  // source → target 去重
  const exists = edges.value.some(
    (e) => e.source === source && e.target === target
  );
  if (exists) {
    showToast('⚠️ 该连接已存在', 'warning');
    return;
  }

  addEdges([{
    id: `edge-${Date.now()}`,
    source,
    target,
    label: `${source} → ${target}`,
  }]);
};
```

导出时，主动做字段映射：

```typescript
function buildDefinition(): WorkflowDefinition {
  return {
    nodes: nodes.value.map((n) => ({
      id: n.id,
      label: String(n.label ?? ''),
      position: n.position,
      data: n.data,
    })),
    edges: edges.value.map((e) => ({
      id: e.id,
      sourceNodeId: e.source,   // ← Vue Flow 内部 → 持久化字段
      targetNodeId: e.target,
      label: e.label,
    })),
  };
}
```

---

### 3）导入文件崩溃 → 分阶段 + nextTick 串行加载

**问题**：直接一次性调 `setNodes` 和 `setEdges`，会触发：

```
Cannot read properties of undefined (reading 'source')
```

**原因**：Vue Flow 内部渲染时，会访问节点和边的引用。如果两者同帧更新，Vue Flow 渲染边时，节点实例可能还没就绪，拿到的就是 `undefined`。

**解决方案**：分两个渲染帧加载，中间用 `nextTick` 隔开：

```typescript
async function applyDefinition(def: WorkflowDefinition) {
  // 1. 清空画布和选中状态
  setNodes([]);
  setEdges([]);
  selectedNode.value = null;
  selectedEdge.value = null;

  // 2. 先加载节点
  setNodes(
    def.nodes.map((n) => ({
      id: n.id,
      label: n.label,
      position: n.position,
      type: 'biz-node',
      data: n.data,
    }))
  );

  // 3. 等 Vue 完成这一帧渲染
  await nextTick();

  // 4. 再加载连线
  setEdges(
    def.edges.map((e) => ({
      id: e.id,
      source: e.sourceNodeId,   // ← 持久化字段 → Vue Flow 内部
      target: e.targetNodeId,
      label: e.label,
    }))
  );

  // 5. 等待渲染完成后适应视图
  await nextTick();
  fitView({ padding: 0.1, duration: 300 });

  showToast('✅ 加载成功', 'success');
}
```

**为什么有效**：`nextTick` 等待 Vue 将节点渲染进 DOM，Vue Flow 在下一帧才去建立边的引用，此时节点已经存在，不会再出现 `undefined`。

---

### 4）连接点体验差 → Handle 悬停时才显示

**问题**：四个方向的 Handle 全部常驻显示，节点看起来很乱；隐藏后拖拽时又容易找不到。

**解决方案**（FlowNode.vue）：

```vue
<template>
  <div
    class="flow-node"
    :class="`flow-node--${data.type}`"
    @mouseenter="hovering = true"
    @mouseleave="hovering = false"
  >
    <span class="node-label">{{ data.type }}</span>

    <Handle type="target" :position="Position.Top" id="top" />
    <Handle type="source" :position="Position.Bottom" id="bottom" />
    <Handle type="target" :position="Position.Left" id="left" />
    <Handle type="source" :position="Position.Right" id="right" />
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core';

defineProps<{ data: { type: string } }>();
</script>

<style scoped>
/* Handle 默认透明 */
:deep(.vue-flow__handle) {
  opacity: 0;
  transition: opacity 0.15s;
}

/* 悬停时显示 */
.flow-node:hover :deep(.vue-flow__handle) {
  opacity: 1;
}

/* 按类型区分颜色 */
.flow-node {
  padding: 10px 16px;
  border-radius: 6px;
  border-left: 4px solid #d9d9d9;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.1);
}
.flow-node--start { border-left-color: #52c41a; }
.flow-node--llm   { border-left-color: #1677ff; }
.flow-node--end   { border-left-color: #ff4d4f; }
</style>
```

**注意**：Handle 的 `position` 属性必须明确传（用 `Position` 枚举），不要省略，否则 Vue Flow 内部定位会出问题。

---

### 5）保存 → 一键下载 JSON

```typescript
function downloadDefinition() {
  const definition = buildDefinition();
  const json = JSON.stringify(definition, null, 2);

  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);

  const link = document.createElement('a');
  link.href = url;
  link.download = `workflow-${new Date().toISOString().slice(0, 10)}.json`;
  link.click();

  URL.revokeObjectURL(url);
  showToast('✅ 下载成功', 'success');
}
```

---

### 6）自动布局 → Dagre 计算节点坐标

手动排列节点很费时，接入 Dagre 之后一键整理成 LR 方向的层次布局。

```bash
pnpm add dagre @types/dagre
```

```typescript
import dagre from 'dagre';

const NODE_WIDTH = 160;
const NODE_HEIGHT = 60;

function applyDagreLayout() {
  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir: 'LR', nodesep: 60, ranksep: 80 });
  g.setDefaultEdgeLabel(() => ({}));

  // 注册节点尺寸
  nodes.value.forEach((node) => {
    g.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  });

  // 注册边
  edges.value.forEach((edge) => {
    g.setEdge(edge.source, edge.target);
  });

  // 执行布局计算
  dagre.layout(g);

  // 将计算结果写回节点坐标
  setNodes(
    nodes.value.map((node) => {
      const { x, y } = g.node(node.id);
      return {
        ...node,
        position: {
          x: x - NODE_WIDTH / 2,   // Dagre 返回的是中心点，转换为左上角
          y: y - NODE_HEIGHT / 2,
        },
      };
    })
  );

  // 布局后自适应视图
  nextTick(() => fitView({ padding: 0.1, duration: 300 }));
  showToast('✅ 自动布局完成', 'success');
}
```

**注意**：布局后如果想保存坐标，直接导出即可，Dagre 修改的是 `nodes.value` 里的 `position`，`buildDefinition()` 读的就是这个值，不需要额外处理。

---

### 7）加载 → 文件选择 + 校验 + 分阶段应用

```typescript
async function loadFromFile() {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.json';

  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;

    try {
      const text = await file.text();
      const rawDef = JSON.parse(text);

      // 格式校验
      validateDefinition(rawDef);

      // 分阶段加载
      await applyDefinition(rawDef);

    } catch (err: any) {
      showToast(`❌ 加载失败：${err.message}`, 'error');
    }
  };

  input.click();
}

function validateDefinition(def: any): void {
  if (!Array.isArray(def?.nodes)) throw new Error('缺少 nodes 字段');
  if (!Array.isArray(def?.edges)) throw new Error('缺少 edges 字段');

  def.edges.forEach((edge: any, idx: number) => {
    if (!edge.sourceNodeId || !edge.targetNodeId) {
      throw new Error(`第 ${idx + 1} 条边缺少 sourceNodeId/targetNodeId`);
    }
  });
}
```

---

### 8）Toast 通知 → 替代 alert

```typescript
const toasts = ref<Array<{ id: string; message: string; type: 'success' | 'error' | 'warning' }>>([]);
let toastTimer: ReturnType<typeof setTimeout> | null = null;

function showToast(message: string, type: 'success' | 'error' | 'warning' = 'success') {
  const id = `toast-${Date.now()}`;
  toasts.value.push({ id, message, type });

  toastTimer = setTimeout(() => {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }, 3000);
}

// 组件卸载时清理定时器
onBeforeUnmount(() => {
  if (toastTimer) clearTimeout(toastTimer);
});
```

模板渲染：

```vue
<TransitionGroup name="toast" tag="div" class="toast-container">
  <div
    v-for="toast in toasts"
    :key="toast.id"
    class="toast"
    :class="`toast--${toast.type}`"
  >
    {{ toast.message }}
  </div>
</TransitionGroup>
```

CSS：

```css
.toast-container {
  position: fixed;
  bottom: 24px;
  right: 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 9999;
}

.toast {
  padding: 10px 18px;
  border-radius: 6px;
  color: #fff;
  font-size: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.toast--success { background: #52c41a; }
.toast--error   { background: #ff4d4f; }
.toast--warning { background: #faad14; }

.toast-enter-active,
.toast-leave-active { transition: all 0.3s ease; }
.toast-enter-from,
.toast-leave-to { opacity: 0; transform: translateY(20px); }
```

---

## 常见问题

**Q：导入后节点位置全挤在一起？**
加载完成后调 `fitView()` 自动适配视图，或提前用 Dagre 布局再导出。

**Q：连线删不掉？**
确认是否绑定了 `@edge-click` 事件：

```typescript
const onEdgeClick = ({ edge }: { edge: Edge }) => {
  removeEdges([edge.id]);
};
```

**Q：Dagre 布局后导出，下次导入位置还是乱？**
Dagre 会直接修改 `nodes.value` 里的 `position`，导出时直接读 `nodes.value` 即可拿到布局后的坐标，不需要额外保存。

**Q：Handle 拖拽时不响应？**
检查 `position` 属性是否用了 `Position` 枚举：

```typescript
// ✓ 正确
<Handle type="source" :position="Position.Right" />

// ✗ 字符串有时不被识别
<Handle type="source" position="right" />
```

---

## 性能建议（节点 50+ 时）

- **防抖**：拖拽和连接事件加 debounce，避免频繁触发重渲染
- **Web Worker**：Dagre 计算量大时放进 Worker，不阻塞主线程
- **按需渲染**：超大图配合视口裁剪，只渲染可见区域节点

```typescript
import { debounce } from 'lodash-es';

const onConnectEdge = debounce((params: Connection) => {
  validateAndAddEdge(params);
}, 150);
```

---

## 项目结构

```text
Vue Flow 案例/
├─ README.md
└─ demo/
   ├─ package.json
   └─ src/
      ├─ views/
      │  └─ FlowEditor.vue    ← 主编辑器（全部核心逻辑）
      ├─ components/
      │  └─ FlowNode.vue      ← 自定义节点组件
      └─ types/
         └─ flow.ts           ← 类型定义
```

---

## Demo 与 gijela 原工程的功能对比

| 功能 | 本 Demo | gijela 原工程 |
|------|:---:|:---:|
| 基础画布编辑 | ✅ | ✅ |
| 保存 / 加载 | ✅ | ✅ |
| 自动布局（Dagre）| ✅ | ✅ |
| 权限控制 | ❌ | ✅ |
| 节点参数动态表单 | ❌ | ✅ |
| 运行态状态同步 | ❌ | ✅ |
| 版本快照 | ❌ | 规划中 |
| 后端任务引擎联调 | ❌ | ✅ |

这个 Demo 是**单机 MVP**，原工程是生产级实现，可以按需参考。

---

## 后续可以做什么？

基础打好后，推荐按这个顺序扩展：

1. **撤销 / 重做**：基于快照栈，或使用 `useUndoRedo()` Composable
2. **连接规则**：按节点类型限制连线（如 start 不能作目标节点）
3. **节点参数编辑**：点击节点弹出配置面板，支持动态 Schema 表单
4. **运行态高亮**：后端推送执行进度，节点实时变色
5. **多人协作**：WebSocket 广播节点变更，多用户同时编辑

---

## 小结

做工作流编辑器，踩最多坑的通常不是"画图"这件事本身，而是：

- 坐标系换算没做对，拖哪偏哪
- 导入/导出格式没解耦，耦合了 Vue Flow 内部实现
- 加载时序没处理好，一导入就崩

这个 Demo 把这几个关键点都踩过一遍，整理成了可直接运行的案例。如果你也在做类似的东西，可以直接从这里起步，欢迎 Star 和交流：

- GitHub：https://github.com/wojiaozhangtudou/gijela
- Gitee：https://gitee.com/zhangjq123/gijela
