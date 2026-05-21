export interface ChatflowSessionItem {
  sessionId: string
  title: string
  workflowId: string
  status: string
  workflowInputs?: Record<string, unknown>
  createdAt?: string
  updatedAt?: string
}

export interface ChatflowSessionPage {
  pageNum: number
  pageSize: number
  total: number
  list: ChatflowSessionItem[]
}

export interface ChatflowMessageItem {
  messageId: string
  role: 'user' | 'assistant' | string
  content: string
  runId?: string
  createdAt?: string
}

export interface ChatflowMessagePage {
  pageNum: number
  pageSize: number
  total: number
  list: ChatflowMessageItem[]
}

export interface ChatflowCompletionResult {
  sessionId: string
  messageId: string
  answer: string
  workflowId: string
  runId: string
  createdAt?: string
}
