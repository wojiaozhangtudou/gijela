<template>
  <div class="node-config-panel">
    <el-card v-if="selectedNode" shadow="never">
      <template #header>
        <span style="font-size: 12px; font-weight: bold">节点配置</span>
      </template>
      <el-form :model="nodeConfig" label-width="80px" size="small">
        <el-form-item label="节点 ID">
          <el-input v-model="nodeConfig.id" disabled />
        </el-form-item>
        <el-form-item label="节点类型">
          <el-input v-model="nodeConfig.type" disabled />
        </el-form-item>
        <template v-if="nodeConfig.type === 'llm'">
          <el-form-item label="模型">
            <el-input v-model="nodeConfig.config.model" placeholder="qwen-plus" />
          </el-form-item>
          <el-form-item label="温度">
            <el-input-number v-model="nodeConfig.config.temperature" :min="0" :max="2" :step="0.1" />
          </el-form-item>
          <el-form-item label="Max Tokens">
            <el-input-number v-model="nodeConfig.config.maxTokens" :min="1" :step="100" />
          </el-form-item>
          <el-form-item label="System Prompt">
            <el-input
              v-model="nodeConfig.config.systemPrompt"
              type="textarea"
              :rows="3"
              placeholder="你是企业流程编排助手"
            />
          </el-form-item>
        </template>
      </el-form>
      <el-space style="margin-top: 12px">
        <el-button type="primary" size="small" @click="onSaveConfig">保存配置</el-button>
        <el-button @click="onDeleteNode" type="danger" size="small">删除节点</el-button>
      </el-space>
    </el-card>
    <el-empty v-else description="选择节点查看配置" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface NodeConfig {
  id: string
  type: string
  config: Record<string, any>
}

const props = defineProps<{
  selectedNode: any
}>()

const emit = defineEmits<{
  updateNode: [node: any]
  deleteNode: [nodeId: string]
}>()

const nodeConfig = ref<NodeConfig>({
  id: '',
  type: '',
  config: {}
})

watch(
  () => props.selectedNode,
  (node) => {
    if (node) {
      nodeConfig.value = {
        id: node.id,
        type: node.data?.type || 'unknown',
        config: node.data?.config || {}
      }
    }
  },
  { immediate: true, deep: true }
)

function onSaveConfig() {
  emit('updateNode', {
    id: nodeConfig.value.id,
    data: {
      ...props.selectedNode.data,
      config: nodeConfig.value.config
    }
  })
}

function onDeleteNode() {
  emit('deleteNode', nodeConfig.value.id)
}
</script>

<style scoped>
.node-config-panel {
  height: 100%;
  overflow-y: auto;
  padding: 8px;
}
</style>
