<template>
  <div class="chat-page">
    <div class="page-header">
      <div class="header-left">
        <div class="title-with-help">
          <h1>大模型联调工作台</h1>
          <el-tooltip
            content="同步验证 gijela-core-chat 的同步、流式、技能与附件链路。"
            placement="top"
          >
            <el-icon class="title-help-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <p>支持会话联调、附件上下文、知识检索、日志追踪与技能/MCP 管理。</p>
      </div>
      <el-space class="header-actions" wrap>
        <el-button type="success" plain @click="createNewSession">新建会话</el-button>
        <el-button type="primary" plain @click="goKnowledgePage">知识库管理</el-button>
        <el-button type="warning" plain @click="goGraphPage">图谱管理</el-button>
        <el-button type="info" plain @click="goAttachmentPage">附件管理</el-button>
        <el-button type="warning" plain @click="goStoragePage">存储管理</el-button>
        <el-button type="success" plain @click="goSkillPage">技能管理</el-button>
        <el-button type="danger" plain @click="goMcpPage">MCP 管理</el-button>
        <el-button type="info" plain @click="goModelConfigPage">模型配置</el-button>
        <el-button type="primary" plain @click="goPromptPage">提示词管理</el-button>
        <el-button type="info" plain @click="goLlmLogsPage">日志看板</el-button>
        <el-button @click="store.clearMessages">仅清空当前窗口</el-button>
      </el-space>
    </div>

    <el-row :gutter="16" class="chat-layout">
      <el-col :span="6" class="panel-col left-col">
        <el-card shadow="never" class="side-card sessions-card">
          <template #header>
            <div class="panel-header-row">
              <div class="panel-header">会话列表</div>
              <el-switch v-model="failedOnly" inline-prompt active-text="仅失败" inactive-text="全部" />
            </div>
          </template>
          <el-empty v-if="!filteredSessions.length" :description="failedOnly ? '暂无失败会话' : '暂无会话'" :image-size="80" />
          <el-scrollbar v-else class="session-scroll">
            <div
              v-for="item in filteredSessions"
              :key="item.sessionId"
              class="session-item"
              :class="{ active: store.sessionId === item.sessionId, failed: isFailedSession(item) }"
              @click="switchSession(item.sessionId)"
            >
              <div class="session-title-row">
                <div class="session-title">{{ item.title || item.sessionId }}</div>
                <div class="session-actions">
                  <el-button link type="primary" size="small" @click.stop="editSession(item)">编辑</el-button>
                  <el-button link type="danger" size="small" @click.stop="removeSession(item.sessionId)">删除</el-button>
                </div>
              </div>
              <div class="session-meta-row">
                <el-tag v-if="item.model" size="small" type="info" effect="plain">{{ item.model }}</el-tag>
                <el-tag v-if="isFailedSession(item)" size="small" type="danger" effect="light">含失败回合</el-tag>
              </div>
              <div class="session-summary">{{ item.summary || '点击加载会话消息' }}</div>
            </div>
          </el-scrollbar>
        </el-card>


        <el-card shadow="never" class="side-card">
          <template #header>
            <div class="panel-header">附件上传</div>
          </template>
          <el-upload :auto-upload="false" :show-file-list="false" :on-change="handleFileChange">
            <el-button type="primary" plain :disabled="!store.sessionId">选择文件</el-button>
          </el-upload>
          <div v-if="!store.sessionId" class="upload-tip">请先从会话列表选择一个会话后再上传附件</div>
          <div v-if="uploadResult" class="upload-result">
            {{ uploadResult }}
          </div>
        </el-card>

      </el-col>

      <el-col :span="18" class="panel-col right-col">
        <div class="window-wrap">
          <ChatWindow 
            :messages="store.messages" 
            :pending-text="store.pendingText" 
            :pending-created-at="store.pendingCreatedAt"
            :pending-usage="store.pendingUsage"
            :active-tool-calls="store.activeToolCalls"
            @open-references="openReferencesPanel"
          />
        </div>
        <el-card shadow="never" class="composer-card">
          <div class="composer-model-row">
            <span class="composer-model-label">当前发送模型</span>
            <el-select
              v-model="store.sessionModel"
              filterable
              allow-create
              clearable
              default-first-option
              style="width: 240px"
              placeholder="可选，默认 qwen-plus"
            >
              <el-option v-for="item in chatModelOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <span class="composer-model-tip">可手动选择或留空，留空时默认使用 qwen-plus</span>
          </div>
          <el-input
            v-model="prompt"
            type="textarea"
            :rows="4"
            placeholder="请输入问题，例如：调用 knowledge.search 检索一段知识。"
            @keydown="handleComposerKeydown"
          />
          <div class="composer-actions">
            <div class="status-text">
              <span v-if="store.streaming">正在流式响应...</span>
              <span v-else-if="store.lastError" class="error-text">{{ store.lastError }}</span>
            </div>
            <el-space>
              <el-switch
                v-model="enterToSend"
                inline-prompt
                active-text="回车发送"
                inactive-text="回车换行"
              />
              <el-button :disabled="disabled" @click="sendSync">同步发送</el-button>
              <el-button type="primary" :loading="store.streaming" :disabled="disabled" @click="sendStream">流式发送</el-button>
            </el-space>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="showNewSessionDialog" title="新建会话" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="会话名称" required>
          <el-input v-model="newSessionTitle" maxlength="64" show-word-limit placeholder="请输入会话名称" />
        </el-form-item>
        <el-form-item label="对话大模型（可选）">
          <el-select v-model="newSessionModel" filterable allow-create clearable default-first-option style="width: 100%" placeholder="可留空，默认使用当前发送模型或 qwen-plus">
            <el-option v-for="item in chatModelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="prompt-meta-text" style="margin-top: 8px;">会话创建后，后续消息将默认使用该模型</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showNewSessionDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmNewSession">确认创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditSessionDialog" title="编辑会话" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="会话名称" required>
          <el-input v-model="editSessionTitle" maxlength="64" show-word-limit placeholder="请输入会话名称" />
        </el-form-item>
        <el-form-item label="对话大模型（可选）">
          <el-select v-model="editSessionModel" filterable allow-create clearable default-first-option style="width: 100%" placeholder="可留空，默认使用 qwen-plus">
            <el-option v-for="item in chatModelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="prompt-meta-text" style="margin-top: 8px;">修改后会立即应用到该会话的后续消息</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditSessionDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmEditSession">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showReferencePanel" title="知识库引用列表" direction="rtl" size="40%" destroy-on-close>
      <div class="reference-list-wrap">
        <el-empty v-if="!currentReferences.length" description="暂无引用" :image-size="72" />
        <el-scrollbar v-else class="reference-list-scroll">
          <button
            v-for="(ref, idx) in currentReferences"
            :key="`drawer-ref-${idx}`"
            type="button"
            class="reference-list-item"
            @click="openReferenceDetail(ref)"
          >
            <div class="reference-list-title">{{ ref.title || `引用 ${idx + 1}` }}</div>
            <div class="reference-list-meta">
              <el-tag size="small" type="info" effect="plain">相似度 {{ formatScore(ref.score) }}</el-tag>
              <span v-if="referenceSnippet(ref.payload)" class="reference-list-snippet">{{ referenceSnippet(ref.payload) }}</span>
            </div>
          </button>
        </el-scrollbar>
      </div>
    </el-drawer>

    <el-dialog v-model="showReferenceDetailDialog" title="引用详情" width="720px" destroy-on-close>
      <div v-if="selectedReference" class="reference-detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="ID">{{ selectedReference.id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ selectedReference.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="相似度">{{ formatScore(selectedReference.score) }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-section-title">内容</div>
        <el-scrollbar class="detail-scroll">
          <pre class="detail-pre">{{ JSON.stringify(selectedReference.payload, null, 2) }}</pre>
        </el-scrollbar>
      </div>
    </el-dialog>

    <el-drawer v-model="showSystemPromptDrawer" title="系统级提示词" direction="rtl" size="45%" destroy-on-close>
      <el-form label-position="top">
        <div class="prompt-meta-row">
          <el-tag type="info" effect="plain">草稿版本：{{ Number(promptStore.systemCurrent?.draftVersion || 0) }}</el-tag>
          <el-tag type="success" effect="plain">发布版本：{{ Number(promptStore.systemCurrent?.publishedVersion || 0) }}</el-tag>
          <span class="prompt-meta-text">最近发布时间：{{ formatDateTimeLabel(promptStore.systemCurrent?.publishedAt) }}</span>
        </div>
        <el-form-item label="草稿内容">
          <el-input v-model="systemDraftText" type="textarea" :rows="14" placeholder="请输入系统级提示词草稿" />
        </el-form-item>
        <el-form-item>
          <el-space>
            <el-button :loading="promptStore.loading" @click="saveSystemPromptDraftAction">保存草稿</el-button>
            <el-button type="primary" :loading="promptStore.loading" @click="publishSystemPromptAction">发布</el-button>
            <el-button type="warning" :loading="promptStore.loading" @click="rollbackSystemPromptAction">回滚到当前发布版本</el-button>
          </el-space>
        </el-form-item>
      </el-form>
    </el-drawer>

    <el-drawer v-model="showSessionPromptDrawer" title="会话级提示词" direction="rtl" size="38%" destroy-on-close>
      <el-form label-position="top">
        <div class="prompt-meta-row">
          <el-tag type="info" effect="plain">当前版本：{{ Number(promptStore.sessionPrompt?.version || 0) }}</el-tag>
          <span class="prompt-meta-text">最近更新时间：{{ formatDateTimeLabel(promptStore.sessionPrompt?.updatedAt) }}</span>
        </div>
        <el-form-item label="会话覆盖内容">
          <el-input v-model="sessionPromptText" type="textarea" :rows="10" placeholder="请输入当前会话提示词（仅影响后续消息）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="promptStore.loading" :disabled="!store.sessionId" @click="saveSessionPromptAction">保存会话提示词</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import ChatWindow from '@/components/ChatWindow.vue'
import { listModelConfigOptions } from '@/api/chat'

import { useChatStore } from '@/store/chat'
import { usePromptStore } from '@/store/prompt'
import type { ChatSessionItem, ModelConfigOptionItem } from '@/types/chat'

const store = useChatStore()
const promptStore = usePromptStore()
const router = useRouter()
const prompt = ref('')
const showNewSessionDialog = ref(false)
const newSessionTitle = ref('')
const newSessionModel = ref('qwen-plus')
const showEditSessionDialog = ref(false)
const editingSessionId = ref('')
const editSessionTitle = ref('')
const editSessionModel = ref('')
const showSystemPromptDrawer = ref(false)
const showSessionPromptDrawer = ref(false)
const systemDraftText = ref('')
const sessionPromptText = ref('')
const systemDraftVersion = ref(0)

const uploadResult = ref('')
const failedOnly = ref(false)
const ENTER_TO_SEND_KEY = 'chat:enter-to-send'
const enterToSend = ref(true)
type ReferenceItem = { id?: string | number; title?: string; score?: number; payload?: Record<string, unknown> }
type PromptConflictPayload = {
  scopeType?: 'system' | 'session'
  latestVersion?: number
  latestContent?: string
  latestUpdatedAt?: string
}

const selectedReference = ref<ReferenceItem | null>(null)
const currentReferences = ref<ReferenceItem[]>([])
const showReferencePanel = ref(false)
const showReferenceDetailDialog = ref(false)
const chatModelOptions = ref<Array<{ label: string; value: string }>>([
  { label: 'Qwen Plus', value: 'qwen-plus' },
  { label: 'Qwen Max', value: 'qwen-max' },
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
  { label: 'DeepSeek Reasoner', value: 'deepseek-reasoner' },
  { label: 'GPT-4o Mini', value: 'gpt-4o-mini' }
])



onMounted(() => {
  const saved = window.localStorage.getItem(ENTER_TO_SEND_KEY)
  if (saved === '0') {
    enterToSend.value = false
  }
  void loadChatModelOptions()
  void store.loadSessions()
})

watch(enterToSend, (value) => {
  window.localStorage.setItem(ENTER_TO_SEND_KEY, value ? '1' : '0')
})

const disabled = computed(() => !prompt.value.trim())
const filteredSessions = computed(() => {
  if (!failedOnly.value) {
    return store.sessions
  }
  return store.sessions.filter((item) => isFailedSession(item))
})

async function sendSync() {
  if (!prompt.value.trim()) {
    return
  }
  try {
    await store.sendSync(prompt.value.trim())
    prompt.value = ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '同步发送失败')
  }
}

async function sendStream() {
  if (!prompt.value.trim()) {
    return
  }
  try {
    await store.sendStream(prompt.value.trim())
    prompt.value = ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '流式发送失败')
  }
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (!enterToSend.value) {
    return
  }
  if (event.key !== 'Enter') {
    return
  }
  if (event.isComposing) {
    return
  }
  if (event.shiftKey) {
    return
  }
  event.preventDefault()
  if (disabled.value || store.streaming) {
    return
  }
  void sendStream()
}

async function handleFileChange(file: { raw?: File }) {
  if (!file.raw) {
    return
  }
  if (!store.sessionId) {
    ElMessage.warning('请先选择会话，再上传附件')
    return
  }
  try {
    const result = await store.upload(file.raw)
    uploadResult.value = `上传成功：${String(result.objectKey || '')}`
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '附件上传失败'))
  }
}

function resolveApiErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { msg?: string } }; message?: string }
  const backendMsg = maybe?.response?.data?.msg
  if (typeof backendMsg === 'string' && backendMsg.trim()) {
    return backendMsg
  }
  if (typeof maybe?.message === 'string' && maybe.message.trim()) {
    return maybe.message
  }
  return fallback
}

function extractPromptConflictPayload(error: unknown): PromptConflictPayload | null {
  const maybe = error as {
    response?: {
      data?: {
        msg?: string
        data?: PromptConflictPayload
      }
    }
  }
  const message = maybe?.response?.data?.msg || ''
  if (!message.includes('PROMPT_VERSION_CONFLICT')) {
    return null
  }
  return maybe?.response?.data?.data || null
}

function applySystemPromptConflict(payload: PromptConflictPayload) {
  systemDraftVersion.value = Number(payload.latestVersion || 0)
  systemDraftText.value = payload.latestContent || ''
  promptStore.systemCurrent = {
    ...(promptStore.systemCurrent || {}),
    appCode: promptStore.systemCurrent?.appCode || 'chat',
    modelRoute: promptStore.systemCurrent?.modelRoute || 'default',
    draftVersion: systemDraftVersion.value,
    draftContent: systemDraftText.value
  }
}

function applySessionPromptConflict(payload: PromptConflictPayload) {
  sessionPromptText.value = payload.latestContent || ''
  promptStore.sessionPrompt = {
    sessionId: store.sessionId || promptStore.sessionPrompt?.sessionId || '',
    content: sessionPromptText.value,
    version: Number(payload.latestVersion || 0),
    updatedAt: payload.latestUpdatedAt
  }
}

function openReferencesPanel(references: ReferenceItem[]) {
  currentReferences.value = Array.isArray(references) ? references : []
  showReferencePanel.value = true
}

function openReferenceDetail(ref: ReferenceItem) {
  selectedReference.value = ref
  showReferenceDetailDialog.value = true
}

function formatScore(score?: number) {
  if (typeof score !== 'number' || Number.isNaN(score)) {
    return '-'
  }
  return score.toFixed(4)
}

function formatDateTimeLabel(value?: string | null) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function referenceSnippet(payload?: Record<string, unknown>) {
  if (!payload) {
    return ''
  }
  const candidates = ['content', 'chunk', 'text', 'summary']
  for (const key of candidates) {
    const value = payload[key]
    if (typeof value === 'string' && value.trim()) {
      const clean = value.trim().replace(/\s+/g, ' ')
      return clean.length > 64 ? `${clean.slice(0, 64)}...` : clean
    }
  }
  return ''
}

async function switchSession(targetSessionId: string) {
  if (!targetSessionId) {
    return
  }
  const matchedSession = store.sessions.find((item) => item.sessionId === targetSessionId)
  if (matchedSession?.model) {
    store.sessionModel = matchedSession.model
  }
  await store.loadMessages(targetSessionId)
}

function goKnowledgePage() {
  void router.push('/knowledge')
}

function goGraphPage() {
  void router.push('/graph')
}

function goAttachmentPage() {
  void router.push('/attachments')
}

function goStoragePage() {
  void router.push('/storage')
}

function goSkillPage() {
  void router.push('/skills')
}

function goMcpPage() {
  void router.push('/mcp-servers')
}

function goPromptPage() {
  void router.push('/prompts')
}

function goModelConfigPage() {
  void router.push('/model-configs')
}

function goLlmLogsPage() {
  void router.push('/chat/logs')
}

function createNewSession() {
  newSessionTitle.value = ''
  newSessionModel.value = store.sessionModel || chatModelOptions.value[0]?.value || 'qwen-plus'
  showNewSessionDialog.value = true
}

async function loadChatModelOptions() {
  try {
    const options = await listModelConfigOptions('CHAT')
    if (!options.length) {
      return
    }
    chatModelOptions.value = options.map((item: ModelConfigOptionItem) => ({
      label: item.label || `${item.providerKey} / ${item.model}`,
      value: item.model
    }))
    if (!newSessionModel.value.trim()) {
      newSessionModel.value = chatModelOptions.value[0]?.value || ''
    }
  } catch {
    // 忽略加载失败，回退到内置模型列表
  }
}

async function confirmNewSession() {
  const title = newSessionTitle.value.trim()
  const model = newSessionModel.value.trim() || store.sessionModel || 'qwen-plus'
  if (!title) {
    ElMessage.warning('请先输入会话名称')
    return
  }
  try {
    await store.startNewSession(title, model)
    showNewSessionDialog.value = false
    ElMessage.success('已新建会话')
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '新建会话失败'))
  }
}

async function openSystemPromptDrawer() {
  try {
    await promptStore.loadSystemPrompt('chat', 'default')
    systemDraftText.value = promptStore.systemCurrent?.draftContent || ''
    systemDraftVersion.value = Number(promptStore.systemCurrent?.draftVersion || 0)
    showSystemPromptDrawer.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载系统提示词失败')
  }
}

async function saveSystemPromptDraftAction() {
  try {
    const result = await promptStore.saveDraft({
      appCode: 'chat',
      modelRoute: 'default',
      draftContent: systemDraftText.value,
      draftVersion: systemDraftVersion.value
    })
    systemDraftVersion.value = Number(result?.draftVersion || systemDraftVersion.value)
    ElMessage.success('系统提示词草稿已保存')
  } catch (error) {
    const conflict = extractPromptConflictPayload(error)
    if (conflict?.scopeType === 'system') {
      applySystemPromptConflict(conflict)
      ElMessage.warning('检测到版本冲突，已刷新为最新系统提示词，请确认后重试')
      return
    }
    ElMessage.error(resolveApiErrorMessage(error, '保存系统提示词草稿失败'))
  }
}

async function publishSystemPromptAction() {
  try {
    const result = await promptStore.publish({
      appCode: 'chat',
      modelRoute: 'default',
      draftVersion: systemDraftVersion.value,
      publishNote: 'chat-page publish'
    })
    systemDraftVersion.value = Number(result?.draftVersion || systemDraftVersion.value)
    ElMessage.success('系统提示词已发布')
  } catch (error) {
    const conflict = extractPromptConflictPayload(error)
    if (conflict?.scopeType === 'system') {
      applySystemPromptConflict(conflict)
      ElMessage.warning('检测到版本冲突，已刷新为最新系统提示词，请确认后重新发布')
      return
    }
    ElMessage.error(resolveApiErrorMessage(error, '发布系统提示词失败'))
  }
}

async function rollbackSystemPromptAction() {
  try {
    const current = promptStore.systemCurrent
    const targetVersion = Number(current?.publishedVersion || 0)
    if (targetVersion <= 0) {
      ElMessage.warning('当前没有可回滚的发布版本')
      return
    }
    await promptStore.rollback({
      appCode: 'chat',
      modelRoute: 'default',
      targetPublishedVersion: targetVersion
    })
    systemDraftText.value = promptStore.systemCurrent?.draftContent || ''
    systemDraftVersion.value = Number(promptStore.systemCurrent?.draftVersion || 0)
    ElMessage.success('已回滚到当前发布版本')
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '回滚系统提示词失败'))
  }
}

async function openSessionPromptDrawer() {
  if (!store.sessionId) {
    ElMessage.warning('请先选择会话')
    return
  }
  try {
    await promptStore.loadSessionPrompt(store.sessionId)
    sessionPromptText.value = promptStore.sessionPrompt?.content || ''
    showSessionPromptDrawer.value = true
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '加载会话提示词失败'))
  }
}

function resetSessionPromptLocalState() {
  sessionPromptText.value = ''
  promptStore.sessionPrompt = null
}

watch(() => store.sessionId, () => {
  resetSessionPromptLocalState()
})

async function saveSessionPromptAction() {
  if (!store.sessionId) {
    ElMessage.warning('请先选择会话')
    return
  }
  try {
    const nextVersion = Number(promptStore.sessionPrompt?.version || 0)
    await promptStore.saveSessionPrompt(store.sessionId, {
      content: sessionPromptText.value,
      version: nextVersion
    })
    sessionPromptText.value = promptStore.sessionPrompt?.content || ''
    ElMessage.success('会话提示词已保存')
  } catch (error) {
    const conflict = extractPromptConflictPayload(error)
    if (conflict?.scopeType === 'session') {
      applySessionPromptConflict(conflict)
      ElMessage.warning('检测到版本冲突，已刷新为最新会话提示词，请确认后重试')
      return
    }
    ElMessage.error(resolveApiErrorMessage(error, '保存会话提示词失败'))
  }
}

async function removeSession(targetSessionId: string) {
  try {
    await ElMessageBox.confirm('删除后将清除该会话的消息与附件记录，是否继续？', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await store.removeSession(targetSessionId)
    ElMessage.success('会话已删除')
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除会话失败')
  }
}

function editSession(item: ChatSessionItem) {
  editingSessionId.value = item.sessionId
  editSessionTitle.value = item.title || ''
  editSessionModel.value = item.model || store.sessionModel || 'qwen-plus'
  showEditSessionDialog.value = true
}

async function confirmEditSession() {
  const sessionId = editingSessionId.value.trim()
  const title = editSessionTitle.value.trim()
  const model = editSessionModel.value.trim()
  if (!sessionId) {
    ElMessage.error('会话 ID 不能为空')
    return
  }
  if (!title) {
    ElMessage.warning('请先输入会话名称')
    return
  }
  try {
    await store.renameSession(sessionId, title, model || undefined)
    showEditSessionDialog.value = false
    ElMessage.success('会话信息已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '编辑失败')
  }
}

function isFailedSession(item: ChatSessionItem) {
  const summary = item.summary || ''
  return summary.includes('失败回合') || summary.includes('ERROR:')
}
</script>

<style scoped>
.chat-page {
  height: 100dvh;
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f5f7fa 0%, #eef3ff 100%);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
  flex: 0 0 auto;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e8edf7;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.04);
}

.header-left {
  min-width: 240px;
}

.page-header h1 {
  margin: 0;
  font-size: 20px;
  line-height: 1.3;
  font-weight: 600;
  color: #1f2937;
}

.title-with-help {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}

.title-help-icon {
  margin-top: 2px;
  font-size: 14px;
  color: #909399;
  cursor: help;
}

.title-help-icon:hover {
  color: #409eff;
}

.page-header p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.45;
  color: #606266;
}

.header-actions {
  justify-content: flex-end;
  row-gap: 8px;
}

.chat-layout {
  flex: 1;
  min-height: 0;
}

.panel-col {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.right-col {
  gap: 16px;
}

.window-wrap {
  flex: 1;
  min-height: 0;
}

.side-card,
.composer-card {
  margin-top: 16px;
  border-radius: 16px;
}

.panel-col .side-card:first-child {
  margin-top: 0;
}

.sessions-card {
  flex: 1;
  min-height: 0;
}

.sessions-card :deep(.el-card__body) {
  height: calc(100% - 56px);
  overflow: hidden;
}

.session-scroll {
  height: 100%;
}

.composer-card {
  margin-top: 0;
  flex: 0 0 auto;
}

.composer-model-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.composer-model-label {
  font-size: 13px;
  color: #606266;
}

.composer-model-tip {
  font-size: 12px;
  color: #909399;
}

.panel-header {
  font-weight: 600;
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.session-item {
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 8px;
  background: #f7f9fc;
}

.session-item:hover {
  background: #ecf5ff;
}

.session-item.active {
  background: #e1f0ff;
  border: 1px solid #b3d8ff;
}

.session-item.failed {
  border: 1px solid #fbc4c4;
  background: #fff7f7;
}

.session-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-meta-row {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.session-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  white-space: nowrap;
}

.session-title {
  font-weight: 500;
  font-size: 13px;
  color: #303133;
  min-width: 0;
  flex: 1 1 auto;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-summary {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.upload-result {
  margin-top: 12px;
  color: #67c23a;
  word-break: break-all;
}


.upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #e6a23c;
}

.prompt-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.prompt-meta-text {
  color: #606266;
  font-size: 13px;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.status-text {
  color: #909399;
}

.error-text {
  color: #f56c6c;
}

.reference-detail {
  padding: 12px 0;
}

.reference-list-wrap {
  height: 100%;
}

.reference-list-scroll {
  max-height: calc(100vh - 140px);
}

.reference-list-item {
  width: 100%;
  text-align: left;
  border: 1px solid #d9e6ff;
  border-radius: 10px;
  background: linear-gradient(180deg, #f8fbff 0%, #eef4ff 100%);
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.reference-list-item:hover {
  border-color: #9ec5ff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.14);
  transform: translateY(-1px);
}

.reference-list-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}

.reference-list-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reference-list-snippet {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-section-title {
  margin: 16px 0 8px;
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}

.detail-scroll {
  max-height: 400px;
}

.detail-pre {
  margin: 0;
  padding: 12px;
  background: #f4f5f7;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
