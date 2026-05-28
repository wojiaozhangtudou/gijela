<template>
  <div class="workflow-editor-container">
    <div class="editor-toolbar">
      <div class="toolbar-meta">
        <div>
          <strong style="font-size: 14px">Vue Flow Demo</strong>
          <div class="toolbar-subtitle">对齐 gijela-bloom-chat-flow 的编辑器布局与画布风格</div>
        </div>
      </div>
    </div>

    <div class="editor-layout">
      <aside class="panel-left">
        <div class="node-palette">
          <h4 style="margin: 0 0 12px 0; font-size: 13px; color: #333">节点库</h4>
          <div class="node-item" draggable="true" @dragstart="(e) => onDragStart(e, 'start')">
            <span class="node-icon">🟢</span>
            <div class="node-info">
              <div class="node-title">开始</div>
              <div class="node-desc">流程起点</div>
            </div>
          </div>
          <div class="node-item" draggable="true" @dragstart="(e) => onDragStart(e, 'llm')">
            <span class="node-icon">🤖</span>
            <div class="node-info">
              <div class="node-title">LLM</div>
              <div class="node-desc">AI 处理节点</div>
            </div>
          </div>
          <div class="node-item" draggable="true" @dragstart="(e) => onDragStart(e, 'end')">
            <span class="node-icon">⏹️</span>
            <div class="node-info">
              <div class="node-title">结束</div>
              <div class="node-desc">流程终点</div>
            </div>
          </div>
        </div>
      </aside>

      <main class="panel-center editor-canvas" :class="{ 'is-connecting': isConnecting }" @dragover="onDragOver" @drop="onDrop" @click="hideContextMenu">
        <div class="canvas-floating-actions">
          <button class="action-btn toolbar-btn icon-only save-btn" title="保存并下载" aria-label="保存并下载" @click="downloadDefinition">
            <span class="btn-icon">💾</span>
          </button>
          <button class="action-btn toolbar-btn icon-only json-btn" title="查看 JSON" aria-label="查看 JSON" @click="openJsonPreview">
            <span class="btn-icon">🧾</span>
          </button>
          <button class="action-btn toolbar-btn icon-only load-btn" title="加载文件" aria-label="加载文件" @click="triggerDefinitionFilePick">
            <span class="btn-icon">📂</span>
          </button>
          <button class="action-btn toolbar-btn icon-only primary layout-btn" title="自动布局" aria-label="自动布局" @click="autoLayout">
            <span class="btn-icon">✨</span>
          </button>
          <input
            ref="definitionFileInput"
            type="file"
            accept="application/json,.json"
            style="display: none"
            @change="onDefinitionFileChange"
          />
        </div>

        <VueFlow
          :nodes="nodes"
          :edges="edges"
          :node-types="nodeTypes"
          :fit-view-on-init="false"
          :fit-view-options="{ padding: 0.1 }"
          :connection-mode="ConnectionMode.Loose"
          :is-valid-connection="isValidConnection"
          :nodes-connectable="true"
          :edges-updatable="true"
          :elements-selectable="true"
          @node-click="onNodeClick"
          @node-context-menu="onNodeContextMenu"
          @edge-click="onEdgeClick"
          @edge-update-start="onEdgeUpdateStart"
          @edge-update="onEdgeUpdate"
          @edge-update-end="onEdgeUpdateEnd"
          @connect="onConnectEdge"
          @connect-start="onConnectStart"
          @connect-end="onConnectEnd"
          @pane-ready="onPaneReady"
          @pane-click="hideContextMenu"
        >
          <Background :pattern-size="16" />
          <Controls />
        </VueFlow>

        <div
          v-if="contextMenu.visible"
          class="node-context-menu"
          :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
        >
          <button @click="duplicateNode">复制节点</button>
          <button class="danger" @click="deleteNode">删除节点</button>
        </div>
      </main>

      <aside class="panel-right">
        <div class="config-panel">
          <div class="config-title">⚙️ 节点配置</div>
          <div v-if="selectedNode" class="config-content">
            <div class="selected-node-summary">
              <strong>{{ selectedNode.data?.label || selectedNode.id }}</strong>
              <div class="selected-node-meta">类型: {{ selectedNode.data?.bizType }}</div>
              <div class="selected-node-meta">ID: {{ selectedNode.id }}</div>
            </div>

            <template v-if="selectedNode.data?.bizType === 'llm'">
              <label>modelKey</label>
              <input v-model="selectedNode.data.config.modelKey" />

              <label>temperature</label>
              <input type="number" step="0.1" v-model.number="selectedNode.data.config.temperature" />

              <label>maxTokens</label>
              <input type="number" step="100" v-model.number="selectedNode.data.config.maxTokens" />

              <label>prompt</label>
              <textarea rows="6" v-model="selectedNode.data.config.prompt" />
            </template>

            <button class="action-btn danger" @click="deleteNode">🗑️ 删除当前节点</button>
          </div>
          <div v-else-if="selectedEdge" class="config-content">
            <div class="selected-node-summary edge-summary">
              <strong>已选中连线</strong>
              <div class="selected-node-meta">{{ selectedEdgeInfo?.sourceText || '-' }} → {{ selectedEdgeInfo?.targetText || '-' }}</div>
              <div class="selected-node-meta">💡 拖拽连线端点可以修改连接</div>
            </div>
            <button class="action-btn danger" @click="deleteSelectedEdge">🗑️ 删除当前连线</button>
          </div>
          <div v-else class="placeholder">点击画布中的节点进行配置</div>

          <div class="connect-log-box">
            <div class="config-title" style="margin-bottom: 8px">🧾 连线日志</div>
            <div v-if="connectionLogs.length === 0" class="placeholder" style="padding: 8px 0">暂无日志</div>
            <div v-for="(log, idx) in connectionLogs" :key="idx" class="connect-log-line">{{ log }}</div>
          </div>

          <div class="manual-edge-box">
            <div class="config-title" style="margin-bottom: 8px">➕ 手动新增连线</div>
            <label>源节点</label>
            <select v-model="newEdgeForm.sourceNodeId">
              <option value="">请选择</option>
              <option v-for="node in nodes" :key="`source-${node.id}`" :value="node.id">{{ node.data?.label }} ({{ node.id }})</option>
            </select>

            <label>目标节点</label>
            <select v-model="newEdgeForm.targetNodeId">
              <option value="">请选择</option>
              <option v-for="node in nodes" :key="`target-${node.id}`" :value="node.id">{{ node.data?.label }} ({{ node.id }})</option>
            </select>

            <button
              class="action-btn"
              :disabled="!newEdgeForm.sourceNodeId || !newEdgeForm.targetNodeId || newEdgeForm.sourceNodeId === newEdgeForm.targetNodeId"
              @click="addManualEdge"
            >
              🔗 创建连线
            </button>
          </div>
        </div>
      </aside>
    </div>

    <div v-if="jsonPreviewVisible" class="json-modal-mask" @click.self="closeJsonPreview">
      <div class="json-modal">
        <div class="json-modal-header">
          <strong>流程 JSON 预览</strong>
          <button class="json-close-btn" @click="closeJsonPreview">✕</button>
        </div>
        <pre class="json-preview-content">{{ jsonPreviewText }}</pre>
      </div>
    </div>

    <transition name="toast-fade">
      <div v-if="toast.visible" class="floating-toast" :class="`toast-${toast.type}`">
        <span class="toast-icon">{{ toast.type === 'success' ? '✅' : '⚠️' }}</span>
        <span>{{ toast.message }}</span>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, nextTick, onBeforeUnmount, ref } from 'vue'
import dagre from 'dagre'
import { VueFlow, useVueFlow, ConnectionMode, MarkerType } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import FlowNode from '../components/FlowNode.vue'

import type { BizNodeType, WorkflowDefinition } from '../types/flow'

const { nodes, edges, addNodes, screenToFlowCoordinate, fitView, setViewport, setNodes, setEdges } = useVueFlow()
const nodeTypes = {
  flowNode: markRaw(FlowNode) as any
}
const selectedNode = ref<any | null>(null)
const selectedEdge = ref<any | null>(null)
const isConnecting = ref(false)
const contextMenu = ref({ visible: false, x: 0, y: 0, nodeId: '' })
const connectionLogs = ref<string[]>([])
const newEdgeForm = ref({ sourceNodeId: '', targetNodeId: '' })
const definitionFileInput = ref<HTMLInputElement | null>(null)
const jsonPreviewVisible = ref(false)
const jsonPreviewText = ref('')
const toast = ref<{ visible: boolean; type: 'success' | 'error'; message: string }>({
  visible: false,
  type: 'success',
  message: ''
})
let toastTimer: ReturnType<typeof setTimeout> | null = null
let nodeCounter = 0
const NODE_WIDTH = 160
const NODE_HEIGHT = 52

function showToast(type: 'success' | 'error', message: string) {
  if (toastTimer) clearTimeout(toastTimer)
  toast.value = { visible: true, type, message }
  toastTimer = setTimeout(() => {
    toast.value.visible = false
  }, 2200)
}

onBeforeUnmount(() => {
  if (toastTimer) {
    clearTimeout(toastTimer)
    toastTimer = null
  }
})

const selectedEdgeInfo = computed(() => {
  const current = selectedEdge.value
  if (!current || !current.id) return null

  const edgeInList = edges.value.find((e: any) => e?.id === current.id)
  if (!edgeInList) return null

  const sourceId = edgeInList?.source || ''
  const targetId = edgeInList?.target || ''
  if (!sourceId || !targetId) return null

  const sourceNode = nodes.value.find((n: any) => n?.id === sourceId)
  const targetNode = nodes.value.find((n: any) => n?.id === targetId)

  return {
    sourceId,
    targetId,
    sourceText: sourceNode ? `${sourceNode.data?.label || sourceId} (${sourceId})` : sourceId,
    targetText: targetNode ? `${targetNode.data?.label || targetId} (${targetId})` : targetId
  }
})

function createEdge(source: string, target: string, id?: string, sourceHandle?: string | null, targetHandle?: string | null) {
  return {
    id: id || `e-${source}-${target}-${Date.now()}`,
    source,
    target,
    sourceHandle,
    targetHandle,
    animated: false,
    markerEnd: MarkerType.ArrowClosed,
    style: { stroke: '#2563eb', strokeWidth: 2 }
  }
}

function pushConnectLog(message: string) {
  const ts = new Date().toLocaleTimeString()
  connectionLogs.value = [`[${ts}] ${message}`, ...connectionLogs.value].slice(0, 20)
}

function nodeClass(t: BizNodeType) {
  if (t === 'start') return 'node-start'
  if (t === 'llm') return 'node-llm'
  return 'node-end'
}

function onDragStart(event: DragEvent, bizType: BizNodeType) {
  if (!event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('bizType', bizType)
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
}

function onDrop(event: DragEvent) {
  if (!event.dataTransfer) return
  event.preventDefault()

  const bizType = event.dataTransfer.getData('bizType') as BizNodeType
  if (!bizType) return

  const pos = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })

  nodeCounter += 1
  addNodes({
    id: `${bizType}-${nodeCounter}`,
    type: 'flowNode',
    label: nodeLabel(bizType),
    class: nodeClass(bizType),
    position: { x: pos.x - 60, y: pos.y - 24 },
    data: {
      label: nodeLabel(bizType),
      bizType,
      config: bizType === 'llm'
        ? { modelKey: 'qwen-plus', temperature: 0.7, maxTokens: 2000, prompt: '{{query}}' }
        : {}
    }
  })
}

function nodeLabel(t: BizNodeType) {
  if (t === 'start') return '🟢 开始'
  if (t === 'llm') return '🤖 LLM'
  return '⏹️ 结束'
}

function isValidConnection(params: any) {
  const valid = !!params?.source && !!params?.target && params.source !== params.target
  if (!valid) {
    pushConnectLog(`连线校验失败: source=${params?.source || '-'} target=${params?.target || '-'}`)
  }
  return valid
}

function onConnectEdge(params: any) {
  if (!params?.source || !params?.target) return
  pushConnectLog(`收到 connect 事件: ${params.source} -> ${params.target}`)

  const exists = edges.value.some((e: any) => e.source === params.source && e.target === params.target)
  if (exists) {
    pushConnectLog(`忽略重复连线: ${params.source} -> ${params.target}`)
    return
  }

  edges.value = [
    ...edges.value,
    createEdge(params.source, params.target, undefined, params.sourceHandle || null, params.targetHandle || null)
  ] as any
  pushConnectLog(`连线成功: ${params.source} -> ${params.target}`)
}

function onNodeClick(evt: any) {
  selectedNode.value = evt?.node || null
  selectedEdge.value = null
  hideContextMenu()
}

function onEdgeClick(payload: any) {
  payload?.event?.stopPropagation?.()
  const edge = payload?.edge
  if (!edge) return
  if (!edge.source || !edge.target) {
    showToast('error', '连线数据异常：缺少 source/target')
    return
  }
  selectedEdge.value = {
    id: edge.id,
    source: edge.source,
    target: edge.target
  }
  selectedNode.value = null
  pushConnectLog(`选中连线: ${edge.source} -> ${edge.target}`)
}

function onConnectStart(payload: any) {
  isConnecting.value = true
  const nodeId = payload?.nodeId || '-'
  const handleId = payload?.handleId || null
  pushConnectLog(`开始连线: source=${nodeId} handle=${handleId || '-'}`)
}

function onConnectEnd(payload: any) {
  isConnecting.value = false
  const nodeId = payload?.nodeId || '-'
  const handleId = payload?.handleId || '-'
  pushConnectLog(`结束连线: node=${nodeId} handle=${handleId} | 当前边数=${edges.value.length}`)
}

function onNodeContextMenu(payload: any) {
  payload?.event?.preventDefault?.()
  payload?.event?.stopPropagation?.()
  const node = payload?.node
  if (!node) return

  selectedNode.value = node
  contextMenu.value = {
    visible: true,
    x: payload.event.clientX,
    y: payload.event.clientY,
    nodeId: node.id
  }
}

function hideContextMenu() {
  contextMenu.value.visible = false
}

function deleteNode() {
  const nodeId = contextMenu.value.nodeId || selectedNode.value?.id
  if (!nodeId) return

  nodes.value = nodes.value.filter((n: any) => n.id !== nodeId) as any
  edges.value = edges.value.filter((e: any) => e.source !== nodeId && e.target !== nodeId) as any
  if (selectedNode.value?.id === nodeId) {
    selectedNode.value = null
  }
  selectedEdge.value = null
  hideContextMenu()
}

function deleteSelectedEdge() {
  const edgeId = selectedEdge.value?.id
  if (!edgeId) return

  const edge = edges.value.find((e: any) => e.id === edgeId)
  edges.value = edges.value.filter((e: any) => e.id !== edgeId) as any
  if (edge) {
    pushConnectLog(`删除连线: ${edge.source} -> ${edge.target}`)
  }
  selectedEdge.value = null
}

function onEdgeUpdateStart(payload: any) {
  pushConnectLog(`开始修改连线: ${payload?.edge?.source} -> ${payload?.edge?.target}`)
}

function onEdgeUpdate(edgeOrPayload: any, maybeConnection?: any) {
  const edge = maybeConnection ? edgeOrPayload : edgeOrPayload?.edge
  const connection = maybeConnection || edgeOrPayload?.connection
  if (!edge || !connection?.source || !connection?.target) return

  edge.source = connection.source
  edge.target = connection.target
  edge.sourceHandle = connection.sourceHandle || null
  edge.targetHandle = connection.targetHandle || null

  if (selectedEdge.value?.id === edge.id) {
    selectedEdge.value = {
      id: edge.id,
      source: edge.source,
      target: edge.target
    }
  }
}

function onEdgeUpdateEnd(payload: any) {
  const edge = payload?.edge
  if (edge) {
    pushConnectLog(`✓ 连线已更新: ${edge.source} -> ${edge.target}`)
  }
}

function duplicateNode() {
  const nodeId = contextMenu.value.nodeId || selectedNode.value?.id
  if (!nodeId) return

  const sourceNode = nodes.value.find((n: any) => n.id === nodeId)
  if (!sourceNode) return

  const bizType = sourceNode.data?.bizType as BizNodeType
  nodeCounter += 1
  addNodes({
    id: `${bizType}-${nodeCounter}`,
    type: 'flowNode',
    label: nodeLabel(bizType),
    class: nodeClass(bizType),
    position: {
      x: Number(sourceNode.position?.x || 0) + 36,
      y: Number(sourceNode.position?.y || 0) + 36
    },
    data: {
      label: nodeLabel(bizType),
      bizType,
      config: JSON.parse(JSON.stringify(sourceNode.data?.config || {}))
    }
  })
  hideContextMenu()
}

function addManualEdge() {
  const { sourceNodeId, targetNodeId } = newEdgeForm.value
  if (!sourceNodeId || !targetNodeId || sourceNodeId === targetNodeId) return
  onConnectEdge({ source: sourceNodeId, target: targetNodeId })
  newEdgeForm.value = { sourceNodeId: '', targetNodeId: '' }
}

function autoLayout() {
  if (!nodes.value.length) return

  const graph = new dagre.graphlib.Graph()
  graph.setGraph({ rankdir: 'LR', nodesep: 48, ranksep: 90, marginx: 24, marginy: 24 })
  graph.setDefaultEdgeLabel(() => ({}))

  nodes.value.forEach((n: any) => {
    graph.setNode(n.id, { width: NODE_WIDTH, height: NODE_HEIGHT })
  })
  edges.value.forEach((e: any) => {
    graph.setEdge(e.source, e.target)
  })

  dagre.layout(graph)

  nodes.value = nodes.value.map((n: any) => {
    const p = graph.node(n.id)
    if (!p) return n
    return {
      ...n,
      position: {
        x: p.x - NODE_WIDTH / 2,
        y: p.y - NODE_HEIGHT / 2
      }
    }
  }) as any

  nextTick(() => fitView({ padding: 0.2, duration: 260 }))
}

function onPaneReady() {
  setViewport({ x: 0, y: 0, zoom: 1 })
  nextTick(() => fitView({ padding: 0.2, duration: 200 }))
  pushConnectLog('画布就绪')
}

function buildDefinition(): WorkflowDefinition {
  return {
    nodes: nodes.value.map((n: any) => ({
      id: n.id,
      type: n.data?.bizType,
      position: n.position,
      config: n.data?.config || {}
    })),
    edges: edges.value.map((e: any) => ({
      id: e.id,
      sourceNodeId: e.source,
      targetNodeId: e.target,
      sourceHandle: e.sourceHandle,
      targetHandle: e.targetHandle
    }))
  }
}

async function applyDefinition(definition: WorkflowDefinition) {
  const normalizedNodes = (definition.nodes || [])
    .filter((n: any) => !!n?.id && !!n?.type && !!n?.position)
    .map((n: any) => ({
      id: String(n.id),
      type: n.type as BizNodeType,
      position: {
        x: Number(n.position?.x ?? 0),
        y: Number(n.position?.y ?? 0)
      },
      config: n.config || {}
    }))

  const nodeIdSet = new Set(normalizedNodes.map((n) => n.id))

  const normalizedEdges = (definition.edges || [])
    .map((e: any) => ({
      id: String(e.id || `e-${e.sourceNodeId}-${e.targetNodeId}-${Date.now()}`),
      sourceNodeId: String(e.sourceNodeId || ''),
      targetNodeId: String(e.targetNodeId || ''),
      sourceHandle: e.sourceHandle || null,
      targetHandle: e.targetHandle || null
    }))
    .filter((e: any) => !!e.sourceNodeId && !!e.targetNodeId)
    .filter((e: any) => nodeIdSet.has(e.sourceNodeId) && nodeIdSet.has(e.targetNodeId))

  const mappedNodes = normalizedNodes.map((n) => ({
    id: n.id,
    type: 'flowNode',
    label: nodeLabel(n.type),
    class: nodeClass(n.type),
    position: n.position,
    data: { label: nodeLabel(n.type), bizType: n.type, config: n.config }
  })) as any

  const mappedEdges = normalizedEdges
    .map((e) => 
      createEdge(
        e.sourceNodeId,
        e.targetNodeId,
        e.id,
        e.sourceHandle || null,
        e.targetHandle || null
      )
    )
    .filter((e: any) => !!e?.id && !!e?.source && !!e?.target) as any

  // 彻底重置状态
  selectedNode.value = null
  selectedEdge.value = null

  try {
    // 分阶段应用，避免中间态触发 Vue Flow 内部 edge 渲染异常
    setEdges([] as any)
    setNodes([] as any)
    await nextTick()

    setNodes(mappedNodes)
    await nextTick()

    setEdges(mappedEdges)
    await nextTick()
    
    nodeCounter = normalizedNodes
      .map((n) => Number(n.id.split('-').pop()))
      .filter((n) => Number.isFinite(n))
      .reduce((m, c) => Math.max(m, c), 0)

    fitView({ padding: 0.2, duration: 200 })
    pushConnectLog(`加载定义: nodes=${normalizedNodes.length}, edges=${normalizedEdges.length}`)
  } catch (error) {
    console.error('加载异常:', error)
    nodes.value = [] as any
    edges.value = [] as any
    showToast('error', '加载失败: ' + (error as any).message)
  }
}

function downloadDefinition() {
  const definition = buildDefinition()
  const content = JSON.stringify(definition, null, 2)
  const blob = new Blob([content], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  const a = document.createElement('a')
  a.href = url
  a.download = `workflow-definition-${timestamp}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  pushConnectLog(`下载定义: nodes=${definition.nodes.length}, edges=${definition.edges.length}`)
}

function openJsonPreview() {
  jsonPreviewText.value = JSON.stringify(buildDefinition(), null, 2)
  jsonPreviewVisible.value = true
}

function closeJsonPreview() {
  jsonPreviewVisible.value = false
}

function triggerDefinitionFilePick() {
  definitionFileInput.value?.click()
}

function validateDefinition(data: any): data is WorkflowDefinition {
  if (!data || !Array.isArray(data.nodes) || !Array.isArray(data.edges)) {
    return false
  }

  const validNodes = data.nodes.every((n: any) =>
    !!n &&
    typeof n.id === 'string' &&
    (n.type === 'start' || n.type === 'llm' || n.type === 'end') &&
    !!n.position &&
    typeof n.position.x === 'number' &&
    typeof n.position.y === 'number'
  )

  const validEdges = data.edges.every((e: any) =>
    !!e &&
    typeof e.sourceNodeId === 'string' &&
    typeof e.targetNodeId === 'string'
  )

  return validNodes && validEdges
}

async function onDefinitionFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  try {
    const text = await file.text()
    const parsed = JSON.parse(text)
    if (!validateDefinition(parsed)) {
      showToast('error', '文件格式不正确：必须使用 sourceNodeId/targetNodeId 新格式')
      return
    }
    await applyDefinition(parsed)
    pushConnectLog(`已加载文件: ${file.name}`)
    showToast('success', `加载成功：${file.name}`)
  } catch (error) {
    console.error('加载文件失败:', error)
    showToast('error', '加载失败：请确认是有效的 JSON 且包含 nodes/edges')
  } finally {
    input.value = ''
  }
}
</script>

<style scoped>
.workflow-editor-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 600px;
  background: #fff;
  overflow: hidden;
}

.editor-toolbar {
  padding: 12px 16px;
  border-bottom: 1px solid #dbe4f0;
  background: linear-gradient(180deg, #f8fbff 0%, #f4f7fb 100%);
  flex-shrink: 0;
}

.toolbar-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar-subtitle {
  color: #999;
  font-size: 11px;
  margin-top: 4px;
}

.canvas-floating-actions {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 40;
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: nowrap;
  padding: 6px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #e6edf5;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8), 0 8px 24px rgba(15, 23, 42, 0.12);
  overflow-x: auto;
}

.action-btn {
  border: 1px solid #d0d0d0;
  background: #fff;
  border-radius: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 12px;
}

.canvas-floating-actions .toolbar-btn {
  height: 34px;
  min-width: 112px;
  border-radius: 10px;
  border: 1px solid #d8e3f2;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #274060;
  background: linear-gradient(180deg, #ffffff 0%, #f7f9fc 100%);
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  transition: all 0.18s ease;
}

.canvas-floating-actions .toolbar-btn.icon-only {
  width: 34px;
  min-width: 34px;
  padding: 0;
  border-radius: 9px;
}

.canvas-floating-actions .toolbar-btn:hover {
  border-color: #b8cbe6;
  background: linear-gradient(180deg, #ffffff 0%, #eef4ff 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(37, 99, 235, 0.12);
}

.canvas-floating-actions .toolbar-btn:active {
  transform: translateY(0);
}

.canvas-floating-actions .toolbar-btn .btn-icon {
  font-size: 14px;
  line-height: 1;
}

.canvas-floating-actions .save-btn {
  color: #1f4d95;
}

.canvas-floating-actions .load-btn {
  color: #14532d;
}

.canvas-floating-actions .json-btn {
  color: #4c1d95;
}

.canvas-floating-actions .layout-btn {
  min-width: 34px;
}

.action-btn.primary {
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  border-color: #365fd6;
  color: #fff;
}

.canvas-floating-actions .action-btn.primary {
  box-shadow: 0 4px 12px rgba(59, 91, 219, 0.32);
}

.canvas-floating-actions .action-btn.primary:hover {
  border-color: #274cbe;
  background: linear-gradient(135deg, #1d4ed8 0%, #4338ca 100%);
}

.json-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, 0.38);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.json-modal {
  width: min(920px, 92vw);
  max-height: 80vh;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #dbe4f0;
  box-shadow: 0 20px 45px rgba(2, 6, 23, 0.24);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.json-modal-header {
  height: 46px;
  padding: 0 12px 0 14px;
  border-bottom: 1px solid #e5ecf6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fbff;
}

.json-close-btn {
  width: 28px;
  height: 28px;
  border: 1px solid #d5dfec;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
}

.json-preview-content {
  margin: 0;
  padding: 12px 14px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  color: #0f172a;
  background: #ffffff;
}

.action-btn.danger {
  margin-top: 12px;
  background: #fff1f2;
  border-color: #fecdd3;
  color: #e11d48;
}

.editor-layout {
  display: grid;
  grid-template-columns: 160px 1fr 280px;
  gap: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.panel-left {
  border-right: 1px solid #e0e0e0;
  overflow-y: hidden;
  background: #fafafa;
}

.panel-center {
  min-height: 0;
  overflow: hidden;
  background: #fff;
  position: relative;
  display: flex;
}

.panel-right {
  border-left: 1px solid #e0e0e0;
  overflow-y: hidden;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.node-palette {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px;
  background: #fff;
  border: 1px solid #d0d0d0;
  border-radius: 4px;
  cursor: grab;
  user-select: none;
  transition: all 0.2s;
}

.node-item:hover {
  background: #f0f7ff;
  border-color: #0066cc;
  box-shadow: 0 2px 4px rgba(0, 102, 204, 0.1);
}

.node-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.node-info {
  flex: 1;
  min-width: 0;
}

.node-title {
  font-size: 12px;
  font-weight: 500;
  color: #333;
}

.node-desc {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}

.node-context-menu {
  position: fixed;
  z-index: 1000;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.16);
  width: 140px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node-context-menu button {
  border: none;
  background: #f8fafc;
  text-align: left;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
}

.node-context-menu button:hover {
  background: #eff6ff;
}

.node-context-menu button.danger:hover {
  background: #fef2f2;
  color: #dc2626;
}

.editor-canvas {
  position: relative;
  min-height: 0;
  width: 100%;
  height: 100%;
}

.config-panel {
  padding: 12px;
  font-size: 12px;
  height: 100%;
  overflow-y: auto;
}

.config-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.config-content {
  font-size: 12px;
}

.selected-node-summary {
  margin-bottom: 12px;
  padding: 8px;
  background: #f0f9ff;
  border-left: 3px solid #0066cc;
}

.edge-summary {
  background: #fff7ed;
  border-left-color: #f97316;
}

.selected-node-meta {
  color: #666;
  font-size: 11px;
  margin-top: 4px;
}

.placeholder {
  color: #999;
  font-size: 12px;
  text-align: center;
  padding: 20px 0;
}

label {
  display: block;
  margin-top: 10px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #666;
}

input,
select,
textarea,
button {
  width: 100%;
  box-sizing: border-box;
}

input,
select,
textarea {
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12px;
}

.connect-log-box,
.manual-edge-box {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.connect-log-line {
  font-size: 11px;
  color: #4b5563;
  line-height: 1.5;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

:deep(.vue-flow__edge-path) {
  stroke: #2563eb;
  stroke-width: 2.5;
}

:deep(.vue-flow__connection-path) {
  stroke: #2563eb;
  stroke-width: 2.5;
}

:deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke: #f97316;
}

:deep(.vue-flow__node) {
  overflow: visible !important;
  min-width: 0;
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
}

:deep(.vue-flow__node.selected) {
  box-shadow: none;
}

:deep(.vue-flow__node.node-start) {
  border-left: none;
}

:deep(.vue-flow__node.node-llm) {
  border-left: none;
}

:deep(.vue-flow__node.node-end) {
  border-left: none;
}

:deep(.vue-flow__handle) {
  width: 10px;
  height: 10px;
  border: 1px solid #fff;
  background: #2563eb;
  cursor: crosshair;
  z-index: 20;
  pointer-events: all;
}

.floating-toast {
  position: fixed;
  right: 18px;
  top: 18px;
  z-index: 1300;
  min-width: 220px;
  max-width: 420px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #dbe4f0;
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.18);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  background: #fff;
}

.floating-toast .toast-icon {
  line-height: 1;
}

.floating-toast.toast-success {
  color: #14532d;
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.floating-toast.toast-error {
  color: #991b1b;
  border-color: #fecaca;
  background: #fef2f2;
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: all 0.18s ease;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
