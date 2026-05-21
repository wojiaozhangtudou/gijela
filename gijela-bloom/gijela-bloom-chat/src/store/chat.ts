import { defineStore } from 'pinia'
import { ref } from 'vue'
import { completeChat, createSession, deleteSession, fetchSessionMessages, fetchSessions, indexKnowledge, indexKnowledgeFile, streamChat, updateSessionTitle, uploadAttachment } from '@/api/chat'
import type { ChatCompletionRequest, ChatEvent, ChatHistoryMessage, ChatMessage, ChatSessionItem, KnowledgeIndexRequest } from '@/types/chat'

export const useChatStore = defineStore('chat', () => {
  const sessionId = ref('')
  const sessionTitle = ref('')
  const sessionModel = ref('qwen-plus')
  const skills = ref([] as string[])
  const messages = ref([] as ChatMessage[])
  const sessions = ref([] as ChatSessionItem[])
  const streaming = ref(false)
  const pendingText = ref('')
  const pendingCreatedAt = ref('')
  const pendingUsage = ref<ChatMessage['usage']>(undefined)
  const pendingReferences = ref<ChatMessage['references']>(undefined)
  const lastError = ref('')
  const streamFailureHandled = ref(false)
  const activeToolCalls = ref<Array<{ name: string; query?: string }>>([])

  function nowIso() {
    return new Date().toISOString()
  }

  async function sendSync(content: string) {
    const userMessage: ChatMessage = { role: 'user', content, createdAt: nowIso() }
    messages.value.push(userMessage)
    const payload: ChatCompletionRequest = {
      sessionId: sessionId.value || undefined,
      messages: [userMessage],
      model: sessionModel.value || 'qwen-plus',
      skills: skills.value
    }
    try {
      const response = await completeChat(payload)
      sessionId.value = response.sessionId
      messages.value.push({
        role: 'assistant',
        content: response.content,
        createdAt: nowIso(),
        usage: response.usage,
        references: response.references || undefined
      })
      await syncSessionTitleIfNeeded()
      await loadSessions()
    } catch (error) {
      const reason = error instanceof Error ? error.message : '同步调用失败'
      markLatestUserRoundFailed(reason)
      messages.value.push({
        role: 'assistant',
        content: `失败回合：${reason}`,
        createdAt: nowIso(),
        failed: true
      })
      lastError.value = reason
      await loadSessions()
      throw error
    }
  }

  async function sendStream(content: string) {
    const userMessage: ChatMessage = { role: 'user', content, createdAt: nowIso() }
    messages.value.push(userMessage)
    streaming.value = true
    pendingText.value = ''
    pendingCreatedAt.value = nowIso()
    pendingUsage.value = undefined
    pendingReferences.value = undefined
    lastError.value = ''
    streamFailureHandled.value = false
    await streamChat(
      {
        sessionId: sessionId.value || undefined,
        messages: [userMessage],
        model: sessionModel.value || 'qwen-plus',
        skills: skills.value
      },
      {
        onEvent: (event: ChatEvent) => {
          consumeEvent(event)
        },
        onError: (message) => {
          handleStreamFailure(message)
        },
        onDone: () => {
          streaming.value = false
          if (pendingText.value && !streamFailureHandled.value) {
            messages.value.push({
              role: 'assistant',
              content: pendingText.value,
              createdAt: nowIso(),
              usage: pendingUsage.value,
              references: pendingReferences.value
            })
            pendingText.value = ''
            pendingUsage.value = undefined
            pendingReferences.value = undefined
          }
          pendingCreatedAt.value = ''
          void syncSessionTitleIfNeeded()
          void loadSessions()
        }
      }
    )
  }

  async function syncSessionTitleIfNeeded() {
    const targetTitle = sessionTitle.value.trim()
    if (!targetTitle || !sessionId.value) {
      return
    }
    try {
      await updateSessionTitle(sessionId.value, targetTitle)
    } catch {
      // 忽略标题同步失败，避免影响主链路
    }
  }

  async function loadSessions(limit = 20) {
    sessions.value = await fetchSessions(limit)
  }

  async function loadMessages(targetSessionId: string, limit = 100) {
    const rows: ChatHistoryMessage[] = await fetchSessionMessages(targetSessionId, limit)
    sessionId.value = targetSessionId
    const matchedSession = sessions.value.find((item) => item.sessionId === targetSessionId)
    if (matchedSession?.model) {
      sessionModel.value = matchedSession.model
    }
    messages.value = mapHistoryMessages(rows)
    pendingText.value = ''
    pendingCreatedAt.value = ''
    pendingUsage.value = undefined
    streamFailureHandled.value = false
  }

  function consumeEvent(event: ChatEvent) {
    if (event.sessionId) {
      sessionId.value = event.sessionId
    }
    if (event.type === 'delta' && event.content) {
      pendingText.value += event.content
    }
    if (event.type === 'tool_call' && event.toolCall) {
      const name = String(event.toolCall.name ?? '')
      const args = (event.toolCall.arguments ?? {}) as Record<string, unknown>
      activeToolCalls.value.push({ name, query: args.query ? String(args.query) : undefined })
    }
    if (event.type === 'tool_result') {
      // 工具返回后清空待扰列表（模型继续生成时䯁用卷向下）
      activeToolCalls.value = []
      const refsFromTool = extractReferencesFromToolResult(event.toolResult ?? undefined)
      if (refsFromTool.length) {
        pendingReferences.value = refsFromTool
      }
    }
    if (event.references?.length) {
      pendingReferences.value = event.references
    }
    if (event.usage) {
      pendingUsage.value = event.usage
    }
    if (event.type === 'done') {
      streaming.value = false
      activeToolCalls.value = []
    }
    if (event.type === 'error') {
      activeToolCalls.value = []
      handleStreamFailure(event.error || '流式调用失败')
    }
  }

  function mapHistoryMessages(rows: ChatHistoryMessage[]): ChatMessage[] {
    const mapped: ChatMessage[] = rows.map((item) => ({
      role: item.role as ChatMessage['role'],
      content: item.content,
      createdAt: item.createdAt || undefined,
      usage: undefined,
      references: item.references || undefined
    }))

    for (let i = 0; i < mapped.length; i++) {
      const current = mapped[i]
      if (current.role !== 'assistant' || !isFailureAssistantMessage(current.content)) {
        continue
      }
      current.failed = true
      for (let j = i - 1; j >= 0; j--) {
        if (mapped[j].role === 'user') {
          mapped[j].failed = true
          break
        }
      }
    }

    return mapped
  }

  function isFailureAssistantMessage(content: string): boolean {
    return content.startsWith('失败回合：') || content.startsWith('失败回合（已收到部分响应）：')
  }

  function extractReferencesFromToolResult(toolResult?: Record<string, unknown> | null): NonNullable<ChatMessage['references']> {
    if (!toolResult) {
      return []
    }
    const direct = mapHitsOrItems(toolResult)
    if (direct.length) {
      return direct
    }
    const resultObj = asRecord(toolResult.result)
    if (resultObj) {
      const nested = mapHitsOrItems(resultObj)
      if (nested.length) {
        return nested
      }
      const dataObj = asRecord(resultObj.data)
      if (dataObj) {
        return mapHitsOrItems(dataObj)
      }
    }
    return []
  }

  function mapHitsOrItems(source: Record<string, unknown>): NonNullable<ChatMessage['references']> {
    const hits = asArray(source.hits)
    if (hits.length) {
      return hits
        .map((row) => asRecord(row))
        .filter((row): row is Record<string, unknown> => Boolean(row))
        .map((row) => ({
          id: (row.id as string | number | undefined),
          title: (row.title as string | undefined) || (asRecord(row.payload)?.title as string | undefined),
          score: typeof row.score === 'number' ? row.score : undefined,
          payload: asRecord(row.payload) ?? row
        }))
    }

    const items = asArray(source.items)
    if (items.length) {
      return items
        .map((row) => asRecord(row))
        .filter((row): row is Record<string, unknown> => Boolean(row))
        .map((row) => ({
          id: (row.id as string | number | undefined),
          title: (row.fileName as string | undefined) || (row.title as string | undefined) || '附件上下文',
          score: 1,
          payload: row
        }))
    }

    return []
  }

  function asRecord(value: unknown): Record<string, unknown> | null {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return null
    }
    return value as Record<string, unknown>
  }

  function asArray(value: unknown): unknown[] {
    return Array.isArray(value) ? value : []
  }

  function handleStreamFailure(message: string) {
    if (streamFailureHandled.value) {
      return
    }
    streamFailureHandled.value = true
    streaming.value = false
    const reason = message || '流式调用失败'
    lastError.value = reason
    markLatestUserRoundFailed(reason)
    const partial = pendingText.value.trim()
    const failedContent = partial
      ? `失败回合（已收到部分响应）：\n${partial}\n\n失败原因：${reason}`
      : `失败回合：${reason}`
    messages.value.push({ role: 'assistant', content: failedContent, createdAt: nowIso(), failed: true })
    pendingText.value = ''
    pendingCreatedAt.value = ''
    pendingUsage.value = undefined
    pendingReferences.value = undefined
    void loadSessions()
  }

  function markLatestUserRoundFailed(reason: string) {
    if (!reason) {
      return
    }
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const item = messages.value[i]
      if (item.role === 'user') {
        item.failed = true
        return
      }
    }
  }

  function setSkills(nextSkills: string[]) {
    skills.value = nextSkills
  }

  async function upload(file: File, summaryModel?: string) {
    if (!sessionId.value) {
      throw new Error('请先选择会话，再上传附件')
    }
    return uploadAttachment(file, sessionId.value || undefined, summaryModel)
  }

  async function ingestKnowledge(payload: KnowledgeIndexRequest) {
    return indexKnowledge(payload)
  }

  async function ingestKnowledgeFile(payload: { file: File; title?: string; chunkSize?: number; chunkOverlap?: number; embeddingModel?: string }) {
    return indexKnowledgeFile(payload)
  }

  function clearMessages() {
    messages.value = []
    pendingText.value = ''
    pendingCreatedAt.value = ''
    pendingUsage.value = undefined
    pendingReferences.value = undefined
    lastError.value = ''
    streamFailureHandled.value = false
    sessionId.value = ''
  }

  async function startNewSession(title = '', model = 'qwen-plus') {
    const result = await createSession(title, model)
    const createdSessionId = String(result.sessionId || '')
    sessionTitle.value = title
    sessionModel.value = model || 'qwen-plus'
    clearMessages()
    sessionId.value = createdSessionId
    await loadSessions()
  }

  async function removeSession(targetSessionId: string) {
    if (!targetSessionId) {
      return
    }
    await deleteSession(targetSessionId)
    if (sessionId.value === targetSessionId) {
      clearMessages()
    }
    await loadSessions()
  }

  async function renameSession(targetSessionId: string, title: string, model?: string) {
    if (!targetSessionId || !title.trim()) {
      return
    }
    await updateSessionTitle(targetSessionId, title.trim(), model?.trim() || undefined)
    await loadSessions()
  }

  return {
    sessionId,
    skills,
    messages,
    sessions,
    streaming,
    pendingText,
    pendingCreatedAt,
    pendingUsage,
    lastError,
    activeToolCalls,
    sessionTitle,
    sessionModel,
    sendSync,
    sendStream,
    loadSessions,
    loadMessages,
    consumeEvent,
    setSkills,
    upload,
    ingestKnowledge,
    ingestKnowledgeFile,
    clearMessages,
    startNewSession,
    removeSession,
    renameSession
  }
})
