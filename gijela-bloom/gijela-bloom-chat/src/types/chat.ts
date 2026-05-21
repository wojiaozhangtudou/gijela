export interface ChatMessage {
  role: 'system' | 'user' | 'assistant' | 'tool'
  content: string
  createdAt?: string
  failed?: boolean
  usage?: ChatUsage | null
  references?: Array<{
    id?: string | number
    title?: string
    score?: number
    payload?: Record<string, unknown>
  }>
}

export interface ChatCompletionRequest {
  sessionId?: string
  messages: ChatMessage[]
  model?: string
  temperature?: number
  maxTokens?: number
  skills?: string[]
}

export interface KnowledgeIndexRequest {
  title?: string
  content: string
  chunkSize?: number
  chunkOverlap?: number
  embeddingModel?: string
}

export interface KnowledgeSearchResult {
  collection?: string
  tenantId?: string
  query?: string
  provider?: string
  hits?: Array<{
    id?: string | number
    title?: string
    score?: number
    payload?: Record<string, unknown>
  }>
}

export interface EmbeddingDimensionResult {
  dimension?: number
  model?: string
  probeText?: string
}

export interface KnowledgeCollectionsResult {
  collections?: string[]
  count?: number
  provider?: string
}

export interface KnowledgeVectorMutationResult {
  collection?: string
  status?: string
  deleted?: number
  provider?: string
}

export interface ChatUsage {
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
}

export interface ChatCompletionResponse {
  sessionId: string
  content: string
  finishReason: string
  usage?: ChatUsage | null
  references?: Array<{
    id?: string | number
    title?: string
    score?: number
    payload?: Record<string, unknown>
  }> | null
}

export interface ChatSessionItem {
  sessionId: string
  title?: string | null
  summary?: string | null
  model?: string | null
  messageCount?: number | null
  updatedAt?: string | null
}

export interface ChatHistoryMessage {
  role: string
  content: string
  createdAt?: string | null
  references?: Array<{
    id?: string | number
    title?: string
    score?: number
    payload?: Record<string, unknown>
  }> | null
}

export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
  timestamp: number
  traceId?: string
}

export interface ChatEvent {
  type: 'start' | 'delta' | 'tool_call' | 'tool_result' | 'error' | 'done'
  sessionId: string
  content?: string | null
  toolCall?: Record<string, unknown> | null
  toolResult?: Record<string, unknown> | null
  error?: string | null
  usage?: ChatUsage | null
  references?: Array<{
    id?: string | number
    title?: string
    score?: number
    payload?: Record<string, unknown>
  }> | null
}

export interface StorageBucketItem {
  name: string
}

export interface StorageObjectItem {
  bucket?: string
  key: string
  size?: number | null
  lastModified?: string | null
}

export interface ChatAttachmentItem {
  id: number
  sessionId?: string | null
  fileName?: string | null
  objectKey?: string | null
  fileSize?: number | null
  contentType?: string | null
  provider?: string | null
  summary?: string | null
  processStatus?: number | null
  createdAt?: string | null
}

export interface ChatAttachmentDetail extends ChatAttachmentItem {
  tenantId?: string | null
  rawText?: string | null
  condensedMd?: string | null
}

export interface ModelConfigItem {
  id: number
  configType: 'CHAT' | 'EMBEDDING'
  providerKey: string
  model: string
  baseUrl: string
  apiKey?: string
  connectTimeoutSeconds: number
  readTimeoutSeconds: number
  callTimeoutSeconds: number
  enabled: boolean
  updatedAt?: string
  updatedBy?: string
}

export interface ModelConfigOptionItem {
  configType: 'CHAT' | 'EMBEDDING'
  providerKey: string
  model: string
  label: string
}

export interface ModelConfigSaveRequest {
  configType: 'CHAT' | 'EMBEDDING'
  providerKey: string
  model: string
  baseUrl: string
  apiKey?: string
  connectTimeoutSeconds: number
  readTimeoutSeconds: number
  callTimeoutSeconds: number
  enabled: boolean
}
