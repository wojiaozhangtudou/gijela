<template>
  <div class="prompt-page">
    <div class="page-header">
      <div>
        <h1>提示词管理</h1>
        <p>统一管理系统提示词与会话提示词（均支持多条）。</p>
      </div>
      <el-space>
        <el-button type="primary" plain @click="goChatPage">返回对话页</el-button>
      </el-space>
    </div>

    <el-tabs v-model="activeTab" class="prompt-tabs">
      <el-tab-pane label="系统提示词" name="system">
        <div class="toolbar">
          <el-input v-model="systemScope.appCode" placeholder="appCode" style="width: 180px" />
          <el-input v-model="systemScope.modelRoute" placeholder="modelRoute" style="width: 180px" />
          <el-button @click="loadSystemItems">刷新</el-button>
          <el-button type="primary" @click="openSystemCreate">新增系统提示词</el-button>
        </div>

        <el-table :data="systemItems" border>
          <el-table-column prop="promptName" label="名称" min-width="140" />
          <el-table-column prop="priority" label="优先级" width="90" />
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
          <el-table-column label="内容" min-width="260">
            <template #default="{ row }">{{ preview(row.content) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openSystemEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="removeSystemItem(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="会话提示词" name="session">
        <div class="toolbar">
          <el-select v-model="selectedSessionId" filterable clearable placeholder="请选择会话" style="width: 420px">
            <el-option v-for="item in sessions" :key="item.sessionId" :label="`${item.title || item.sessionId} (${item.sessionId})`" :value="item.sessionId" />
          </el-select>
          <el-button @click="reloadSessions">刷新会话</el-button>
          <el-button :disabled="!selectedSessionId" @click="loadSessionItems">刷新提示词</el-button>
          <el-button type="primary" :disabled="!selectedSessionId" @click="openSessionCreate">新增会话提示词</el-button>
        </div>

        <el-table :data="sessionItems" border>
          <el-table-column prop="promptName" label="名称" min-width="140" />
          <el-table-column prop="priority" label="优先级" width="90" />
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
          <el-table-column label="内容" min-width="260">
            <template #default="{ row }">{{ preview(row.content) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openSessionEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="removeSessionItem(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showSystemDialog" :title="systemDialogMode === 'create' ? '新增系统提示词' : '编辑系统提示词'" width="680px">
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="systemForm.promptName" :disabled="systemDialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="systemForm.priority" :min="0" :max="10000" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="systemForm.enabled" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="systemForm.content" type="textarea" :rows="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSystemDialog = false">取消</el-button>
        <el-button type="primary" @click="submitSystem">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showSessionDialog" :title="sessionDialogMode === 'create' ? '新增会话提示词' : '编辑会话提示词'" width="680px">
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="sessionForm.promptName" :disabled="sessionDialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="sessionForm.priority" :min="0" :max="10000" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="sessionForm.enabled" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="sessionForm.content" type="textarea" :rows="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSessionDialog = false">取消</el-button>
        <el-button type="primary" @click="submitSession">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import type { ChatSessionItem } from '@/types/chat'
import { fetchSessions } from '@/api/chat'
import {
  createSessionPromptItem,
  createSystemPromptItem,
  deleteSessionPromptItem,
  deleteSystemPromptItem,
  listSessionPromptItems,
  listSystemPromptItems,
  updateSessionPromptItem,
  updateSystemPromptItem,
  type PromptItemResponse
} from '@/api/prompt'

const router = useRouter()
const activeTab = ref<'system' | 'session'>('system')
const sessions = ref<ChatSessionItem[]>([])
const selectedSessionId = ref('')

const systemScope = ref({ appCode: 'chat', modelRoute: 'default' })
const systemItems = ref<PromptItemResponse[]>([])
const sessionItems = ref<PromptItemResponse[]>([])

const showSystemDialog = ref(false)
const showSessionDialog = ref(false)
const systemDialogMode = ref<'create' | 'edit'>('create')
const sessionDialogMode = ref<'create' | 'edit'>('create')
const editingSystemId = ref<number | null>(null)
const editingSessionId = ref<number | null>(null)

const systemForm = ref({ promptName: '', content: '', priority: 100, enabled: true })
const sessionForm = ref({ promptName: '', content: '', priority: 100, enabled: true })

onMounted(async () => {
  await Promise.all([reloadSessions(), loadSystemItems()])
})

watch(selectedSessionId, async (id) => {
  if (!id) {
    sessionItems.value = []
    return
  }
  await loadSessionItems()
})

function goChatPage() {
  void router.push('/')
}

function preview(text: string) {
  if (!text) {
    return '-'
  }
  const clean = text.replace(/\s+/g, ' ').trim()
  return clean.length > 80 ? `${clean.slice(0, 80)}...` : clean
}

async function reloadSessions() {
  sessions.value = await fetchSessions(200)
}

async function loadSystemItems() {
  try {
    systemItems.value = await listSystemPromptItems(systemScope.value.appCode, systemScope.value.modelRoute)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载系统提示词失败')
  }
}

async function loadSessionItems() {
  if (!selectedSessionId.value) {
    return
  }
  try {
    sessionItems.value = await listSessionPromptItems(selectedSessionId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载会话提示词失败')
  }
}

function openSystemCreate() {
  systemDialogMode.value = 'create'
  editingSystemId.value = null
  systemForm.value = { promptName: '', content: '', priority: 100, enabled: true }
  showSystemDialog.value = true
}

function openSystemEdit(item: PromptItemResponse) {
  systemDialogMode.value = 'edit'
  editingSystemId.value = item.id
  systemForm.value = {
    promptName: item.promptName,
    content: item.content,
    priority: item.priority,
    enabled: item.enabled
  }
  showSystemDialog.value = true
}

async function submitSystem() {
  if (!systemForm.value.promptName.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  if (!systemForm.value.content.trim()) {
    ElMessage.warning('内容不能为空')
    return
  }
  try {
    if (systemDialogMode.value === 'create') {
      await createSystemPromptItem({
        appCode: systemScope.value.appCode,
        modelRoute: systemScope.value.modelRoute,
        promptName: systemForm.value.promptName.trim(),
        content: systemForm.value.content,
        priority: systemForm.value.priority,
        enabled: systemForm.value.enabled
      })
    } else if (editingSystemId.value) {
      await updateSystemPromptItem(editingSystemId.value, {
        content: systemForm.value.content,
        priority: systemForm.value.priority,
        enabled: systemForm.value.enabled
      })
    }
    showSystemDialog.value = false
    await loadSystemItems()
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存系统提示词失败')
  }
}

async function removeSystemItem(item: PromptItemResponse) {
  try {
    await ElMessageBox.confirm(`确认删除系统提示词「${item.promptName}」吗？`, '删除确认', { type: 'warning' })
    await deleteSystemPromptItem(item.id)
    await loadSystemItems()
    ElMessage.success('删除成功')
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除系统提示词失败')
  }
}

function openSessionCreate() {
  sessionDialogMode.value = 'create'
  editingSessionId.value = null
  sessionForm.value = { promptName: '', content: '', priority: 100, enabled: true }
  showSessionDialog.value = true
}

function openSessionEdit(item: PromptItemResponse) {
  sessionDialogMode.value = 'edit'
  editingSessionId.value = item.id
  sessionForm.value = {
    promptName: item.promptName,
    content: item.content,
    priority: item.priority,
    enabled: item.enabled
  }
  showSessionDialog.value = true
}

async function submitSession() {
  if (!selectedSessionId.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  if (!sessionForm.value.promptName.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  if (!sessionForm.value.content.trim()) {
    ElMessage.warning('内容不能为空')
    return
  }
  try {
    if (sessionDialogMode.value === 'create') {
      await createSessionPromptItem(selectedSessionId.value, {
        promptName: sessionForm.value.promptName.trim(),
        content: sessionForm.value.content,
        priority: sessionForm.value.priority,
        enabled: sessionForm.value.enabled
      })
    } else if (editingSessionId.value) {
      await updateSessionPromptItem(selectedSessionId.value, editingSessionId.value, {
        content: sessionForm.value.content,
        priority: sessionForm.value.priority,
        enabled: sessionForm.value.enabled
      })
    }
    showSessionDialog.value = false
    await loadSessionItems()
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存会话提示词失败')
  }
}

async function removeSessionItem(item: PromptItemResponse) {
  if (!selectedSessionId.value) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除会话提示词「${item.promptName}」吗？`, '删除确认', { type: 'warning' })
    await deleteSessionPromptItem(selectedSessionId.value, item.id)
    await loadSessionItems()
    ElMessage.success('删除成功')
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除会话提示词失败')
  }
}
</script>

<style scoped>
.prompt-page {
  height: 100dvh;
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  overflow: auto;
  background: linear-gradient(180deg, #f5f7fa 0%, #eef3ff 100%);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.page-header h1 {
  margin: 0 0 8px;
  font-size: 26px;
}

.page-header p {
  margin: 0;
  color: #606266;
}

.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.prompt-tabs :deep(.el-tabs__content) {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
}
</style>
