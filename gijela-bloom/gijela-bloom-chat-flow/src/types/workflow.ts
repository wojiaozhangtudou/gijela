export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface WorkflowSummary {
  workflowId: string
  code: string
  name: string
  appType: string
  status: string
  draftVersion: number
  publishedVersion?: number
  updatedAt?: string
}

export interface WorkflowDetail extends WorkflowSummary {
  description?: string
  draft?: {
    workflowId: string
    version: number
    definition: Record<string, unknown>
  }
}

export interface WorkflowRunSummary {
  runId: string
  workflowId: string
  workflowVersion: number
  runType: string
  triggerBy?: string
  status: string
  startedAt?: string
  endedAt?: string
  durationMs?: number
}

export interface WorkflowRunPage {
  pageNum: number
  pageSize: number
  total: number
  list: WorkflowRunSummary[]
}

export interface WorkflowDraftSaveResult {
  workflowId: string
  draftVersion: number
  savedAt: string
}

export interface WorkflowPublishResult {
  workflowId: string
  publishedVersion: number
  status: string
  publishedAt: string
}

export interface WorkflowValidateResult {
  workflowId: string
  version: number
  valid: boolean
  issues: string[]
}

export interface WorkflowRunDetail {
  runId: string
  workflowId: string
  workflowVersion: number
  runType?: string
  status: string
  startedAt?: string
  endedAt?: string
  durationMs?: number
  finalResult?: Record<string, unknown>
  nodes: Array<{
    nodeId: string
    nodeType: string
    status: string
    durationMs?: number
    inputSnapshot?: Record<string, unknown>
    outputSnapshot?: Record<string, unknown>
    errorMessage?: string
  }>
}

export interface LlmModelItem {
  id: number
  modelKey: string
  displayName: string
  provider: string
  baseUrl: string
  targetModel: string
  enabled: number
  defaultTemperature?: number
  defaultMaxTokens?: number
  remark?: string
  updatedAt?: string
}

export interface LlmModelSavePayload {
  modelKey: string
  displayName: string
  provider: string
  baseUrl: string
  apiKey: string
  targetModel: string
  enabled: number
  defaultTemperature?: number
  defaultMaxTokens?: number
  remark?: string
}
