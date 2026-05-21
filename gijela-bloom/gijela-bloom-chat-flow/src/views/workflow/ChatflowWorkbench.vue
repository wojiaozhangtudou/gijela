<template>
  <div class="workbench-page">
    <el-card class="session-panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <strong>会话列表</strong>
          <el-button type="primary" size="small" @click="openCreate">新建会话</el-button>
        </div>
      </template>

      <el-input v-model="keyword" placeholder="按标题/会话ID搜索" clearable @keyup.enter="loadSessions" />
      <el-button style="margin-top: 8px" size="small" @click="loadSessions">查询</el-button>

      <div class="session-list" v-loading="sessionLoading">
        <div
          v-for="item in sessions"
          :key="item.sessionId"
          class="session-item"
          :class="{ active: item.sessionId === activeSessionId }"
          @click="selectSession(item.sessionId)"
        >
          <div class="title">{{ item.title }}</div>
          <div class="meta">{{ item.workflowId }}</div>
          <div class="actions">
            <el-button text type="primary" size="small" @click.stop="openEdit(item)">编辑</el-button>
            <el-button text type="danger" size="small" @click.stop="removeSession(item.sessionId)">删除</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="chat-panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <strong>{{ activeSessionTitle || '消息窗口' }}</strong>
          <div class="header-right">
            <span v-if="activeSessionId" class="session-id">{{ activeSessionId }}</span>
          </div>
        </div>
      </template>

      <div class="message-list" v-loading="messageLoading">
        <el-empty v-if="!activeSessionId" description="请先选择会话" />
        <template v-else>
          <div v-for="msg in messages" :key="msg.messageId" class="msg-row" :class="msg.role">
            <div class="bubble">{{ msg.content }}</div>
          </div>
        </template>
      </div>

      <div class="composer">
        <el-input
          v-model="inputText"
          :disabled="!activeSessionId || sending"
          type="textarea"
          :rows="3"
          placeholder="输入消息后点击发送"
          @keyup.ctrl.enter="send"
        />
        <div class="composer-action">
          <el-button type="primary" :disabled="!activeSessionId || sending" :loading="sending" @click="send">
            发送
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card class="run-panel" shadow="never">
      <template #header>
        <div class="run-panel-header">
          <strong>执行面板</strong>
          <div class="run-panel-actions">
            <el-button size="small" text :disabled="!activeSessionId" @click="openRunHistoryDialog">运行历史</el-button>
            <el-button size="small" text :disabled="!latestRunId" :loading="runLoading" @click="refreshLatestRun">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="run-panel-scroll">
        <div class="run-section">
          <div class="detail-header">
            <strong>实时执行</strong>
          </div>

          <div v-loading="runLoading" class="run-live-panel">
            <el-empty v-if="!activeSessionId" description="请先选择会话" :image-size="60" />
            <el-empty v-else-if="!latestRunId || !runDetail" description="暂无实时执行" :image-size="60" />
            <template v-else>
              <div class="debug-panel-meta">
                <div><strong>状态：</strong>{{ runDetail.status || '-' }}</div>
                <div><strong>耗时：</strong>{{ runDetail.durationMs || 0 }}ms</div>
                <div><el-tag :type="runDetail.runType === 'debug' ? 'warning' : 'info'" size="small">{{ runDetail.runType === 'debug' ? '调试' : '正式' }}</el-tag></div>
              </div>

              <el-collapse class="debug-result-collapse" accordion>
                <el-collapse-item title="最终结果（finalResult）" name="live-final-result">
                  <pre class="debug-result-pre">{{ toPrettyJson(runDetail.finalResult || {}) }}</pre>
                </el-collapse-item>
              </el-collapse>

              <div class="debug-log-scroll live-log-scroll">
                <div v-if="(runDetail.nodes || []).length === 0" class="debug-log-empty">暂无节点日志</div>
                <div
                  v-for="node in runDetail.nodes"
                  :key="node.nodeId"
                  class="debug-log-item debug-log-clickable"
                  :class="`debug-log-${nodeLogLevel(node.status)}`"
                  @click="openNodeDetail(node.nodeId, 'current')"
                >
                  <div class="debug-log-time">{{ node.nodeType }} · {{ node.durationMs || 0 }}ms</div>
                  <div class="debug-log-text">{{ nodeLogMessage(node) }}</div>
                  <div class="debug-log-hint">点击查看节点输入/输出</div>
                  <div v-if="node.errorMessage" class="node-error">{{ node.errorMessage }}</div>
                </div>
              </div>
            </template>
          </div>
        </div>


      </div>
    </el-card>

    <el-dialog v-model="nodeDetailDialogVisible" :title="`节点详情 - ${activeNode?.nodeId || '-'}`" width="60%">
      <div v-if="activeNode" style="font-size: 12px; color: #606266; margin-bottom: 8px">
        节点类型：{{ activeNode.nodeType || '-' }} ｜ 状态：{{ activeNode.status || '-' }} ｜ 耗时：{{ activeNode.durationMs || 0 }}ms
      </div>
      <el-collapse>
        <el-collapse-item title="输入（inputSnapshot）" name="input">
          <pre class="debug-result-pre">{{ toPrettyJson(activeNode?.inputSnapshot || {}) }}</pre>
        </el-collapse-item>
        <el-collapse-item title="输出（outputSnapshot）" name="output">
          <pre class="debug-result-pre">{{ toPrettyJson(activeNode?.outputSnapshot || {}) }}</pre>
        </el-collapse-item>
      </el-collapse>
    </el-dialog>

    <el-dialog v-model="runHistoryDialogVisible" title="运行历史" width="78%" top="6vh">
      <div class="history-toolbar">
        <el-switch
          v-model="historyOnlyCurrentSession"
          active-text="仅当前会话"
          inactive-text="显示同工作流全部会话"
          @change="loadRunHistory"
        />
        <el-button size="small" text :loading="runHistoryLoading" @click="loadRunHistory">刷新</el-button>
      </div>

      <div class="history-dialog-body" v-loading="runHistoryLoading">
        <div class="history-list-col">
          <el-empty v-if="visibleRunHistory.length === 0" description="暂无运行记录" />
          <div v-else class="run-history-list">
            <div
              v-for="run in visibleRunHistory"
              :key="run.runId"
              class="run-history-item"
              :class="{ active: run.runId === selectedRunId }"
              @click="selectRun(run.runId)"
            >
              <div class="run-header">
                <el-tag :type="run.fromCurrentSession ? 'success' : 'info'" size="small">{{ run.fromCurrentSession ? '当前会话' : '其他会话' }}</el-tag>
                <el-tag :type="run.runType === 'debug' ? 'warning' : 'info'" size="small">{{ run.runType === 'debug' ? '调试' : '正式' }}</el-tag>
                <el-tag :type="run.status === 'success' ? 'success' : run.status === 'failed' ? 'danger' : 'warning'" size="small">
                  {{ run.status === 'success' ? '成功' : run.status === 'failed' ? '失败' : '运行中' }}
                </el-tag>
              </div>
              <div class="run-meta">{{ run.runId }}</div>
              <div class="run-time">{{ run.startedAt }}</div>
            </div>
          </div>
        </div>

        <div class="history-detail-col">
          <el-empty v-if="!selectedRunDetail" description="请选择左侧运行记录查看详情" />
          <template v-else>
            <div class="debug-panel-meta">
              <div><strong>RunId：</strong>{{ selectedRunDetail.runId }}</div>
              <div><strong>类型：</strong>{{ selectedRunDetail.runType === 'debug' ? '调试' : '正式' }}</div>
              <div><strong>状态：</strong>{{ selectedRunDetail.status || '-' }}</div>
              <div><strong>耗时：</strong>{{ selectedRunDetail.durationMs || 0 }}ms</div>
            </div>

            <el-collapse class="debug-result-collapse" accordion>
              <el-collapse-item title="最终结果（finalResult）" name="history-dialog-final-result">
                <pre class="debug-result-pre">{{ toPrettyJson(selectedRunDetail.finalResult || {}) }}</pre>
              </el-collapse-item>
            </el-collapse>

            <div class="debug-log-scroll history-log-scroll">
              <div v-if="(selectedRunDetail.nodes || []).length === 0" class="debug-log-empty">暂无节点日志</div>
              <div
                v-for="node in selectedRunDetail.nodes"
                :key="node.nodeId"
                class="debug-log-item debug-log-clickable"
                :class="`debug-log-${nodeLogLevel(node.status)}`"
                @click="openNodeDetail(node.nodeId, 'selected')"
              >
                <div class="debug-log-time">{{ node.nodeType }} · {{ node.durationMs || 0 }}ms</div>
                <div class="debug-log-text">{{ nodeLogMessage(node) }}</div>
                <div class="debug-log-hint">点击查看节点输入/输出</div>
                <div v-if="node.errorMessage" class="node-error">{{ node.errorMessage }}</div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="editingSessionId ? '编辑会话' : '新建会话'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="会话标题">
          <el-input v-model="form.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="工作流">
          <el-select v-model="form.workflowId" style="width: 100%" filterable no-data-text="暂无已发布的工作流，请先在工作流列表中发布">
            <el-option v-for="wf in workflowOptions" :key="wf.workflowId" :label="wf.name" :value="wf.workflowId" />
          </el-select>
        </el-form-item>
        <el-form-item label="固定入参JSON">
          <el-input v-model="form.workflowInputsJson" type="textarea" :rows="4" placeholder='例如 {"scene":"daily"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSession">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  chatflowCompletion,
  createChatflowSession,
  deleteChatflowSession,
  listChatflowMessages,
  listChatflowSessions,
  updateChatflowSession
} from '../../api/chatflow'
import { getRunDetail, listRuns, listWorkflows } from '../../api/workflow'
import type { ChatflowMessageItem, ChatflowSessionItem } from '../../types/chatflow'
import type { WorkflowRunDetail, WorkflowSummary } from '../../types/workflow'

const sessionLoading = ref(false)
const sessions = ref<ChatflowSessionItem[]>([])
const keyword = ref('')
const activeSessionId = ref('')

const messageLoading = ref(false)
const messages = ref<ChatflowMessageItem[]>([])
const inputText = ref('')
const sending = ref(false)

const latestRunId = ref('')
const latestRunSessionId = ref('')
const lastSyncedCompletedRunId = ref('')
const pendingBaselineCount = ref(0)
const runLoading = ref(false)
const runDetail = ref<WorkflowRunDetail | null>(null)
const activeNodeId = ref('')
const activeNodeScope = ref<'current' | 'selected'>('current')
const nodeDetailDialogVisible = ref(false)
let runPollTimer: number | null = null

// 运行历史
const runHistoryLoading = ref(false)
const runHistoryDialogVisible = ref(false)
const historyOnlyCurrentSession = ref(true)
const runHistory = ref<Array<{ runId: string; runType: string; status: string; durationMs?: number; startedAt?: string; fromCurrentSession: boolean }>>([])
const selectedRunId = ref('')
const selectedRunDetail = ref<WorkflowRunDetail | null>(null)

const workflowOptions = ref<WorkflowSummary[]>([])

const dialogVisible = ref(false)
const editingSessionId = ref('')
const form = reactive({
  title: '',
  workflowId: '',
  workflowInputsJson: '{}'
})

const activeSessionTitle = computed(() => {
  return sessions.value.find(s => s.sessionId === activeSessionId.value)?.title || ''
})

const activeNode = computed(() => {
  const source = activeNodeScope.value === 'selected' ? selectedRunDetail.value : runDetail.value
  if (!source || !activeNodeId.value) {
    return null
  }
  return source.nodes.find(n => n.nodeId === activeNodeId.value) || null
})

const visibleRunHistory = computed(() => {
  return runHistory.value
})

function toPrettyJson(value: unknown) {
  try {
    return JSON.stringify(value ?? {}, null, 2)
  } catch {
    return String(value ?? '')
  }
}

function nodeLogLevel(status?: string) {
  const normalized = (status || '').toLowerCase()
  if (normalized === 'success') return 'success'
  if (normalized === 'failed' || normalized === 'error') return 'error'
  return 'info'
}

function nodeLogMessage(node: { nodeId: string; status?: string; errorMessage?: string }) {
  const normalized = (node.status || '').toLowerCase()
  if (normalized === 'success') {
    return `节点 ${node.nodeId} 执行成功`
  }
  if (normalized === 'running') {
    return `节点 ${node.nodeId} 正在执行`
  }
  if (normalized === 'failed' || normalized === 'error') {
    return `节点 ${node.nodeId} 执行失败${node.errorMessage ? `：${node.errorMessage}` : ''}`
  }
  return `节点 ${node.nodeId} 状态：${node.status || 'unknown'}`
}

function openNodeDetail(nodeId: string, scope: 'current' | 'selected' = 'current') {
  activeNodeScope.value = scope
  activeNodeId.value = nodeId
  nodeDetailDialogVisible.value = true
}

function openRunHistoryDialog() {
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  runHistoryDialogVisible.value = true
  loadRunHistory()
}

async function syncMessagesWhenRunCompleted(runId: string, status?: string) {
  const detail = runDetail.value
  const normalized = (status || '').toLowerCase()
  if (normalized === 'running' || !runId) {
    return
  }
  if (lastSyncedCompletedRunId.value === runId) {
    return
  }
  if (activeSessionId.value !== latestRunSessionId.value) {
    return
  }

  for (let i = 0; i < 3; i++) {
    await loadMessages()
    if (hasAssistantReplyArrived()) {
      lastSyncedCompletedRunId.value = runId
      pendingBaselineCount.value = 0
      return
    }
    await new Promise(resolve => window.setTimeout(resolve, 500))
  }

  // 后端消息入库可能存在短暂延迟，兜底将 finalResult 先展示到对话框
  const fallbackAnswer = extractRunAnswer(detail)
  if (fallbackAnswer) {
    const localId = `local_${runId}`
    const exists = messages.value.some(m => m.messageId === localId)
    if (!exists) {
      messages.value = [
        ...messages.value,
        {
          messageId: localId,
          role: 'assistant',
          content: fallbackAnswer,
          createdAt: new Date().toISOString()
        }
      ]
    }
  }

  lastSyncedCompletedRunId.value = runId
  pendingBaselineCount.value = 0
}

function hasAssistantReplyArrived() {
  if (messages.value.length === 0) {
    return false
  }
  const last = messages.value[messages.value.length - 1]
  // 当前轮最少会新增一条 user 消息，assistant 到达后通常会成为最后一条
  // 使用 baseline+2 避免把“仅新增用户消息”误判为已完成
  if (pendingBaselineCount.value > 0 && messages.value.length >= pendingBaselineCount.value + 2 && last?.role === 'assistant') {
    return true
  }
  // 兜底：即使计数不满足，只要最后一条是 assistant 也视为已回填
  return last?.role === 'assistant'
}

function extractRunAnswer(detail: WorkflowRunDetail | null) {
  if (!detail?.finalResult) {
    return ''
  }
  const finalResult = detail.finalResult as Record<string, unknown>
  const candidate = finalResult.answer ?? finalResult.text ?? finalResult.output ?? finalResult.result
  const text = String(candidate ?? '').trim()
  return text
}

async function loadRun(runId: string) {
  runLoading.value = true
  try {
    const detail = await getRunDetail(runId)
    runDetail.value = detail
    if (!activeNodeId.value || !detail?.nodes?.some(n => n.nodeId === activeNodeId.value)) {
      activeNodeId.value = detail?.nodes?.[0]?.nodeId || ''
    }

    if (detail?.status === 'running') {
      startRunPolling(runId)
    } else {
      stopRunPolling()
      await syncMessagesWhenRunCompleted(runId, detail?.status)
    }
  } catch {
    ElMessage.warning('获取执行详情失败，请稍后重试')
  } finally {
    runLoading.value = false
  }
}

function stopRunPolling() {
  if (runPollTimer != null) {
    window.clearInterval(runPollTimer)
    runPollTimer = null
  }
}

function startRunPolling(runId: string) {
  stopRunPolling()
  runPollTimer = window.setInterval(async () => {
    try {
      const detail = await getRunDetail(runId)
      runDetail.value = detail
      if (!activeNodeId.value || !detail?.nodes?.some(n => n.nodeId === activeNodeId.value)) {
        activeNodeId.value = detail?.nodes?.[0]?.nodeId || ''
      }
      if (detail?.status !== 'running') {
        stopRunPolling()
        await syncMessagesWhenRunCompleted(runId, detail?.status)
      }
    } catch {
      stopRunPolling()
    }
  }, 1000)
}

async function refreshLatestRun() {
  if (!latestRunId.value) {
    return
  }
  await loadRun(latestRunId.value)
}

async function loadWorkflows() {
  try {
    const rows = await listWorkflows({ status: 'published' })
    workflowOptions.value = rows || []
  } catch {
    ElMessage.error('加载工作流列表失败，请刷新重试')
  }
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    const page = await listChatflowSessions({ keyword: keyword.value, pageNum: 1, pageSize: 100 })
    sessions.value = page?.list || []
    if (!activeSessionId.value && sessions.value.length > 0) {
      activeSessionId.value = sessions.value[0].sessionId
      await loadMessages()
    }
  } finally {
    sessionLoading.value = false
  }
}

async function selectSession(sessionId: string) {
  activeSessionId.value = sessionId
  latestRunId.value = ''
  latestRunSessionId.value = ''
  lastSyncedCompletedRunId.value = ''
  pendingBaselineCount.value = 0
  runDetail.value = null
  activeNodeId.value = ''
  selectedRunId.value = ''
  selectedRunDetail.value = null
  stopRunPolling()
  await loadMessages()
  await loadRunHistory()
}

async function loadMessages() {
  if (!activeSessionId.value) {
    messages.value = []
    return
  }
  messageLoading.value = true
  try {
    const page = await listChatflowMessages(activeSessionId.value, { pageNum: 1, pageSize: 100 })
    messages.value = page?.list || []
  } finally {
    messageLoading.value = false
  }
}

function openCreate() {
  editingSessionId.value = ''
  form.title = ''
  form.workflowId = workflowOptions.value[0]?.workflowId || ''
  form.workflowInputsJson = '{}'
  dialogVisible.value = true
}

function openEdit(item: ChatflowSessionItem) {
  editingSessionId.value = item.sessionId
  form.title = item.title
  form.workflowId = item.workflowId
  form.workflowInputsJson = JSON.stringify(item.workflowInputs || {}, null, 2)
  dialogVisible.value = true
}

async function submitSession() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入会话标题')
    return
  }
  if (!form.workflowId) {
    ElMessage.warning('请选择工作流')
    return
  }

  let workflowInputs: Record<string, unknown> = {}
  if (form.workflowInputsJson.trim()) {
    try {
      workflowInputs = JSON.parse(form.workflowInputsJson)
    } catch {
      ElMessage.error('固定入参JSON格式不正确')
      return
    }
  }

  const payload = {
    title: form.title.trim(),
    workflowId: form.workflowId,
    workflowInputs
  }

  if (editingSessionId.value) {
    await updateChatflowSession(editingSessionId.value, payload)
    ElMessage.success('更新成功')
  } else {
    await createChatflowSession(payload)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  await loadSessions()
}

async function removeSession(sessionId: string) {
  await ElMessageBox.confirm('确认删除该会话？', '删除确认', { type: 'warning' })
  await deleteChatflowSession(sessionId)
  ElMessage.success('删除成功')
  if (activeSessionId.value === sessionId) {
    activeSessionId.value = ''
    messages.value = []
  }
  await loadSessions()
}

async function send() {
  const content = inputText.value.trim()
  if (!content || !activeSessionId.value) {
    return
  }
  sending.value = true
  try {
    const result = await chatflowCompletion({ sessionId: activeSessionId.value, content })
    pendingBaselineCount.value = messages.value.length
    latestRunId.value = result.runId || ''
    latestRunSessionId.value = activeSessionId.value
    lastSyncedCompletedRunId.value = ''
    if (latestRunId.value) {
      await loadRun(latestRunId.value)
    }
    inputText.value = ''
    await loadMessages()
  } catch (err: any) {
    const isTimeout = err?.code === 'ECONNABORTED' || err?.message?.includes('timeout')
    if (isTimeout) {
      ElMessage.warning('响应较慢，正在刷新消息列表...')
      await loadMessages()
    } else {
      const msg = err?.response?.data?.msg || err?.message || '发送失败'
      ElMessage.error(msg)
    }
  } finally {
    sending.value = false
  }
}

async function loadRunHistory() {
  if (!activeSessionId.value) {
    return
  }
  runHistoryLoading.value = true
  try {
    const sessionId = activeSessionId.value
    const workflowId = sessions.value.find(s => s.sessionId === activeSessionId.value)?.workflowId
    if (!workflowId) {
      return
    }
    const page = await listRuns(workflowId, {
      pageNum: 1,
      pageSize: 200,
      triggerBy: historyOnlyCurrentSession.value ? sessionId : undefined
    })
    if (page?.list) {
      runHistory.value = page.list.map(run => ({
        runId: run.runId,
        runType: run.runType || 'prod',
        status: run.status || 'unknown',
        durationMs: run.durationMs,
        startedAt: run.startedAt,
        fromCurrentSession: run.triggerBy === sessionId
      }))
      const visibleIds = new Set(runHistory.value.map(item => item.runId))
      if (selectedRunId.value && !visibleIds.has(selectedRunId.value)) {
        selectedRunId.value = ''
        selectedRunDetail.value = null
      }
    }
  } catch (err) {
    ElMessage.error('加载运行历史失败')
  } finally {
    runHistoryLoading.value = false
  }
}

async function selectRun(runId: string) {
  selectedRunId.value = runId
  try {
    selectedRunDetail.value = await getRunDetail(runId)
  } catch (err) {
    ElMessage.error('加载运行详情失败')
  }
}

onMounted(async () => {
  await loadWorkflows()
  await loadSessions()
})

onBeforeUnmount(() => {
  stopRunPolling()
})
</script>

<style scoped>
.workbench-page {
  display: grid;
  grid-template-columns: 320px minmax(560px, 1fr) 320px;
  gap: 12px;
  height: calc(100vh - 70px);
  padding: 12px;
  box-sizing: border-box;
}

.session-panel,
.chat-panel,
.run-panel {
  height: 100%;
}

.run-panel {
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.run-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
}

.run-panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-id {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.session-list {
  margin-top: 10px;
  height: calc(100% - 72px);
  overflow: auto;
}

.session-item {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 8px;
  margin-bottom: 8px;
  cursor: pointer;
}

.session-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.session-item .title {
  font-weight: 600;
}

.session-item .meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 2px;
}

.session-item .actions {
  margin-top: 6px;
}

.message-list {
  height: calc(100% - 160px);
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}

.msg-row {
  display: flex;
  margin-bottom: 10px;
}

.msg-row.user {
  justify-content: flex-end;
}

.msg-row .bubble {
  max-width: 75%;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
}

.msg-row.user .bubble {
  background: var(--el-color-primary-light-8);
}

.composer {
  margin-top: 10px;
}

.composer-action {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.run-panel-scroll {
  height: 100%;
  overflow: auto;
}

.run-section {
  margin-bottom: 12px;
}

.run-live-panel {
  min-height: 180px;
}

.run-history-panel {
  margin-bottom: 12px;
}

.run-detail-panel {
  margin-top: 12px;
}

.history-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.history-dialog-body {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 12px;
  min-height: 520px;
}

.history-list-col {
  border-right: 1px solid #ebeef5;
  padding-right: 12px;
  max-height: 520px;
  overflow-y: auto;
}

.history-detail-col {
  max-height: 520px;
  overflow-y: auto;
}

.debug-panel-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #666;
  align-items: center;
}

.debug-log-scroll {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow-y: auto;
  padding: 10px;
  background: #fafafa;
}

.live-log-scroll {
  max-height: calc(100vh - 340px);
}

.history-log-scroll {
  max-height: 320px;
}

.run-history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.run-history-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  cursor: pointer;
  background: #fff;
}

.run-history-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.run-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.run-meta,
.run-time,
.duration {
  font-size: 12px;
  color: #909399;
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
  max-height: 220px;
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

.node-error {
  margin-top: 4px;
  color: var(--el-color-danger);
  font-size: 12px;
}
</style>
