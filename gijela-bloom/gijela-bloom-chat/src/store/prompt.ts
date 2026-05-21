import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  fetchSessionPrompt,
  fetchSystemPromptCurrent,
  publishSystemPrompt,
  rollbackSystemPrompt,
  saveSystemPromptDraft,
  updateSessionPrompt,
  type SessionPromptResponse,
  type SystemPromptCurrent
} from '@/api/prompt'

export const usePromptStore = defineStore('prompt', () => {
  const systemCurrent = ref<SystemPromptCurrent | null>(null)
  const sessionPrompt = ref<SessionPromptResponse | null>(null)
  const loading = ref(false)

  async function loadSystemPrompt(appCode = 'chat', modelRoute = 'default') {
    loading.value = true
    try {
      systemCurrent.value = await fetchSystemPromptCurrent(appCode, modelRoute)
    } finally {
      loading.value = false
    }
  }

  async function loadSessionPrompt(sessionId: string) {
    loading.value = true
    try {
      sessionPrompt.value = await fetchSessionPrompt(sessionId)
      return sessionPrompt.value
    } finally {
      loading.value = false
    }
  }

  async function saveDraft(payload: { appCode: string; modelRoute: string; draftContent: string; draftVersion: number }) {
    systemCurrent.value = await saveSystemPromptDraft(payload)
    return systemCurrent.value
  }

  async function publish(payload: { appCode: string; modelRoute: string; draftVersion: number; publishNote?: string }) {
    systemCurrent.value = await publishSystemPrompt(payload)
    return systemCurrent.value
  }

  async function rollback(payload: { appCode: string; modelRoute: string; targetPublishedVersion: number }) {
    systemCurrent.value = await rollbackSystemPrompt(payload)
    return systemCurrent.value
  }

  async function saveSessionPrompt(sessionId: string, payload: { content: string; version: number }) {
    sessionPrompt.value = await updateSessionPrompt(sessionId, payload)
    return sessionPrompt.value
  }

  return {
    loading,
    systemCurrent,
    sessionPrompt,
    loadSystemPrompt,
    loadSessionPrompt,
    saveDraft,
    publish,
    rollback,
    saveSessionPrompt
  }
})
