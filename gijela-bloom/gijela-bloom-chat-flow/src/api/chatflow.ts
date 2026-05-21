import request from './client'
import type { ApiResponse } from '../types/workflow'
import type {
  ChatflowCompletionResult,
  ChatflowMessagePage,
  ChatflowSessionItem,
  ChatflowSessionPage
} from '../types/chatflow'

export async function listChatflowSessions(params?: { keyword?: string; pageNum?: number; pageSize?: number }) {
  const res = await request.get<ApiResponse<ChatflowSessionPage>>('/v1/chat-flow/chat/sessions', { params })
  return res.data.data
}

export async function createChatflowSession(payload: {
  title: string
  workflowId: string
  workflowInputs?: Record<string, unknown>
}) {
  const res = await request.post<ApiResponse<ChatflowSessionItem>>('/v1/chat-flow/chat/sessions', payload)
  return res.data.data
}

export async function updateChatflowSession(
  sessionId: string,
  payload: { title: string; workflowId: string; workflowInputs?: Record<string, unknown> }
) {
  const res = await request.put<ApiResponse<ChatflowSessionItem>>(`/v1/chat-flow/chat/sessions/${sessionId}`, payload)
  return res.data.data
}

export async function deleteChatflowSession(sessionId: string) {
  const res = await request.delete<ApiResponse<null>>(`/v1/chat-flow/chat/sessions/${sessionId}`)
  return res.data
}

export async function listChatflowMessages(sessionId: string, params?: { pageNum?: number; pageSize?: number }) {
  const res = await request.get<ApiResponse<ChatflowMessagePage>>(`/v1/chat-flow/chat/sessions/${sessionId}/messages`, {
    params
  })
  return res.data.data
}

export async function chatflowCompletion(payload: { sessionId: string; content: string; requestId?: string }) {
  const res = await request.post<ApiResponse<ChatflowCompletionResult>>('/v1/chat-flow/chat/completions', payload, {
    timeout: 120000  // LLM 调用可能耗时较长，单独设置 120s
  })
  return res.data.data
}
