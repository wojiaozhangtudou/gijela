<template>
  <div class="node-palette">
    <el-card shadow="never" :body-style="{ padding: '8px' }">
      <template #header><span style="font-size: 12px; font-weight: bold">节点面板</span></template>
      <div class="palette-items">
        <div
          v-for="nodeType in nodeTypes"
          :key="nodeType.id"
          class="palette-item"
          draggable="true"
          @dragstart="onDragStart($event, nodeType)"
        >
          <el-icon style="margin-right: 4px">{{ nodeType.icon }}</el-icon>
          <span>{{ nodeType.label }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { CircleCheck, Cpu, Flag } from '@element-plus/icons-vue'

interface NodeType {
  id: string
  label: string
  icon: any
}

const nodeTypes: NodeType[] = [
  { id: 'start', label: '开始', icon: Flag },
  { id: 'llm', label: '大模型', icon: Cpu },
  { id: 'end', label: '结束', icon: CircleCheck }
]

function onDragStart(event: DragEvent, nodeType: NodeType) {
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('nodeType', nodeType.id)
  }
}
</script>

<style scoped>
.node-palette {
  height: 100%;
  overflow-y: auto;
}

.palette-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.palette-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: #f5f5f5;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: move;
  user-select: none;
  font-size: 12px;
  transition: all 0.2s;
}

.palette-item:hover {
  background: #e8f4f8;
  border-color: #409eff;
}
</style>
