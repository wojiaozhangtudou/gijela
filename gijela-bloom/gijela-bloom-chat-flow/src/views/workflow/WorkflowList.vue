<template>
  <div class="workflow-dashboard">
    <!-- 头部区域 -->
    <div class="dashboard-header">
      <div>
        <h2 class="dashboard-title">工作流空间</h2>
        <p class="dashboard-subtitle">管理和构建您的自动化流程与 AI 应用</p>
      </div>
      <div class="header-actions">
        <el-button plain @click="goLlmModels" size="large">
          <el-icon class="el-icon--left"><Monitor /></el-icon>
          模型维护
        </el-button>
        <el-button type="primary" @click="onCreate" size="large">
          <el-icon class="el-icon--left"><Plus /></el-icon>
          新建流程
        </el-button>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- 空状态 -->
    <el-empty 
      v-else-if="rows.length === 0" 
      description="暂无工作流，请点击新建开始吧！"
    >
      <el-button type="primary" @click="onCreate">新建流程</el-button>
    </el-empty>

    <!-- 卡片网格 -->
    <el-row v-else :gutter="24" class="card-grid">
      <el-col 
        v-for="workflow in rows" 
        :key="workflow.workflowId" 
        :xs="24" :sm="12" :md="8" :lg="6"
        style="margin-bottom: 24px;"
      >
        <el-card class="workflow-card" shadow="hover">
          <div class="card-header">
            <div class="title-section">
              <h3 class="workflow-name" :title="workflow.name">{{ workflow.name }}</h3>
              <el-tag 
                size="small" 
                :type="workflow.status === 'active' ? 'success' : 'info'"
                class="status-tag"
              >
                {{ workflow.status || '草稿' }}
              </el-tag>
            </div>
            
            <el-tag size="small" effect="plain" type="primary" class="app-type-tag">
              {{ workflow.appType === 'workflow' ? '工作流' : workflow.appType === 'chat' ? '聊天应用' : workflow.appType }}
            </el-tag>
          </div>

          <div class="card-body">
            <p class="description">
              {{ workflow.description || '暂无描述信息' }}
            </p>
            <div class="meta-info">
              <span class="meta-label">ID:</span>
              <span class="meta-value">{{ workflow.workflowId }}</span>
            </div>
          </div>

          <div class="card-footer">
            <el-button text bg type="primary" @click="goEditor(workflow.workflowId)">
              <el-icon><Edit /></el-icon> 编辑
            </el-button>
            <el-button text bg @click="goRuns(workflow.workflowId)">
              <el-icon><DataLine /></el-icon> 运行历史
            </el-button>
            <el-button text bg type="danger" @click="onDelete(workflow)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Plus, Monitor, Edit, DataLine, Delete } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createWorkflow, deleteWorkflow, listWorkflows } from '../../api/workflow'
import type { WorkflowSummary } from '../../types/workflow'

const router = useRouter()
const loading = ref(false)
const rows = ref<WorkflowSummary[]>([])

async function load() {
  loading.value = true
  try {
    rows.value = await listWorkflows()
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  const ts = Date.now()
  const created = await createWorkflow({
    code: `wf_chat_${ts}`,
    name: `流程_${ts}`,
    appType: 'workflow',
    description: '自动创建'
  })
  ElMessage.success('创建成功')
  goEditor(created.workflowId)
}

function goEditor(id: string) {
  router.push(`/workflows/${id}/editor`)
}

function goRuns(id: string) {
  router.push(`/workflows/${id}/runs`)
}

function goLlmModels() {
  router.push('/llm-models')
}

async function onDelete(workflow: WorkflowSummary) {
  await ElMessageBox.confirm(`确认删除流程「${workflow.name}」？此操作不可恢复。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger'
  })
  await deleteWorkflow(workflow.workflowId)
  ElMessage.success('删除成功')
  await load()
}

onMounted(load)
</script>

<style scoped>
.workflow-dashboard {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.dashboard-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.dashboard-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.loading-state {
  padding: 40px;
  background: white;
  border-radius: 8px;
}

.workflow-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.workflow-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px;
}

.card-header {
  margin-bottom: 16px;
}

.title-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.workflow-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 70%;
}

.app-type-tag {
  border-radius: 4px;
}

.card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.description {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.meta-info {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
  padding: 6px 10px;
  border-radius: 6px;
  margin-bottom: 20px;
}

.meta-label {
  margin-right: 4px;
}

.meta-value {
  font-family: monospace;
}

.card-footer {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.card-footer .el-button {
  flex: 1;
  margin: 0;
}
</style>
