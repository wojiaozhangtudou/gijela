import request from './client'
import type {
  ApiResponse,
  LlmModelItem,
  LlmModelSavePayload,
  WorkflowDetail,
  WorkflowDraftSaveResult,
  WorkflowPublishResult,
  WorkflowRunDetail,
  WorkflowRunPage,
  WorkflowSummary,
  WorkflowValidateResult
} from '../types/workflow'

export async function listWorkflows(params?: { name?: string; status?: string; appType?: string }) {
  const res = await request.get<ApiResponse<WorkflowSummary[]>>('/v1/chat-flow/workflows', { params })
  return res.data.data || []
}

export async function getWorkflowDetail(workflowId: string) {
  const res = await request.get<ApiResponse<WorkflowDetail>>(`/v1/chat-flow/workflows/${workflowId}`)
  return res.data.data
}

export async function updateWorkflow(workflowId: string, payload: { name: string; description?: string }) {
  const res = await request.put<ApiResponse<WorkflowDetail>>(`/v1/chat-flow/workflows/${workflowId}`, payload)
  return res.data.data
}

export async function createWorkflow(payload: { code: string; name: string; appType: 'workflow' | 'chatflow'; description?: string }) {
  const res = await request.post<ApiResponse<{ workflowId: string }>>('/v1/chat-flow/workflows', payload)
  return res.data.data
}

export async function validateWorkflow(workflowId: string) {
  const res = await request.post<ApiResponse<WorkflowValidateResult>>(`/v1/chat-flow/workflows/${workflowId}/validate`)
  return res.data.data
}

export async function getWorkflowDraft(workflowId: string) {
  const res = await request.get<ApiResponse<WorkflowDetail['draft']>>(`/v1/chat-flow/workflows/${workflowId}/draft`)
  return res.data.data
}

export async function saveWorkflowDraft(workflowId: string, payload: { version: number; definition: Record<string, unknown> }) {
  const res = await request.put<ApiResponse<WorkflowDraftSaveResult>>(`/v1/chat-flow/workflows/${workflowId}/draft`, payload)
  return res.data.data
}

export async function publishWorkflow(workflowId: string) {
  const res = await request.post<ApiResponse<WorkflowPublishResult>>(`/v1/chat-flow/workflows/${workflowId}/publish`)
  return res.data.data
}

export async function startDebugRun(workflowId: string, payload: { version: number; inputs?: Record<string, unknown> }) {
  const res = await request.post<ApiResponse<{ runId: string }>>(`/v1/chat-flow/workflows/${workflowId}/debug-runs`, payload)
  return res.data.data
}

export async function startRun(workflowId: string, payload: { version: number; inputs?: Record<string, unknown> }) {
  const res = await request.post<ApiResponse<{ runId: string }>>(`/v1/chat-flow/workflows/${workflowId}/runs`, payload)
  return res.data.data
}

export async function listRuns(workflowId: string, params?: { runType?: string; status?: string; triggerBy?: string; pageNum?: number; pageSize?: number }) {
  const res = await request.get<ApiResponse<WorkflowRunPage>>(`/v1/chat-flow/workflows/${workflowId}/runs`, { params })
  return res.data.data
}

export async function getRunDetail(runId: string) {
  const res = await request.get<ApiResponse<WorkflowRunDetail>>(`/v1/chat-flow/runs/${runId}`)
  return res.data.data
}

export async function listLlmModels(params?: { name?: string; enabled?: number }) {
  const res = await request.get<ApiResponse<LlmModelItem[]>>('/v1/chat-flow/llm-models', { params })
  return res.data.data || []
}

export async function createLlmModel(payload: LlmModelSavePayload) {
  const res = await request.post<ApiResponse<LlmModelItem>>('/v1/chat-flow/llm-models', payload)
  return res.data.data
}

export async function updateLlmModel(id: number, payload: LlmModelSavePayload) {
  const res = await request.put<ApiResponse<LlmModelItem>>(`/v1/chat-flow/llm-models/${id}`, payload)
  return res.data.data
}

export async function deleteWorkflow(workflowId: string) {
  const res = await request.delete<ApiResponse<null>>(`/v1/chat-flow/workflows/${workflowId}`)
  return res.data
}

export async function deleteLlmModel(id: number) {
  const res = await request.delete<ApiResponse<null>>(`/v1/chat-flow/llm-models/${id}`)
  return res.data
}
