<template>
  <div class="flow-node" :class="[`flow-node-${data?.bizType || 'default'}`, { 'is-selected': !!selected }]">
    <Handle id="top" type="source" :position="Position.Top" class="flow-handle" :connectable-start="true" :connectable-end="true" />
    <Handle id="left" type="source" :position="Position.Left" class="flow-handle" :connectable-start="true" :connectable-end="true" />
    <Handle id="right" type="source" :position="Position.Right" class="flow-handle" :connectable-start="true" :connectable-end="true" />
    <Handle id="bottom" type="source" :position="Position.Bottom" class="flow-handle" :connectable-start="true" :connectable-end="true" />

    <div class="node-label">{{ data?.label || id }}</div>
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{
  id: string
  data?: Record<string, any>
  selected?: boolean
}>()
</script>

<style scoped>
.flow-node {
  position: relative;
  min-width: 120px;
  padding: 10px 15px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 6px;
  border: 1px solid #ccc;
  background: #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #333;
}

.flow-node.is-selected {
  border-color: #0066cc;
  box-shadow: 0 0 0 2px rgba(0, 102, 204, 0.2);
}

.flow-node-start {
  border-left: 4px solid #10b981;
}

.flow-node-llm {
  border-left: 4px solid #6366f1;
}

.flow-node-end {
  border-left: 4px solid #ef4444;
}

.node-label {
  pointer-events: none;
  user-select: none;
}

.flow-handle {
  width: 10px;
  height: 10px;
  border: 1px solid #fff;
  background: #2563eb;
  opacity: 0;
  transition: opacity 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.flow-node:hover .flow-handle {
  opacity: 1;
}

:global(.is-connecting) .flow-node .flow-handle {
  opacity: 1;
}

.flow-handle:hover {
  opacity: 1;
  box-shadow: 0 0 8px rgba(37, 99, 235, 0.45);
  transform: scale(1.05);
}

:deep(.vue-flow__handle-top) {
  top: -6px;
}

:deep(.vue-flow__handle-bottom) {
  bottom: -6px;
}

:deep(.vue-flow__handle-left) {
  left: -6px;
}

:deep(.vue-flow__handle-right) {
  right: -6px;
}
</style>
