<template>
  <div class="workflow-editor-container">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <el-space wrap>
        <div>
          <strong v-if="detail">{{ detail.name }}</strong>
        </div>
        <div style="color: #999; font-size: 12px" v-if="detail">
          流程ID: {{ detail.workflowId }} | 版本: {{ detail.draft?.version }}
        </div>
      </el-space>

      <el-space wrap style="margin-top: 8px">
        <el-button size="small" @click="onSaveDraft">💾 保存草稿</el-button>
        <el-button size="small" type="primary" @click="onDebug">▶️ 调试运行</el-button>
        <el-button size="small" type="success" @click="onValidate">✓ 校验</el-button>
        <el-input v-model="name" size="small" placeholder="流程名称" style="width: 150px" />
        <el-button size="small" type="info" @click="onUpdateMeta">更新</el-button>
      </el-space>
    </div>

    <!-- 三分布局：左节点面板 + 中画布 + 右配置面板 -->
    <div class="editor-layout">
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
            </div>
          </div>
          :fitViewOptions="{ padding: 0.1 }"
        >
          <Background :patternSize="16" />
          <Controls />
        </VueFlow>
      </div>

      <!-- 右：配置面板 -->
      <div class="editor-right">
        <div style="padding: 12px; font-weight: bold; border-bottom: 1px solid #eee">
          节点配置
        </div>
        <div style="padding: 12px">
          <div v-if="selectedNode" style="font-size: 12px">
            <div style="margin-bottom: 8px">
              <strong>节点 ID:</strong> {{ selectedNode.id }}
            </div>
            <div style="margin-bottom: 8px">
              <strong>节点类型:</strong> {{ selectedNode.data?.type }}
            </div>
            <div v-if="selectedNode.data?.type === 'llm'" style="margin-bottom: 8px">
              <label style="display: block; margin-bottom: 4px">模型:</label>
              <el-input
                v-model="selectedNode.data.config.model"
                size="small"
                @change="onNodeConfigChange"
              />
            </div>
            <div v-if="selectedNode.data?.type === 'llm'" style="margin-bottom: 8px">
              <label style="display: block; margin-bottom: 4px">温度:</label>
              <el-input
                v-model.number="selectedNode.data.config.temperature"
                size="small"
                type="number"
                @change="onNodeConfigChange"
              />
            </div>
            <el-button size="small" type="danger" @click="onDeleteSelectedNode">
              🗑️ 删除节点
            </el-button>
          </div>
          <div v-else style="color: #999; font-size: 12px">
            点击画布中的节点进行配置
          </div>
        </div>
      </div>
    </div>

    <!-- 运行详情（底部） -->
    <div v-if="runDetailText" class="editor-result">
      <div style="padding: 12px; font-weight: bold; border-bottom: 1px solid #eee">
        最近运行结果 ({{ runId }})
      </div>
      <pre style="padding: 12px; margin: 0; overflow-y: auto; font-size: 11px">{{ runDetailText }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import NodePalette from '../../components/workflow/NodePalette.vue'
import {
  getWorkflowDetail,
  getRunDetail,
  saveWorkflowDraft,
  startDebugRun,
  updateWorkflow,
  validateWorkflow
} from '../../api/workflow'
import type { WorkflowDetail } from '../../types/workflow'

const route = useRoute()
const id = String(route.params.id)

const { nodes, edges, addNodes } = useVueFlow()

const detail = ref<WorkflowDetail | null>(null)
const runId = ref('')
const runDetailText = ref('')
const name = ref('')
const selectedNode = ref<any>(null)
let nodeCounter = 0

function deleteNode(nodeId: string) {
  nodes.value = nodes.value.filter((n) => n.id !== nodeId)
  edges.value = edges.value.filter(
    (e) => e.source !== nodeId && e.target !== nodeId
  )
}

async function load() {
  detail.value = await getWorkflowDetail(id)
  name.value = detail.value?.name || ''

  const definition = detail.value?.draft?.definition
  if (definition && typeof definition === 'object' && 'nodes' in definition) {
    const defNodes = (definition as any).nodes || []
    const defEdges = (definition as any).edges || []

    nodes.value = defNodes.map((n: any) => ({
      id: n.id,
      data: { label: n.type, type: n.type, config: n.config || {} },
      position: n.position || { x: 0, y: 0 }
    }))

    edges.value = defEdges.map((e: any) => ({
      id: `${e.sourceNodeId}-${e.targetNodeId}`,
      source: e.sourceNodeId,
      target: e.targetNodeId
    }))
  }
}

function onNodeClick(event: any) {
  selectedNode.value = event.node
}

function onNodeConfigChange() {
  if (selectedNode.value) {
    // 配置已更新
    ElMessage.info('配置已修改，保存后生效')
  }
}

function onDeleteSelectedNode() {
  if (selectedNode.value) {
    deleteNode(selectedNode.value.id)
    selectedNode.value = null
    ElMessage.success('节点已删除')
  }
}

function onCanvasDragover(event: DragEvent) {
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
    event.preventDefault()
  }
}

function onCanvasDrop(event: DragEvent) {
  if (!event.dataTransfer) return

  const nodeType = event.dataTransfer.getData('nodeType')
  if (!nodeType) return

  // 获取画布相对位置（需要计算）
  const canvas = document.querySelector('.editor-canvas')
  if (!canvas) return

  const rect = canvas.getBoundingClientRect()
  const position = {
    x: event.clientX - rect.left - 50,
    y: event.clientY - rect.top - 25
  }

  const nodeId = `${nodeType}_${++nodeCounter}`
  addNodes({
    id: nodeId,
    data: { label: nodeType, type: nodeType, config: {} },
    position
  })

  ElMessage.success(`添加了 ${nodeType} 节点`)
}

async function onUpdateMeta() {
  if (!name.value) {
    ElMessage.warning('请输入流程名称')
    return
  }
  const ret = await updateWorkflow(id, { name: name.value })
  detail.value = ret
  ElMessage.success('基础信息已更新')
}

async function onSaveDraft() {
  if (nodes.value.length === 0) {
    ElMessage.warning('请先添加节点')
    return
  }

  const version = detail.value?.draft?.version || 1
  const definition = {
    nodes: nodes.value.map((n) => ({
      id: n.id,
      type: n.data.type,
      position: n.position,
      config: n.data.config || {}
    })),
    edges: edges.value.map((e) => ({
      sourceNodeId: e.source,
      targetNodeId: e.target
    }))
  }

  await saveWorkflowDraft(id, { version, definition })
  ElMessage.success('草稿已保存')
  await load()
}

async function onValidate() {
  const ret = await validateWorkflow(id)
  if (ret.valid) {
    ElMessage.success('✓ 流程定义有效')
  } else {
    ElMessage.error(`✗ 校验失败: ${(ret.issues || []).join('; ')}`)
  }
}

async function onDebug() {
  const version = detail.value?.draft?.version || 1
  const ret = await startDebugRun(id, { version, inputs: { question: '请总结本流程执行摘要' } })
  runId.value = ret.runId

  try {
    const runDetail = await getRunDetail(ret.runId)
    runDetailText.value = JSON.stringify(runDetail, null, 2)
    ElMessage.success('✓ 调试运行完成')
  } catch (e) {
    runDetailText.value = '运行进行中或加载失败，请稍后...'
  }
}

onMounted(load)
</script>

<style scoped>
.workflow-editor-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f7fa;
  padding: 12px;
  gap: 12px;
}

.editor-toolbar {
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.editor-layout {
  display: grid;
  grid-template-columns: 200px 1fr 240px;
  gap: 12px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.editor-left {
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.editor-left > :last-child {
  flex: 1;
  overflow-y: auto;
}

.editor-canvas {
  background: #fafafa;
  border: 1px solid #ddd;
  border-radius: 4px;
  overflow: hidden;
  position: relative;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

:deep(.vue-flow__viewport) {
  touch-action: none;
}

.editor-right {
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.editor-right > div:last-child {
  flex: 1;
  overflow-y: auto;
}

.editor-result {
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  max-height: 200px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.editor-result > :last-child {
  flex: 1;
  overflow-y: auto;
}
</style>
