import client from '@/api/client'
import type { ApiResponse } from '@/types/chat'

function unwrapResponse<T>(response: ApiResponse<T>): T {
  if (response.code === 0) {
    return response.data
  }
  const error = new Error(response.msg || '请求失败') as Error & {
    response?: { data?: ApiResponse<T> }
  }
  error.response = { data: response }
  throw error
}

export interface SystemPromptCurrent {
  tenantId?: string
  appCode?: string
  modelRoute?: string
  draftContent?: string
  draftVersion?: number
  publishedContent?: string
  publishedVersion?: number
  publishedAt?: string
}

export interface SessionPromptResponse {
  sessionId: string
  content?: string
  version?: number
  updatedAt?: string
}

export interface PromptItemResponse {
  id: number
  scopeType: 'system' | 'session'
  appCode?: string
  modelRoute?: string
  sessionId?: string
  promptName: string
  content: string
  priority: number
  enabled: boolean
  version: number
  updatedAt?: string
}

export async function fetchSystemPromptCurrent(appCode = 'chat', modelRoute = 'default') {
  const response = await client.get<ApiResponse<SystemPromptCurrent>>('/v1/chat/prompts/system/current', {
    params: { appCode, modelRoute }
  })
  return unwrapResponse(response.data)
}

export async function fetchSessionPrompt(sessionId: string) {
  const response = await client.get<ApiResponse<SessionPromptResponse>>(`/v1/chat/sessions/${encodeURIComponent(sessionId)}/prompt`)
  return unwrapResponse(response.data)
}

export async function saveSystemPromptDraft(payload: {
  appCode: string
  modelRoute: string
  draftContent: string
  draftVersion: number
}) {
  const response = await client.put<ApiResponse<SystemPromptCurrent>>('/v1/chat/prompts/system/draft', payload)
  return unwrapResponse(response.data)
}

export async function publishSystemPrompt(payload: {
  appCode: string
  modelRoute: string
  draftVersion: number
  publishNote?: string
}) {
  const response = await client.post<ApiResponse<SystemPromptCurrent>>('/v1/chat/prompts/system/publish', payload)
  return unwrapResponse(response.data)
}

export async function rollbackSystemPrompt(payload: {
  appCode: string
  modelRoute: string
  targetPublishedVersion: number
}) {
  const response = await client.post<ApiResponse<SystemPromptCurrent>>('/v1/chat/prompts/system/rollback', payload)
  return unwrapResponse(response.data)
}

export async function updateSessionPrompt(sessionId: string, payload: { content: string; version: number }) {
  const response = await client.put<ApiResponse<SessionPromptResponse>>(`/v1/chat/sessions/${encodeURIComponent(sessionId)}/prompt`, payload)
  return unwrapResponse(response.data)
}

export async function listSystemPromptItems(appCode = 'chat', modelRoute = 'default') {
  const response = await client.get<ApiResponse<PromptItemResponse[]>>('/v1/chat/prompt-items/system', {
    params: { appCode, modelRoute }
  })
  return unwrapResponse(response.data) || []
}

export async function createSystemPromptItem(payload: {
  appCode: string
  modelRoute: string
  promptName: string
  content: string
  priority: number
  enabled: boolean
}) {
  const response = await client.post<ApiResponse<PromptItemResponse>>('/v1/chat/prompt-items/system', payload)
  return unwrapResponse(response.data)
}

export async function updateSystemPromptItem(id: number, payload: {
  content: string
  priority: number
  enabled: boolean
}) {
  const response = await client.put<ApiResponse<PromptItemResponse>>(`/v1/chat/prompt-items/system/${id}`, payload)
  return unwrapResponse(response.data)
}

export async function deleteSystemPromptItem(id: number) {
  const response = await client.delete<ApiResponse<null>>(`/v1/chat/prompt-items/system/${id}`)
  return unwrapResponse(response.data)
}

export async function listSessionPromptItems(sessionId: string) {
  const response = await client.get<ApiResponse<PromptItemResponse[]>>(`/v1/chat/prompt-items/sessions/${encodeURIComponent(sessionId)}`)
  return unwrapResponse(response.data) || []
}

export async function createSessionPromptItem(sessionId: string, payload: {
  promptName: string
  content: string
  priority: number
  enabled: boolean
}) {
  const response = await client.post<ApiResponse<PromptItemResponse>>(`/v1/chat/prompt-items/sessions/${encodeURIComponent(sessionId)}`, payload)
  return unwrapResponse(response.data)
}

export async function updateSessionPromptItem(sessionId: string, id: number, payload: {
  content: string
  priority: number
  enabled: boolean
}) {
  const response = await client.put<ApiResponse<PromptItemResponse>>(`/v1/chat/prompt-items/sessions/${encodeURIComponent(sessionId)}/${id}`, payload)
  return unwrapResponse(response.data)
}

export async function deleteSessionPromptItem(sessionId: string, id: number) {
  const response = await client.delete<ApiResponse<null>>(`/v1/chat/prompt-items/sessions/${encodeURIComponent(sessionId)}/${id}`)
  return unwrapResponse(response.data)
}
