<template>
  <div class="workflow-editor-container">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div style="display: flex; align-items: center; gap: 16px; flex-wrap: wrap">
        <div>
          <strong v-if="detail" style="font-size: 14px">{{ detail.name }}</strong>
          <div v-if="detail" style="color: #999; font-size: 11px">
            流程ID: {{ detail.workflowId }} | 版本: {{ detail.draft?.version }}
          </div>
        </div>

        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
          <el-input v-model="name" size="small" placeholder="输入流程名称" style="width: 120px" />
          <el-button size="small" type="info" @click="onUpdateMeta">更新名称</el-button>
          <el-button size="small" @click="onSaveDraft">💾 保存</el-button>
          <el-button size="small" type="primary" @click="onValidate">✓ 校验</el-button>
          <el-button size="small" type="success" @click="onDebug">▶️ 调试</el-button>
          <el-button size="small" type="warning" @click="onPublish">🚀 发布</el-button>
          <el-button size="small" type="info" plain :disabled="!runId" @click="debugPanelVisible = true">📋 调试面板</el-button>
          <el-button size="small" type="info" plain @click="connectionLogVisible = true">🧾 连线日志</el-button>
          <el-button size="small" type="info" plain @click="showJsonViewer">👁️ 查看JSON</el-button>
        </div>
      </div>
    </div>

    <!-- 三分布局：左节点库 + 中画布 + 右配置 -->
    <div class="editor-layout">
      <!-- 左：节点库 -->
      <div class="panel-left">
        <div class="node-palette">
          <h4 style="margin: 0 0 12px 0; font-size: 13px; color: #333">节点库</h4>
          
          <!-- 开始节点 -->
          <div class="node-item" draggable="true" @dragstart="(e) => onNodeDragStart(e, 'start')">
            <span class="node-icon">🟢</span>
            <div class="node-info">
              <div class="node-title">开始</div>
              <div class="node-desc">流程起点</div>
            </div>
          </div>

          <!-- LLM 节点 -->
          <div class="node-item" draggable="true" @dragstart="(e) => onNodeDragStart(e, 'llm')">
            <span class="node-icon">🤖</span>
            <div class="node-info">
              <div class="node-title">LLM</div>
              <div class="node-desc">AI 处理节点</div>
            </div>
          </div>

          <!-- 结束节点 -->
          <div class="node-item" draggable="true" @dragstart="(e) => onNodeDragStart(e, 'end')">
            <span class="node-icon">⏹️</span>
            <div class="node-info">
              <div class="node-title">结束</div>
              <div class="node-desc">流程终点</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 中：画布 -->
      <div ref="canvasHostRef" class="panel-center editor-canvas" @dragover="onCanvasDragover" @drop="onCanvasDrop">
        <div v-if="forceFallbackCanvas" class="fallback-canvas" @click="clearSelection">
          <svg class="fallback-edges" width="100%" height="100%">
            <path
              v-for="edge in storedEdges"
              :key="edge.id"
              :d="buildFallbackEdgePath(edge)"
              class="fallback-edge-path"
              :class="{ 'edge-selected': selectedEdge?.id === edge.id }"
              @click.stop="onFallbackEdgeClick(edge)"
            />
            <!-- 临时拖拽连线 -->
            <path
              v-if="drawingEdge"
              :d="drawingEdge"
              class="fallback-edge-path"
              style="stroke: #f97316; stroke-dasharray: 5,5; opacity: 0.7"
            />
          </svg>

          <div
            v-for="node in nodes"
            :key="node.id"
            class="fallback-node"
            :class="[
              node.data?.type ? `node-${node.data.type}` : 'node-default',
              { selected: selectedNode?.id === node.id }
            ]"
            :style="getFallbackNodeStyle(node)"
            @click.stop="onFallbackNodeClick(node)"
            @pointerdown.stop="onFallbackNodePointerDown($event, node)"
            @contextmenu.prevent="onNodeContextMenu($event, node)"
          >
            {{ node.data?.label || node.label || node.id }}
            <!-- 拖拽连线的小把手 -->
            <div
              class="node-connect-handle"
              @pointerdown.stop="startDrawingEdge($event, node)"
              title="拖拽创建连线"
            />
          </div>
        </div>

        <VueFlow
          v-else
          style="width: 100%; height: 100%"
          :nodes="nodes"
          :edges="edges"
          @node-click="onNodeClick"
          @connect="onFlowConnect"
          @edge-click="onEdgeClick"
          @connect-start="onConnectStart"
          @connect-end="onConnectEnd"
          @pane-ready="onPaneReady"
          @pane-click="clearSelection"
          :fitViewOptions="{ padding: 0.1 }"
          :fit-view-on-init="false"
          :connection-mode="ConnectionMode.Loose"
          :is-valid-connection="isValidConnection"
          :nodes-connectable="true"
          :edges-updatable="true"
          :elements-selectable="true"
        >
          <Background :patternSize="16" />
          <Controls />
        </VueFlow>
      </div>

      <!-- 右：配置面板 -->
      <div class="panel-right">
        <!-- 工作流入参配置标签 -->
        <el-tabs style="flex: 1; min-height: 0; overflow: hidden">
          <el-tab-pane label="🔧 工作流入参">
            <div class="panel-scrollable" style="padding: 12px; font-size: 12px; height: 100%; overflow-y: auto">
              <div style="margin-bottom: 12px">
                <div style="font-weight: 500; margin-bottom: 8px">调试入参</div>
                <div v-if="selectedNodeWorkflowInputs.length > 0" style="color: #666; font-size: 11px; margin-bottom: 8px">
                  当前节点：{{ selectedNode?.data?.label || selectedNode?.id }}（仅显示该节点使用的工作流入参）
                </div>
                <div v-if="visibleWorkflowInputs.length === 0" style="color: #999; font-size: 11px">
                  {{ workflowInputEmptyText }}
                </div>
                <div v-for="(input, idx) in visibleWorkflowInputs" :key="idx" style="margin-bottom: 8px">
                  <label style="display: block; margin-bottom: 4px; font-size: 11px; color: #666">{{ input }}:</label>
                  <el-input
                    v-model="debugInputValues[input]"
                    :placeholder="`输入 ${input} 的值`"
                    size="small"
                    type="textarea"
                    rows="3"
                  />
                </div>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="⚙️ 节点配置">
            <div class="panel-scrollable" style="padding: 12px; font-size: 12px; height: 100%; overflow-y: auto">
              <div v-if="selectedNode" style="font-size: 12px">
                  <div style="margin-bottom: 12px; padding: 8px; background: #f0f9ff; border-left: 3px solid #0066cc">
                    <strong>{{ selectedNode.data?.label || selectedNode.id }}</strong>
                    <div style="color: #666; font-size: 11px; margin-top: 4px">
                      类型: {{ selectedNode.data?.type || 'unknown' }}
                    </div>
                  </div>

                  <!-- LLM 节点配置表单 -->
                  <div v-if="selectedNode.data?.type === 'llm'" style="margin-bottom: 12px">
                    <div style="margin-bottom: 8px">
                      <label style="display: block; margin-bottom: 4px; font-weight: 500">输入模式:</label>
                      <el-select
                        v-model="selectedNode.data.config.mode"
                        size="small"
                        style="width: 100%"
                        @change="onNodeConfigChange"
                      >
                        <el-option label="CHAT（默认注入 sys.query/sys.chat_history/sys.conversation_id）" value="chat" />
                        <el-option label="TRANSFORM（按入参映射手动配置）" value="transform" />
                      </el-select>
                    </div>

                    <div style="margin-bottom: 8px">
                      <label style="display: block; margin-bottom: 4px; font-weight: 500">模型:</label>
                      <el-select
                        v-model="selectedNode.data.config.modelKey"
                        size="small"
                        placeholder="请选择模型"
                        filterable
                        clearable
                        style="width: 100%"
                        :disabled="enabledLlmModels.length === 0"
                        @change="onNodeConfigChange"
                      >
                        <el-option
                          v-for="item in enabledLlmModels"
                          :key="item.modelKey"
                          :label="`${item.displayName} (${item.targetModel})`"
                          :value="item.modelKey"
                        />
                      </el-select>
                      <div v-if="enabledLlmModels.length === 0" style="color:#f56c6c;font-size:12px;margin-top:6px;">
                        未配置启用模型，请先到"模型维护"新增并启用模型
                      </div>
                    </div>

                    <div style="margin-bottom: 8px">
                      <label style="display: block; margin-bottom: 4px; font-weight: 500">温度 (0-2):</label>
                      <el-input-number
                        v-model="selectedNode.data.config.temperature"
                        :min="0"
                        :max="2"
                        :step="0.1"
                        size="small"
                        @change="onNodeConfigChange"
                      />
                    </div>

                    <div style="margin-bottom: 8px">
                      <label style="display: block; margin-bottom: 4px; font-weight: 500">最大令牌数:</label>
                      <el-input-number
                        v-model="selectedNode.data.config.maxTokens"
                        :min="1"
                        :max="10000"
                        :step="100"
                        size="small"
                        @change="onNodeConfigChange"
                      />
                    </div>

                    <div style="margin-bottom: 12px">
                      <label style="display: block; margin-bottom: 4px; font-weight: 500">提示词:</label>
                      <div v-if="selectedNode.data.config.mode === 'chat'" style="color:#909399;font-size:11px;margin-bottom:6px;">
                        CHAT 模式固定使用 sys.query，提示词不可修改
                      </div>
                      <el-input
                        v-model="selectedNode.data.config.userPromptTemplate"
                        type="textarea"
                        rows="4"
                        size="small"
                        placeholder="支持模板变量如 {{sys.query}}、{{sys.chat_history}}"
                        :disabled="selectedNode.data.config.mode === 'chat'"
                        @change="onNodeConfigChange"
                      />
                    </div>

                    <!-- 入参配置 -->
                    <div style="margin-bottom: 12px; padding: 8px; background: #f9f9f9; border-radius: 4px; border: 1px solid #eee">
                      <div style="font-weight: 500; margin-bottom: 8px; font-size: 12px">📥 入参映射</div>
                      <div v-if="selectedNode.data.config.mode === 'chat'" style="color:#909399;font-size:11px;margin-bottom:8px;">
                        CHAT 模式下固定注入：sys.query / sys.chat_history / sys.conversation_id（不可手动修改）
                      </div>
                      <div v-if="!selectedNode.data.config.inputs" style="color: #999; font-size: 11px; margin-bottom: 8px">
                        暂无入参配置
                      </div>
                      <div style="display: flex; flex-direction: column; gap: 6px">
                        <div v-for="(input, idx) in (selectedNode.data.config.inputs || [])" :key="idx" style="display: flex; flex-direction: column; gap: 4px; padding: 6px; background: #fff; border: 1px solid #e0e0e0; border-radius: 4px">
                          <!-- 第一行：参数名和数据源选择 -->
                          <div style="display: flex; gap: 6px; align-items: center">
                            <el-input
                              :model-value="input.name"
                              size="small"
                              placeholder="参数名"
                              style="flex: 1; min-width: 0"
                              disabled
                            />
                            <span style="color: #999">←</span>
                            <el-select
                              :model-value="input.source"
                              size="small"
                              placeholder="数据源"
                              style="flex: 1; min-width: 0"
                              :disabled="selectedNode.data.config.mode === 'chat'"
                              @update:model-value="(val) => { if (selectedNode.data.config.inputs) selectedNode.data.config.inputs[idx].source = val; onNodeConfigChange() }"
                            >
                              <el-option label="前置节点输出" value="previous" />
                              <el-option label="工作流入参" value="workflow" />
                              <el-option label="常量" value="constant" />
                            </el-select>
                            <el-button size="small" type="danger" text :disabled="selectedNode.data.config.mode === 'chat'" @click="removeInput(idx)">🗑️</el-button>
                          </div>
                        
                          <!-- 第二行：常量值输入（仅当 source=constant 时显示） -->
                          <div v-if="input.source === 'constant'" style="display: flex; gap: 6px; align-items: center">
                            <span style="font-size: 11px; color: #666; flex: 0 0 auto">常量值:</span>
                            <el-input
                              :model-value="input.value"
                              size="small"
                              placeholder="输入常量值"
                              style="flex: 1; min-width: 0"
                              :disabled="selectedNode.data.config.mode === 'chat'"
                              @update:model-value="(val) => { if (selectedNode.data.config.inputs) selectedNode.data.config.inputs[idx].value = val; onNodeConfigChange() }"
                            />
                          </div>
                        </div>
                        <el-button size="small" style="width: 100%; margin-top: 6px" :disabled="selectedNode.data.config.mode === 'chat'" @click="addInput">+ 添加入参</el-button>
                      </div>
                    </div>
                  </div>

                  <div style="margin-bottom: 12px">
                    <el-button
                      size="small"
                      type="danger"
                      style="width: 100%"
                      @click="onDeleteSelectedNode"
                    >
                      🗑️ 删除节点
                    </el-button>
                  </div>
                </div>

                <!-- 选中边线配置 -->
                <div v-else-if="selectedEdge" style="font-size: 12px">
                  <div style="margin-bottom: 12px; padding: 8px; background: #fff7ed; border-left: 3px solid #f97316">
                    <strong>已选中连线</strong>
                    <div style="color: #666; font-size: 11px; margin-top: 4px">
                      {{ selectedEdge.source }} → {{ selectedEdge.target }}
                    </div>
                  </div>
                  <el-button size="small" type="danger" style="width: 100%" @click="onDeleteSelectedEdge">
                    🗑️ 删除连线
                  </el-button>
                </div>

                <!-- 默认状态：未选中 -->
                <div v-else style="font-size: 12px">
                  <div style="color: #999; text-align: center; padding: 20px 0">
                    点击画布上的节点进行配置
                  </div>

                  <!-- 新增连线表单 -->
                  <div style="margin-top: 16px; padding: 12px; background: #f0f9ff; border: 1px solid #bfdbfe; border-radius: 4px">
                    <div style="font-weight: 500; margin-bottom: 8px">➕ 新增连线</div>
                    <div style="margin-bottom: 8px">
                      <label style="display: block; margin-bottom: 4px; font-size: 11px; color: #666">源节点:</label>
                      <el-select
                        v-model="newEdgeForm.sourceNodeId"
                        size="small"
                        placeholder="选择源节点"
                        style="width: 100%"
                        clearable
                      >
                        <el-option v-for="node in nodes" :key="node.id" :label="`${node.data?.label} (${node.id})`" :value="node.id" />
                      </el-select>
                    </div>
                    <div style="margin-bottom: 8px">
                      <label style="display: block; margin-bottom: 4px; font-size: 11px; color: #666">目标节点:</label>
                      <el-select
                        v-model="newEdgeForm.targetNodeId"
                        size="small"
                        placeholder="选择目标节点"
                        style="width: 100%"
                        clearable
                      >
                        <el-option v-for="node in nodes" :key="node.id" :label="`${node.data?.label} (${node.id})`" :value="node.id" />
                      </el-select>
                    </div>
                    <el-button
                      size="small"
                      type="primary"
                      style="width: 100%"
                      :disabled="!newEdgeForm.sourceNodeId || !newEdgeForm.targetNodeId || newEdgeForm.sourceNodeId === newEdgeForm.targetNodeId"
                      @click="addNewEdge"
                    >
                      🔗 创建连线
                    </el-button>
                  </div>
                </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <el-drawer
      v-model="debugPanelVisible"
      title="调试执行日志"
      size="40%"
      :with-header="true"
      destroy-on-close
    >
      <div class="debug-panel-meta">
        <div><strong>RunId：</strong>{{ runId || '-' }}</div>
        <div><strong>状态：</strong>{{ debugRunStatus || '-' }}</div>
      </div>
      <el-collapse v-if="runDetailText" class="debug-result-collapse" accordion>
        <el-collapse-item title="最近运行结果" name="run-result">
          <pre class="debug-result-pre">{{ runDetailText }}</pre>
        </el-collapse-item>
      </el-collapse>
      <div ref="debugLogContainerRef" class="debug-log-scroll">
        <div v-if="debugLogs.length === 0" class="debug-log-empty">暂无日志</div>
        <div
          v-for="item in debugLogs"
          :key="item.id"
          class="debug-log-item"
          :class="[`debug-log-${item.level}`, { 'debug-log-clickable': !!item.nodeId }]"
          @click="onDebugLogClick(item)"
        >
          <div class="debug-log-time">{{ item.time }}</div>
          <div class="debug-log-text">{{ item.message }}</div>
          <div v-if="item.nodeId" class="debug-log-hint">点击查看节点详情</div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="nodeTraceDialogVisible" :title="`节点详情 - ${selectedNodeTrace?.nodeId || '-'}`" width="60%">
      <div v-if="selectedNodeTrace" style="font-size: 12px; color: #606266; margin-bottom: 8px">
        节点类型：{{ selectedNodeTrace.nodeType || '-' }} ｜ 状态：{{ selectedNodeTrace.status || '-' }} ｜ 耗时：{{ selectedNodeTrace.durationMs || 0 }}ms
      </div>
      <el-collapse>
        <el-collapse-item title="输入（inputSnapshot）" name="input">
          <pre class="debug-result-pre">{{ JSON.stringify(selectedNodeTrace?.inputSnapshot || {}, null, 2) }}</pre>
        </el-collapse-item>
        <el-collapse-item title="输出（outputSnapshot）" name="output">
          <pre class="debug-result-pre">{{ JSON.stringify(selectedNodeTrace?.outputSnapshot || {}, null, 2) }}</pre>
        </el-collapse-item>
      </el-collapse>
    </el-dialog>

    <!-- JSON 查看器对话框 -->
    <el-dialog v-model="jsonViewerVisible" title="画布 JSON 定义" width="70%" @close="jsonViewerContent = ''">
      <div style="display: flex; gap: 8px; margin-bottom: 12px">
        <el-button type="primary" size="small" @click="copyJsonToClipboard">📋 复制到剪贴板</el-button>
      </div>
      <div style="max-height: 500px; overflow-y: auto; background: #f5f5f5; padding: 12px; border-radius: 4px; border: 1px solid #ddd">
        <pre style="margin: 0; font-size: 12px; color: #333; white-space: pre-wrap; word-break: break-all">{{ jsonViewerContent }}</pre>
      </div>
    </el-dialog>

    <el-dialog v-model="connectionLogVisible" title="连线调试日志" width="50%">
      <div class="connect-log-box" style="max-height: 420px; overflow-y: auto">
        <div v-if="connectionLogs.length === 0" style="color: #999; font-size: 12px">暂无日志</div>
        <div v-for="(log, idx) in connectionLogs" :key="idx" class="connect-log-line">{{ log }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { VueFlow, useVueFlow, ConnectionMode } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import {
  listLlmModels,
  getWorkflowDetail,
  getRunDetail,
  publishWorkflow,
  saveWorkflowDraft,
  startDebugRun,
  updateWorkflow,
  validateWorkflow
} from '../../api/workflow'
import type { WorkflowDetail } from '../../types/workflow'
import type { LlmModelItem } from '../../types/workflow'

const route = useRoute()
const id = String(route.params.id)

const { nodes, edges, addNodes, addEdges, onConnect, screenToFlowCoordinate, fitView, setViewport } = useVueFlow()

function onFlowConnect(params: any) {
  if (!params?.source || !params?.target) return

  lastConnectAt.value = Date.now()
  pushConnectLog(`收到 connect 事件: ${params.source} -> ${params.target}`)

  const exists = storedEdges.value.some(
    (e) =>
      e.source === params.source &&
      e.target === params.target &&
      (e.sourceHandle || '') === (params.sourceHandle || '') &&
      (e.targetHandle || '') === (params.targetHandle || '')
  )
  if (exists) {
    pushConnectLog(`忽略重复连线: ${params.source} -> ${params.target}`)
    return
  }

  const edge = {
    ...params,
    id: `e-${params.source}-${params.target}-${Date.now()}`
  }
  storedEdges.value = [...storedEdges.value, edge as any]
  syncVisibleEdges()
  pushConnectLog(`连线成功: ${params.source} -> ${params.target}`)
}

onConnect((params: any) => {
  onFlowConnect(params)
})

const detail = ref<WorkflowDetail | null>(null)
const runId = ref('')
const runDetailText = ref('')
const debugRunStatus = ref('')
const name = ref('')
const selectedNode = ref<any>(null)
const selectedEdge = ref<any>(null)
const connectionLogs = ref<string[]>([])
const enabledLlmModels = ref<LlmModelItem[]>([])
const debugPanelVisible = ref(false)
const connectionLogVisible = ref(false)
const debugLogContainerRef = ref<HTMLElement | null>(null)
const debugLogs = ref<Array<{ id: string; time: string; message: string; level: 'info' | 'success' | 'error'; nodeId?: string }>>([])
const nodeTraceDialogVisible = ref(false)
const selectedNodeTrace = ref<any>(null)
const debugNodeTraceMap = ref<Record<string, any>>({})
const pendingConnectStart = ref<{ nodeId: string; handleId: string | null } | null>(null)
const lastConnectAt = ref(0)
const paneReady = ref(false)
const jsonViewerVisible = ref(false)
const jsonViewerContent = ref('')
const newEdgeForm = ref({ sourceNodeId: '', targetNodeId: '' })
const workflowInputs = ref<string[]>([])
const debugInputValues = ref<Record<string, string>>({})
const pendingFitAfterLoad = ref(false)
const canvasHostRef = ref<HTMLElement | null>(null)
const edgeRenderEnabled = ref(false)
const storedEdges = ref<any[]>([])
const forceFallbackCanvas = ref(true)
let nodeCounter = 0
let debugPollTimer: number | null = null
const debugNodeEventKeys = new Set<string>()
const FALLBACK_NODE_WIDTH = 160
const FALLBACK_NODE_HEIGHT = 48
let fallbackDraggingNodeId: string | null = null
let fallbackDragOffset = { x: 0, y: 0 }
let drawingSourceNodeId: string | null = null
let drawingEdge = ref('')
let lastPointerEvent: PointerEvent | null = null

const selectedNodeWorkflowInputs = computed<string[]>(() => {
  const node = selectedNode.value
  if (!node || node?.data?.type !== 'llm') {
    return []
  }
  const cfgInputs = Array.isArray(node?.data?.config?.inputs) ? node.data.config.inputs : []
  const names = cfgInputs
    .filter((inp: any) => inp?.source === 'workflow' && inp?.name)
    .map((inp: any) => String(inp.name).trim())
    .filter((name: string) => !!name)
  return Array.from(new Set(names))
})

const visibleWorkflowInputs = computed<string[]>(() => {
  const type = selectedNode.value?.data?.type
  if (type === 'llm') {
    return selectedNodeWorkflowInputs.value
  }
  if (type === 'end') {
    return []
  }
  return workflowInputs.value
})

const workflowInputEmptyText = computed<string>(() => {
  const type = selectedNode.value?.data?.type
  if (type === 'llm') {
    return '当前节点未配置工作流入参映射'
  }
  if (type === 'end') {
    return '结束节点不需要调试入参'
  }
  return '暂无入参'
})

function appendDebugLog(
  message: string,
  level: 'info' | 'success' | 'error' = 'info',
  extras?: { nodeId?: string }
) {
  const now = new Date()
  debugLogs.value.push({
    id: `${now.getTime()}-${Math.random().toString(36).slice(2, 8)}`,
    time: now.toLocaleTimeString(),
    message,
    level,
    nodeId: extras?.nodeId
  })
  if (debugLogs.value.length > 500) {
    debugLogs.value = debugLogs.value.slice(-500)
  }
  nextTick(() => {
    if (debugLogContainerRef.value) {
      debugLogContainerRef.value.scrollTop = debugLogContainerRef.value.scrollHeight
    }
  })
}

function onDebugLogClick(item: { nodeId?: string }) {
  if (!item.nodeId) return
  const trace = debugNodeTraceMap.value[item.nodeId]
  if (!trace) return
  selectedNodeTrace.value = trace
  nodeTraceDialogVisible.value = true
}

function stopDebugPolling() {
  if (debugPollTimer != null) {
    window.clearInterval(debugPollTimer)
    debugPollTimer = null
  }
}

async function pollDebugRunDetail() {
  if (!runId.value) return
  try {
    const detailData = await getRunDetail(runId.value)
    debugRunStatus.value = detailData?.status || ''
    runDetailText.value = JSON.stringify(detailData, null, 2)

    const nodeList = Array.isArray(detailData?.nodes) ? detailData.nodes : []
    for (const node of nodeList) {
      if (node?.nodeId) {
        debugNodeTraceMap.value[node.nodeId] = node
      }
      const key = `${node.nodeId}|${node.status}|${node.durationMs || 0}|${node.errorMessage || ''}`
      if (debugNodeEventKeys.has(key)) continue
      debugNodeEventKeys.add(key)
      if (node.status === 'success') {
        appendDebugLog(`节点 ${node.nodeId} 执行成功（${node.durationMs || 0}ms）`, 'success', { nodeId: node.nodeId })
      } else if (node.status === 'failed') {
        appendDebugLog(`节点 ${node.nodeId} 执行失败：${node.errorMessage || '未知错误'}`, 'error', { nodeId: node.nodeId })
      } else {
        appendDebugLog(`节点 ${node.nodeId} 状态：${node.status}`, 'info', { nodeId: node.nodeId })
      }
    }

    if (detailData?.status === 'success' || detailData?.status === 'failed') {
      appendDebugLog(`运行结束：${detailData.status}`, detailData.status === 'success' ? 'success' : 'error')
      stopDebugPolling()
    }
  } catch (err: any) {
    appendDebugLog(`拉取调试详情失败：${err?.message || '未知错误'}`, 'error')
    stopDebugPolling()
  }
}

function startDebugPolling() {
  stopDebugPolling()
  void pollDebugRunDetail()
  debugPollTimer = window.setInterval(() => {
    void pollDebugRunDetail()
  }, 1200)
}

function getNodeDisplayLabel(type?: string) {
  if (type === 'start') return '🟢 开始'
  if (type === 'llm') return '🤖 LLM'
  if (type === 'end') return '⏹️ 结束'
  return type || '节点'
}

function syncVisibleEdges() {
  edges.value = edgeRenderEnabled.value ? storedEdges.value.map((e) => ({ ...e })) : []
  if (!edgeRenderEnabled.value) {
    selectedEdge.value = null
  }
}

function toggleEdgeRender() {
  edgeRenderEnabled.value = !edgeRenderEnabled.value
  syncVisibleEdges()
  pushConnectLog(edgeRenderEnabled.value ? `显示连线: ${storedEdges.value.length}` : '隐藏连线')
}

function getFallbackNodeStyle(node: any) {
  const x = Number(node?.position?.x || 0)
  const y = Number(node?.position?.y || 0)
  return {
    left: `${x}px`,
    top: `${y}px`,
    width: `${FALLBACK_NODE_WIDTH}px`,
    minHeight: `${FALLBACK_NODE_HEIGHT}px`
  }
}

function buildFallbackEdgePath(edge: any) {
  const sourceNode = nodes.value.find((node: any) => node.id === edge.source)
  const targetNode = nodes.value.find((node: any) => node.id === edge.target)
  if (!sourceNode || !targetNode) return ''

  const sx = Number(sourceNode.position?.x || 0) + FALLBACK_NODE_WIDTH
  const sy = Number(sourceNode.position?.y || 0) + FALLBACK_NODE_HEIGHT / 2
  const tx = Number(targetNode.position?.x || 0)
  const ty = Number(targetNode.position?.y || 0) + FALLBACK_NODE_HEIGHT / 2
  const cx = (sx + tx) / 2
  return `M ${sx} ${sy} C ${cx} ${sy}, ${cx} ${ty}, ${tx} ${ty}`
}

function onFallbackNodeClick(node: any) {
  selectedNode.value = node
  selectedEdge.value = null
}

function onFallbackNodePointerDown(event: PointerEvent, node: any) {
  if (!canvasHostRef.value) return
  // 如果在连线状态，则完成连线
  if (drawingSourceNodeId && drawingSourceNodeId !== node.id) {
    const exists = storedEdges.value.some((e) => e.source === drawingSourceNodeId && e.target === node.id)
    if (!exists) {
      const newEdge = {
        id: `${drawingSourceNodeId}-${node.id}`,
        source: drawingSourceNodeId,
        target: node.id
      }
      storedEdges.value.push(newEdge)
      syncVisibleEdges()
      pushConnectLog(`拖拽连线: ${drawingSourceNodeId} → ${node.id}`)
      ElMessage.success('连线已创建')
    } else {
      ElMessage.warning('该连线已存在')
    }
    drawingSourceNodeId = null
    drawingEdge.value = ''
    return
  }

  // 否则进入拖拽节点模式
  fallbackDraggingNodeId = node.id
  const rect = canvasHostRef.value.getBoundingClientRect()
  fallbackDragOffset = {
    x: event.clientX - rect.left - Number(node.position?.x || 0),
    y: event.clientY - rect.top - Number(node.position?.y || 0)
  }
}

function onFallbackPointerMove(event: PointerEvent) {
  if (!canvasHostRef.value) return
  
  lastPointerEvent = event
  const rect = canvasHostRef.value.getBoundingClientRect()
  const mouseX = event.clientX - rect.left
  const mouseY = event.clientY - rect.top

  // 处理拖拽连线
  if (drawingSourceNodeId) {
    const sourceNode = nodes.value.find((n: any) => n.id === drawingSourceNodeId)
    if (sourceNode) {
      const sx = Number(sourceNode.position?.x || 0) + FALLBACK_NODE_WIDTH
      const sy = Number(sourceNode.position?.y || 0) + FALLBACK_NODE_HEIGHT / 2
      const cx = (sx + mouseX) / 2
      drawingEdge.value = `M ${sx} ${sy} C ${cx} ${sy}, ${cx} ${mouseY}, ${mouseX} ${mouseY}`
    }
    return
  }

  // 处理拖拽节点
  if (!fallbackDraggingNodeId) return
  const node = nodes.value.find((item: any) => item.id === fallbackDraggingNodeId)
  if (!node) return

  node.position = {
    x: Math.max(0, mouseX - fallbackDragOffset.x),
    y: Math.max(0, mouseY - fallbackDragOffset.y)
  }
}

function onFallbackPointerUp() {
  if (!fallbackDraggingNodeId && drawingSourceNodeId && lastPointerEvent && canvasHostRef.value) {
    // 在拖拽连线状态下释放，检查鼠标下方是否有节点
    const rect = canvasHostRef.value.getBoundingClientRect()
    const mouseX = lastPointerEvent.clientX - rect.left
    const mouseY = lastPointerEvent.clientY - rect.top

    // 检查是否有节点在鼠标位置
    for (const node of nodes.value) {
      const nodeX = Number(node.position?.x || 0)
      const nodeY = Number(node.position?.y || 0)
      
      // 检查鼠标是否在节点范围内
      if (
        mouseX >= nodeX &&
        mouseX <= nodeX + FALLBACK_NODE_WIDTH &&
        mouseY >= nodeY &&
        mouseY <= nodeY + FALLBACK_NODE_HEIGHT &&
        node.id !== drawingSourceNodeId
      ) {
        // 完成连线
        const exists = storedEdges.value.some((e) => e.source === drawingSourceNodeId && e.target === node.id)
        if (!exists) {
          const newEdge = {
            id: `${drawingSourceNodeId}-${node.id}`,
            source: drawingSourceNodeId,
            target: node.id
          }
          storedEdges.value.push(newEdge)
          syncVisibleEdges()
          pushConnectLog(`拖拽连线: ${drawingSourceNodeId} → ${node.id}`)
          ElMessage.success('连线已创建')
        } else {
          ElMessage.warning('该连线已存在')
        }
        break
      }
    }
  }

  fallbackDraggingNodeId = null
  drawingSourceNodeId = null
  drawingEdge.value = ''
  lastPointerEvent = null
}

function startDrawingEdge(event: PointerEvent, node: any) {
  drawingSourceNodeId = node.id
}

function onFallbackEdgeClick(edge: any) {
  selectedEdge.value = edge
  selectedNode.value = null
}

function addNewEdge() {
  const { sourceNodeId, targetNodeId } = newEdgeForm.value
  if (!sourceNodeId || !targetNodeId || sourceNodeId === targetNodeId) return

  // 检查连线是否已存在
  const exists = storedEdges.value.some((e) => e.source === sourceNodeId && e.target === targetNodeId)
  if (exists) {
    ElMessage.warning('该连线已存在')
    return
  }

  const newEdge = {
    id: `${sourceNodeId}-${targetNodeId}`,
    source: sourceNodeId,
    target: targetNodeId
  }

  storedEdges.value.push(newEdge)
  syncVisibleEdges()
  pushConnectLog(`新增连线: ${sourceNodeId} → ${targetNodeId}`)
  newEdgeForm.value = { sourceNodeId: '', targetNodeId: '' }
  ElMessage.success('连线已添加')
}

async function forceFitView() {
  await nextTick()
  setTimeout(() => {
    try {
      // 先重置到稳定视口，避免历史平移/缩放导致节点看不见
      setViewport({ x: 0, y: 0, zoom: 1 })
      fitView({ padding: 0.2, duration: 250 })
    } catch {
      // ignore
    }
  }, 0)
}

async function loadEnabledLlmModels() {
  try {
    enabledLlmModels.value = await listLlmModels({ enabled: 1 })
    ensureAllLlmNodesModelKey()
  } catch {
    enabledLlmModels.value = []
  }
}

function defaultModelKey() {
  return enabledLlmModels.value[0]?.modelKey || ''
}

function normalizeLlmPromptTemplate(value: unknown) {
  const text = String(value || '').trim()
  if (!text || text === '{{question}}') {
    return '{{sys.query}}'
  }
  return text
}

function normalizeLlmMode(config: any) {
  const mode = String(config?.mode || '').trim().toLowerCase()
  if (mode === 'chat' || mode === 'transform') {
    return mode
  }
  const hasMappedInputs = Array.isArray(config?.inputs) && config.inputs.length > 0
  return hasMappedInputs ? 'transform' : 'chat'
}

function ensureAllLlmNodesModelKey() {
  const fallback = defaultModelKey()
  nodes.value = (nodes.value || []).map((node: any) => {
    if (node?.data?.type !== 'llm') {
      return node
    }
    const config = node?.data?.config || {}
    const nextModelKey = String(config.modelKey || '').trim() || fallback
    const nextMode = normalizeLlmMode(config)
    const nextPromptTemplate = nextMode === 'chat' ? '{{sys.query}}' : normalizeLlmPromptTemplate(config.userPromptTemplate)
    return {
      ...node,
      data: {
        ...(node.data || {}),
        config: {
          ...config,
          modelKey: nextModelKey,
          userPromptTemplate: nextPromptTemplate,
          mode: nextMode,
          inputs: nextMode === 'chat' ? [] : (Array.isArray(config.inputs) ? config.inputs : [])
        }
      }
    }
  })
}

function onPaneReady() {
  paneReady.value = true
  const rect = canvasHostRef.value?.getBoundingClientRect()
  pushConnectLog(`画布就绪: ${Math.round(rect?.width || 0)}x${Math.round(rect?.height || 0)}`)
  if (pendingFitAfterLoad.value) {
    pendingFitAfterLoad.value = false
    void forceFitView()
  }
}

function pushConnectLog(message: string) {
  const ts = new Date().toLocaleTimeString()
  connectionLogs.value = [`[${ts}] ${message}`, ...connectionLogs.value].slice(0, 20)
}

function isValidConnection(params: any) {
  const valid = Boolean(params?.source && params?.target && params?.source !== params?.target)
  if (!valid) {
    pushConnectLog(`连线校验失败: source=${params?.source || '-'} target=${params?.target || '-'}`)
  }
  return valid
}

// 删除节点
function deleteNode(nodeId: string) {
  nodes.value = nodes.value.filter((n) => n.id !== nodeId)
  storedEdges.value = storedEdges.value.filter(
    (e) => e.source !== nodeId && e.target !== nodeId
  )
  syncVisibleEdges()
}

// 加载工作流详情
async function load() {
  try {
    detail.value = await getWorkflowDetail(id)
    name.value = detail.value?.name || ''

    const definition = detail.value?.draft?.definition
    if (definition && typeof definition === 'object' && 'nodes' in definition) {
      const defNodes = Array.isArray((definition as any).nodes) ? (definition as any).nodes : []
      const defEdges = Array.isArray((definition as any).edges) ? (definition as any).edges : []

      // 提取工作流入参（从所有节点的 inputs 中提取 source='workflow' 的参数名）
      const inputSet = new Set<string>()
      defNodes.forEach((n: any) => {
        if (n.config?.inputs && Array.isArray(n.config.inputs)) {
          n.config.inputs.forEach((inp: any) => {
            if (inp.source === 'workflow' && inp.name) {
              inputSet.add(inp.name)
            }
          })
        }
      })
      workflowInputs.value = Array.from(inputSet)
      const savedDebugInputs =
        definition && typeof definition === 'object' && (definition as any).debugInputs && typeof (definition as any).debugInputs === 'object'
          ? (definition as any).debugInputs
          : {}
      const restoredInputs: Record<string, string> = {}
      Object.entries(savedDebugInputs).forEach(([k, v]) => {
        restoredInputs[k] = v == null ? '' : String(v)
      })
      workflowInputs.value.forEach((key) => {
        if (restoredInputs[key] == null) {
          restoredInputs[key] = ''
        }
      })
      debugInputValues.value = restoredInputs

      const normalizedNodes = defNodes
        .filter((n: any) => n && n.id != null && n.type != null)
        .map((n: any) => ({
          ...(String(n.type) === 'llm' && !(n.config && String(n.config.modelKey || '').trim())
            ? {
                config: {
                  ...(n.config || {}),
                  modelKey: defaultModelKey()
                }
              }
            : {}),
          id: String(n.id),
          // 使用内置 default 节点兜底渲染，避免自定义节点插槽在特定时机下不渲染
          type: 'default',
          label: getNodeDisplayLabel(String(n.type)),
          data: {
            label: getNodeDisplayLabel(String(n.type)),
            type: String(n.type),
            config:
              String(n.type) === 'llm'
                ? {
                    ...(n.config || {}),
                    modelKey: String(n?.config?.modelKey || '').trim() || defaultModelKey(),
                    mode: normalizeLlmMode(n?.config),
                    userPromptTemplate:
                      normalizeLlmMode(n?.config) === 'chat'
                        ? '{{sys.query}}'
                        : normalizeLlmPromptTemplate(n?.config?.userPromptTemplate),
                    inputs:
                      normalizeLlmMode(n?.config) === 'chat'
                        ? []
                        : (Array.isArray(n?.config?.inputs) ? n.config.inputs : [])
                  }
                : (n.config || {})
          },
          position: n.position || { x: 0, y: 0 }
        }))

      const validNodeIds = new Set(normalizedNodes.map((n: any) => n.id))
      const normalizedEdges = defEdges
        .filter((e: any) => e && typeof e === 'object')
        .map((e: any) => {
          const source = String(e.sourceNodeId || '').trim()
          const target = String(e.targetNodeId || '').trim()
          return {
            id: String(e.id || `${source}-${target}`),
            source,
            target
          }
        })
        .filter((e: any) => e.source && e.target && validNodeIds.has(e.source) && validNodeIds.has(e.target))

      nodes.value = normalizedNodes as any
      storedEdges.value = normalizedEdges as any

      // 紧急兜底：先保证节点可见，连线可按需手动开启
      edgeRenderEnabled.value = false
      syncVisibleEdges()

      // 避免新增节点 ID 与已有节点冲突，导致节点看起来“乱跳/覆盖”
      const maxSuffix = normalizedNodes
        .map((n: any) => Number(String(n.id || '').split('-').pop()))
        .filter((v: number) => Number.isFinite(v))
        .reduce((max: number, cur: number) => Math.max(max, cur), 0)
      nodeCounter = maxSuffix

      pushConnectLog(`加载定义: nodes=${normalizedNodes.length}, edges=${normalizedEdges.length}`)
      if (normalizedEdges.length > 0) {
        pushConnectLog('已临时关闭连线渲染，请点“显示连线”验证')
      }
      const rect = canvasHostRef.value?.getBoundingClientRect()
      pushConnectLog(`当前画布: ${Math.round(rect?.width || 0)}x${Math.round(rect?.height || 0)}`)

      pendingFitAfterLoad.value = true
      if (paneReady.value) {
        pendingFitAfterLoad.value = false
        await forceFitView()
      }
    } else {
      nodes.value = []
      storedEdges.value = []
      edges.value = []
    }
  } catch (err: any) {
    nodes.value = []
    storedEdges.value = []
    edges.value = []
    const message = err?.response?.data?.msg || err?.response?.data?.message || err?.message || '加载流程失败'
    ElMessage.error(message)
  }
}

// 节点点击事件
function onNodeClick(event: any) {
  selectedNode.value = event?.node || null
  selectedEdge.value = null
}

function onEdgeClick(payload: any) {
  payload?.event?.stopPropagation?.()
  const edge = payload?.edge
  if (!edge) return
  selectedEdge.value = edge
  selectedNode.value = null
  pushConnectLog(`选中连线: ${edge.source} -> ${edge.target}`)
}

function clearSelection() {
  selectedNode.value = null
  selectedEdge.value = null
}

function onConnectStart(payload: any) {
  const nodeId = payload?.nodeId || '-'
  const handleId = payload?.handleId || null
  pendingConnectStart.value = nodeId === '-' ? null : { nodeId, handleId }
  pushConnectLog(`开始连线: source=${nodeId} handle=${handleId || '-'}`)
}

function onConnectEnd(payload: any) {
  const rawEvent = payload?.event || payload
  const targetEl = rawEvent?.target as HTMLElement | null
  const targetClass = targetEl?.className || 'unknown'
  const targetNodeId =
    targetEl?.getAttribute?.('data-nodeid') ||
    targetEl?.getAttribute?.('data-node-id') ||
    targetEl?.getAttribute?.('data-id') ||
    ''
  const targetHandleId = targetEl?.getAttribute?.('data-handleid') || null

  pushConnectLog(
    `结束连线: target=${String(targetClass)} node=${targetNodeId || '-'} handle=${targetHandleId || '-'} | 当前边数=${storedEdges.value.length}`
  )

  const sourceNodeId = pendingConnectStart.value?.nodeId
  const sourceHandleId = pendingConnectStart.value?.handleId || null
  const connectRecentlyFired = Date.now() - lastConnectAt.value < 180

  if (!connectRecentlyFired && sourceNodeId && targetNodeId && sourceNodeId !== targetNodeId) {
    pushConnectLog(`触发兜底补边: ${sourceNodeId} -> ${targetNodeId}`)
    onFlowConnect({
      source: sourceNodeId,
      target: targetNodeId,
      sourceHandle: sourceHandleId,
      targetHandle: targetHandleId
    })
  }

  pendingConnectStart.value = null
}

// 节点配置变更
function onNodeConfigChange() {
  if (selectedNode.value?.data?.type === 'llm') {
    const cfg = selectedNode.value?.data?.config || {}
    const mode = normalizeLlmMode(cfg)
    selectedNode.value.data.config.mode = mode
    if (mode === 'chat') {
      selectedNode.value.data.config.userPromptTemplate = '{{sys.query}}'
      selectedNode.value.data.config.inputs = []
    } else {
      if (!Array.isArray(selectedNode.value.data.config.inputs) || selectedNode.value.data.config.inputs.length === 0) {
        selectedNode.value.data.config.inputs = [
          {
            name: 'query',
            source: 'previous'
          }
        ]
      }
      const tpl = String(selectedNode.value.data.config.userPromptTemplate || '').trim()
      if (!tpl || tpl === '{{sys.query}}') {
        selectedNode.value.data.config.userPromptTemplate = '{{query}}'
      }
    }
  }
  if (selectedNode.value) {
    ElMessage.info('配置已修改，保存时生效')
  }
}

// 删除选中的节点
function onDeleteSelectedNode() {
  if (selectedNode.value) {
    deleteNode(selectedNode.value.id)
    selectedNode.value = null
    ElMessage.success('节点已删除')
  }
}

function onDeleteSelectedEdge() {
  if (!selectedEdge.value) return
  const edgeId = selectedEdge.value.id
  storedEdges.value = storedEdges.value.filter((e) => e.id !== edgeId)
  syncVisibleEdges()
  pushConnectLog(`删除连线: ${selectedEdge.value.source} -> ${selectedEdge.value.target}`)
  selectedEdge.value = null
  ElMessage.success('连线已删除')
}

function addInput() {
  if (selectedNode.value) {
    if (selectedNode.value?.data?.type === 'llm' && normalizeLlmMode(selectedNode.value?.data?.config) === 'chat') {
      ElMessage.warning('CHAT 模式不支持手动入参映射')
      return
    }
    if (!selectedNode.value.data.config.inputs) {
      selectedNode.value.data.config.inputs = []
    }
    selectedNode.value.data.config.inputs.push({
      name: `param_${selectedNode.value.data.config.inputs.length + 1}`,
      source: 'workflow'
    })
    onNodeConfigChange()
  }
}

function removeInput(idx: number) {
  if (selectedNode.value && selectedNode.value.data.config.inputs) {
    selectedNode.value.data.config.inputs.splice(idx, 1)
    onNodeConfigChange()
  }
}

// 节点拖拽开始
function onNodeDragStart(event: DragEvent, nodeType: string) {
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('nodeType', nodeType)
  }
}

// 画布拖拽悬停
function onCanvasDragover(event: DragEvent) {
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
    event.preventDefault()
  }
}

// 画布放开（创建新节点）
function onCanvasDrop(event: DragEvent) {
  if (!event.dataTransfer) return

  const nodeType = event.dataTransfer.getData('nodeType')
  if (!nodeType) return

  event.preventDefault()

  let position
  if (forceFallbackCanvas.value && canvasHostRef.value) {
    const rect = canvasHostRef.value.getBoundingClientRect()
    position = {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    }
  } else {
    // 使用 Vue Flow 的坐标转换
    position = screenToFlowCoordinate({
      x: event.clientX,
      y: event.clientY
    })
  }

  // 稍微偏移以居中
  if (position) {
    position.x -= 60
    position.y -= 25
  }

  // 创建新节点
  nodeCounter++
  const newNode = {
    id: `${nodeType}-${nodeCounter}`,
    // 统一用 default 进行稳定渲染；真实业务类型保存在 data.type
    type: 'default',
    label: getNodeDisplayLabel(nodeType),
    data: {
      label: getNodeDisplayLabel(nodeType),
      type: nodeType,
      config:
        nodeType === 'llm'
          ? {
              mode: 'chat',
              modelKey: defaultModelKey(),
              temperature: 0.7,
              maxTokens: 2000,
              userPromptTemplate: '{{sys.query}}',
              inputs: []
            }
          : {}
    },
    position
  }

  addNodes(newNode)
  ElMessage.success(`已添加 ${nodeType} 节点`)
}

function buildCurrentDefinition() {
  if (!detail.value) return

  const currentDefinition = (detail.value.draft?.definition || {}) as Record<string, unknown>
  return {
    ...currentDefinition,
    appType: detail.value.appType || currentDefinition.appType || 'workflow',
    debugInputs: { ...debugInputValues.value },
    nodes: nodes.value.map((n) => ({
      id: n.id,
      type: n.data?.type,
      position: n.position,
      config: n.data?.config || {}
    })),
    edges: storedEdges.value.map((e) => ({
      id: e.id,
      sourceNodeId: e.source,
      targetNodeId: e.target
    }))
  }
}

async function persistCurrentDraft(showSuccess = true) {
  if (!detail.value) return null

  ensureAllLlmNodesModelKey()

  const llmWithoutModel = (nodes.value || []).filter(
    (n: any) => n?.data?.type === 'llm' && !String(n?.data?.config?.modelKey || '').trim()
  )
  if (llmWithoutModel.length > 0) {
    ElMessage.error(`存在未选择模型的 LLM 节点：${llmWithoutModel.map((n: any) => n.id).join('、')}`)
    return null
  }

  const definition = buildCurrentDefinition()
  if (!definition) return null

  try {
    // 后端 saveDraft 期望的是“下一次可编辑版本号”，不是最新已保存草稿版本号
    const editableVersion = detail.value.draftVersion || 1
    const result = await saveWorkflowDraft(id, { version: editableVersion, definition })

    // 前端本地同步最新状态，避免下一次保存继续拿旧版本导致冲突
    detail.value.draftVersion = result.draftVersion
    detail.value.draft = {
      workflowId: detail.value.workflowId,
      version: result.draftVersion - 1,
      definition
    }

    if (showSuccess) {
      ElMessage.success('保存成功')
    }
    return definition
  } catch (err: any) {
    const message = err?.response?.data?.msg || err?.response?.data?.message || err?.message || '保存失败'
    ElMessage.error(message)
    return null
  }
}

// 保存草稿
async function onSaveDraft() {
  await persistCurrentDraft(true)
}

// 发布工作流
async function onPublish() {
  try {
    const saved = await persistCurrentDraft(false)
    if (!saved) return
    await ElMessageBox.confirm('发布后，此版本将可被会话调用。确认发布？', '发布确认', { type: 'warning' })
    const result = await publishWorkflow(id)
    ElMessage.success(`发布成功，版本 v${result.publishedVersion}`)
    await load()
  } catch (err: any) {
    if (err === 'cancel') return
    const message = err?.response?.data?.msg || err?.response?.data?.message || err?.message || '发布失败'
    ElMessage.error(message)
  }
}

// 校验工作流
async function onValidate() {
  try {
    const saved = await persistCurrentDraft(false)
    if (!saved) return

    const result = await validateWorkflow(id)
    if (result.valid) {
      ElMessage.success('流程校验通过')
    } else {
      ElMessage.warning(result.issues?.join('；') || '流程校验失败')
    }
  } catch (err: any) {
    const message = err?.response?.data?.msg || err?.response?.data?.message || err?.message || '校验出错'
    ElMessage.error(message)
  }
}

// 调试运行
async function onDebug() {
  try {
    debugPanelVisible.value = true
    stopDebugPolling()
    debugLogs.value = []
    debugNodeTraceMap.value = {}
    debugNodeEventKeys.clear()
    runDetailText.value = ''
    debugRunStatus.value = 'starting'
    appendDebugLog('正在启动调试运行...', 'info')

    const defaultVersion = detail.value?.draftVersion || 1
    
    // 收集调试入参
    const inputs: Record<string, string> = {}
    for (const key of workflowInputs.value) {
      if (debugInputValues.value[key]) {
        inputs[key] = debugInputValues.value[key]
      }
    }

    const result = await startDebugRun(id, { version: defaultVersion, inputs })
    runId.value = result.runId
    debugRunStatus.value = result.status || ''
    runDetailText.value = JSON.stringify(result, null, 2)
    appendDebugLog(`调试启动：${runId.value}`, 'info')
    startDebugPolling()
    ElMessage.success('调试启动成功')
  } catch (err: any) {
    const message = err?.response?.data?.msg || err?.response?.data?.message || err?.message || '调试启动失败'
    ElMessage.error(message)
  }
}

// 更新元数据
async function onUpdateMeta() {
  if (!detail.value) return

  try {
    await updateWorkflow(id, { name: name.value })
    ElMessage.success('名称更新成功')
  } catch (err) {
    ElMessage.error('名称更新失败')
  }
}

function showJsonViewer() {
  const definition = buildCurrentDefinition()
  jsonViewerContent.value = definition ? JSON.stringify(definition, null, 2) : '{}'
  jsonViewerVisible.value = true
}

function copyJsonToClipboard() {
  navigator.clipboard.writeText(jsonViewerContent.value)
  ElMessage.success('已复制到剪贴板')
}

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  const tagName = target.tagName
  if (target.isContentEditable || tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'SELECT') {
    return true
  }
  return !!target.closest('.el-input, .el-textarea, .el-select, [contenteditable="true"]')
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key !== 'Delete') return
  if (e.ctrlKey || e.metaKey || e.altKey) return
  if (isEditableTarget(e.target)) return

  if (selectedNode.value) {
    e.preventDefault()
    onDeleteSelectedNode()
  } else if (selectedEdge.value) {
    e.preventDefault()
    onDeleteSelectedEdge()
  }
}

onMounted(() => {
  window.addEventListener('pointermove', onFallbackPointerMove)
  window.addEventListener('pointerup', onFallbackPointerUp)
  window.addEventListener('keydown', onGlobalKeydown)
  loadEnabledLlmModels()
  load()
})

onUnmounted(() => {
  stopDebugPolling()
  window.removeEventListener('pointermove', onFallbackPointerMove)
  window.removeEventListener('pointerup', onFallbackPointerUp)
  window.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<style scoped>
.workflow-editor-container {
  display: flex;
  flex-direction: column;
  height: 100%; /* 不写死 100vh 避免撑出框架导致外层滚动 */
  min-height: 500px;
  background: #fff;
  overflow: hidden; /* 去除整个容器的滚动条 */
}

/* 右侧参数面板启用可见滚动条 */
.panel-scrollable {
  scrollbar-width: thin;
  scrollbar-color: #c1c1c1 transparent;
}

.panel-scrollable::-webkit-scrollbar {
  display: block;
  width: 8px;
  height: 8px;
}

.panel-scrollable::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.panel-scrollable::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.editor-toolbar {
  padding: 12px 16px;
  border-bottom: 1px solid #e0e0e0;
  background: #fafafa;
  flex-shrink: 0;
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
  overflow-y: hidden; /* 改为 hidden 去除滚动条 */
  background: #fafafa;
}

.panel-center {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  position: relative;
  display: flex;
}

.panel-right {
  border-left: 1px solid #e0e0e0;
  overflow-y: hidden; /* 改为 hidden 去除滚动条 */
  background: #fff;
  display: flex;
  flex-direction: column;
}

.panel-right :deep(.el-tabs) {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.panel-right :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.panel-right :deep(.el-tab-pane) {
  height: 100%;
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

.node-item:active {
  cursor: grabbing;
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
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-desc {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.editor-canvas {
  position: relative;
  min-height: 0;
  width: 100%;
  height: 100%;
}

.fallback-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  background: linear-gradient(0deg, rgba(148, 163, 184, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.08) 1px, transparent 1px);
  background-size: 16px 16px;
  overflow: hidden;
}

.fallback-edges {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.fallback-edge-path {
  fill: none;
  stroke: #2563eb;
  stroke-width: 2.5;
  pointer-events: stroke;
  cursor: pointer;
  transition: stroke 0.2s, filter 0.2s;
}

.fallback-edge-path:hover {
  filter: brightness(1.2);
  stroke-width: 3.5;
}

.fallback-edge-path.edge-selected {
  stroke: #f97316;
  stroke-width: 4;
  filter: brightness(1.3);
}

.fallback-node {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
  color: #1e293b;
  font-size: 14px;
  font-weight: 500;
  cursor: move;
  user-select: none;
}

.node-connect-handle {
  position: absolute;
  right: -6px;
  top: 50%;
  transform: translateY(-50%);
  width: 12px;
  height: 12px;
  background: #2563eb;
  border: 2px solid #fff;
  border-radius: 50%;
  cursor: crosshair;
  opacity: 0;
  transition: opacity 0.2s;
}

.fallback-node:hover .node-connect-handle {
  opacity: 1;
}

.node-connect-handle:hover {
  background: #1d4ed8;
  box-shadow: 0 0 8px rgba(37, 99, 235, 0.5);
}

.fallback-node.selected {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.18);
}

.debug-panel-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #666;
}

.debug-log-scroll {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  height: calc(100vh - 180px);
  overflow-y: auto;
  padding: 10px;
  background: #fafafa;
}

.debug-log-empty {
  color: #999;
  font-size: 12px;
}

.debug-result-collapse {
  margin-bottom: 12px;
}

.debug-result-pre {
  padding: 10px;
  margin: 0;
  max-height: 180px;
  overflow-y: auto;
  font-size: 11px;
  line-height: 1.5;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-all;
}

.debug-log-item {
  padding: 8px 10px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #ebeef5;
  margin-bottom: 8px;
}

.debug-log-clickable {
  cursor: pointer;
}

.debug-log-clickable:hover {
  background: #f5faff;
  border-color: #d9ecff;
}

.debug-log-time {
  color: #909399;
  font-size: 11px;
  margin-bottom: 2px;
}

.debug-log-text {
  font-size: 12px;
  color: #303133;
  word-break: break-all;
}

.debug-log-hint {
  font-size: 11px;
  color: #409eff;
  margin-top: 4px;
}

.debug-log-success {
  border-left: 3px solid #67c23a;
}

.debug-log-error {
  border-left: 3px solid #f56c6c;
}

.debug-log-info {
  border-left: 3px solid #409eff;
}

/* Vue Flow 节点样式优化 */
.custom-node {
  background: white;
  border: 1px solid #ccc;
  border-radius: 6px;
  min-width: 120px;
  padding: 10px 15px;
  font-size: 14px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  display: flex;
  align-items: center;
  justify-content: center;
}

.custom-node.selected {
  border-color: #0066cc;
  box-shadow: 0 0 0 2px rgba(0, 102, 204, 0.2);
}

.node-start {
  border-left: 4px solid #10b981;
}

.node-llm {
  border-left: 4px solid #6366f1;
}

.node-end {
  border-left: 4px solid #ef4444;
}

.node-label {
  font-weight: 500;
  color: #333;
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
}

:deep(.vue-flow__handle) {
  width: 12px;
  height: 12px;
  border: 2px solid #fff;
  background: #2563eb;
  cursor: crosshair;
  z-index: 20;
  pointer-events: all;
}

.connect-log-box {
  max-height: 120px;
  overflow: auto;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 6px;
  background: #fafafa;
}

.connect-log-line {
  font-size: 11px;
  color: #4b5563;
  line-height: 1.5;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}
</style>
