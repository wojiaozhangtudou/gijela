import client from '@/api/client'
import { buildCommonHeaders } from '@/utils/auth'
import type {
  ApiResponse,
  ChatCompletionRequest,
  ChatCompletionResponse,
  ChatEvent,
  ChatHistoryMessage,
  ChatSessionItem,
  KnowledgeIndexRequest,
  KnowledgeSearchResult,
  EmbeddingDimensionResult,
  KnowledgeCollectionsResult,
  KnowledgeVectorMutationResult,
  ChatAttachmentDetail,
  ChatAttachmentItem,
  StorageBucketItem,
  StorageObjectItem,
  ModelConfigItem,
  ModelConfigOptionItem,
  ModelConfigSaveRequest
} from '@/types/chat'

export async function completeChat(payload: ChatCompletionRequest) {
  const response = await client.post<ApiResponse<ChatCompletionResponse>>('/v1/chat/completions', payload)
  return response.data.data
}

export async function uploadAttachment(file: File, sessionId?: string, summaryModel?: string) {
  const formData = new FormData()
  formData.append('file', file)
  if (sessionId) {
    formData.append('sessionId', sessionId)
  }
  if (summaryModel) {
    formData.append('summaryModel', summaryModel)
  }
  const response = await client.post<ApiResponse<Record<string, unknown>>>('/v1/chat/attachments', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}

export async function listSessionAttachments(payload: {
  sessionId: string
  page?: number
  pageSize?: number
  status?: number
  fileNameLike?: string
  startTime?: string
  endTime?: string
}) {
  const response = await client.get<ApiResponse<{ sessionId?: string; page?: number; pageSize?: number; total?: number; count?: number; items?: ChatAttachmentItem[] }>>('/v1/chat/attachments', {
    params: {
      sessionId: payload.sessionId,
      page: payload.page,
      pageSize: payload.pageSize,
      status: payload.status,
      fileNameLike: payload.fileNameLike,
      startTime: payload.startTime,
      endTime: payload.endTime
    }
  })
  return response.data.data
}

export async function fetchAttachmentDetail(attachmentId: number) {
  const response = await client.get<ApiResponse<ChatAttachmentDetail>>(`/v1/chat/attachments/${attachmentId}`)
  return response.data.data
}

export async function renameAttachment(attachmentId: number, fileName: string) {
  const response = await client.post<ApiResponse<Record<string, unknown>>>(`/v1/chat/attachments/${attachmentId}/rename`, null, {
    params: { fileName }
  })
  return response.data.data
}

export async function deleteAttachment(attachmentId: number) {
  const response = await client.delete<ApiResponse<Record<string, unknown>>>(`/v1/chat/attachments/${attachmentId}`)
  return response.data.data
}

export async function deleteAttachmentsBatch(ids: number[]) {
  const response = await client.post<ApiResponse<Record<string, unknown>>>('/v1/chat/attachments/delete-batch', {
    ids
  })
  return response.data.data
}

export async function downloadAttachment(attachmentId: number) {
  const response = await client.get<Blob>(`/v1/chat/attachments/${attachmentId}/download`, {
    responseType: 'blob'
  })
  return response
}

export async function fetchSessions(limit = 20) {
  const response = await client.get<ApiResponse<ChatSessionItem[]>>('/v1/chat/sessions', {
    params: { limit }
  })
  return response.data.data || []
}

export async function createSession(title: string, model?: string) {
  const response = await client.post<ApiResponse<Record<string, unknown>>>('/v1/chat/sessions', {
    title,
    model
  })
  return response.data.data
}

export async function fetchSessionMessages(sessionId: string, limit = 100) {
  const response = await client.get<ApiResponse<ChatHistoryMessage[]>>(`/v1/chat/sessions/${encodeURIComponent(sessionId)}/messages`, {
    params: { limit }
  })
  return response.data.data || []
}

export async function deleteSession(sessionId: string) {
  const response = await client.delete<ApiResponse<Record<string, unknown>>>(`/v1/chat/sessions/${encodeURIComponent(sessionId)}`)
  return response.data.data
}

export async function updateSessionTitle(sessionId: string, title: string, model?: string) {
  const response = await client.post<ApiResponse<Record<string, unknown>>>(`/v1/chat/sessions/${encodeURIComponent(sessionId)}/title`, {
    title,
    model
  })
  return response.data.data
}

export async function indexKnowledge(payload: KnowledgeIndexRequest) {
  const response = await client.post<ApiResponse<Record<string, unknown>>>('/v1/chat/knowledge/index', payload)
  return response.data.data
}

export async function indexKnowledgeFile(payload: {
  file: File
  title?: string
  chunkSize?: number
  chunkOverlap?: number
  embeddingModel?: string
}) {
  const formData = new FormData()
  formData.append('file', payload.file)
  if (payload.title) {
    formData.append('title', payload.title)
  }
  if (typeof payload.chunkSize === 'number') {
    formData.append('chunkSize', String(payload.chunkSize))
  }
  if (typeof payload.chunkOverlap === 'number') {
    formData.append('chunkOverlap', String(payload.chunkOverlap))
  }
  if (payload.embeddingModel) {
    formData.append('embeddingModel', payload.embeddingModel)
  }
  const response = await client.post<ApiResponse<Record<string, unknown>>>('/v1/chat/knowledge/index/file', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}

export async function searchKnowledge(query: string) {
  const response = await client.get<ApiResponse<KnowledgeSearchResult>>('/v1/chat/knowledge/search', {
    params: { query }
  })
  return response.data.data
}

export async function initKnowledgeCollection(payload: { vectorSize?: number; distance?: string; embeddingModel?: string }) {
  const params: Record<string, unknown> = {
    distance: payload.distance
  }
  if (typeof payload.vectorSize === 'number' && payload.vectorSize > 0) {
    params.vectorSize = payload.vectorSize
  }
  if (payload.embeddingModel) {
    params.embeddingModel = payload.embeddingModel
  }
  const response = await client.post<ApiResponse<Record<string, unknown>>>('/v1/chat/knowledge/collection/init', null, {
    params
  })
  return response.data.data
}

export async function detectEmbeddingDimension(probeText?: string, embeddingModel?: string) {
  const response = await client.get<ApiResponse<EmbeddingDimensionResult>>('/v1/chat/knowledge/embedding/dimension', {
    params: {
      probeText,
      embeddingModel
    }
  })
  return response.data.data
}

export async function listKnowledgeCollections() {
  const response = await client.get<ApiResponse<KnowledgeCollectionsResult>>('/v1/chat/knowledge/collections')
  return response.data.data
}

export async function deleteKnowledgeCollection(collection: string) {
  const response = await client.delete<ApiResponse<KnowledgeVectorMutationResult>>(
    `/v1/chat/knowledge/collections/${encodeURIComponent(collection)}`
  )
  return response.data.data
}

export async function deleteKnowledgeVector(collection: string, id: string) {
  const response = await client.post<ApiResponse<KnowledgeVectorMutationResult>>('/v1/chat/knowledge/vectors/delete-one', null, {
    params: {
      collection,
      id
    }
  })
  return response.data.data
}

export async function deleteKnowledgeVectors(collection: string, ids: string[]) {
  const response = await client.post<ApiResponse<KnowledgeVectorMutationResult>>('/v1/chat/knowledge/vectors/delete-batch', {
    collection,
    ids
  })
  return response.data.data
}

export async function clearKnowledgeVectors(collection: string) {
  const response = await client.post<ApiResponse<KnowledgeVectorMutationResult>>('/v1/chat/knowledge/vectors/clear', null, {
    params: {
      collection
    }
  })
  return response.data.data
}

export async function listStorageBuckets() {
  const response = await client.get<ApiResponse<{ buckets?: StorageBucketItem[]; count?: number }>>('/v1/chat/storage/buckets')
  return response.data.data
}

export async function createStorageBucket(bucket: string) {
  const response = await client.post<ApiResponse<Record<string, unknown>>>('/v1/chat/storage/buckets', null, {
    params: { bucket }
  })
  return response.data.data
}

export async function deleteStorageBucket(bucket: string) {
  const response = await client.delete<ApiResponse<Record<string, unknown>>>(`/v1/chat/storage/buckets/${encodeURIComponent(bucket)}`)
  return response.data.data
}

export async function listStorageObjects(payload: { bucket: string; prefix?: string; maxKeys?: number }) {
  const response = await client.get<ApiResponse<{ objects?: StorageObjectItem[]; count?: number }>>('/v1/chat/storage/objects', {
    params: {
      bucket: payload.bucket,
      prefix: payload.prefix,
      maxKeys: payload.maxKeys
    }
  })
  return response.data.data
}

export async function deleteStorageObject(bucket: string, objectKey: string) {
  const response = await client.delete<ApiResponse<Record<string, unknown>>>('/v1/chat/storage/objects', {
    params: {
      bucket,
      objectKey
    }
  })
  return response.data.data
}

export async function listModelConfigs(configType?: 'CHAT' | 'EMBEDDING') {
  const response = await client.get<ApiResponse<ModelConfigItem[]>>('/v1/chat/model-configs', {
    params: {
      configType
    }
  })
  return response.data.data || []
}

export async function listModelConfigOptions(configType: 'CHAT' | 'EMBEDDING') {
  const response = await client.get<ApiResponse<ModelConfigOptionItem[]>>('/v1/chat/model-configs/options', {
    params: {
      configType
    }
  })
  return response.data.data || []
}

export async function createModelConfig(payload: ModelConfigSaveRequest) {
  const response = await client.post<ApiResponse<ModelConfigItem>>('/v1/chat/model-configs', payload)
  return response.data.data
}

export async function updateModelConfig(id: number, payload: ModelConfigSaveRequest) {
  const response = await client.put<ApiResponse<ModelConfigItem>>(`/v1/chat/model-configs/${id}`, payload)
  return response.data.data
}

export async function deleteModelConfig(id: number) {
  const response = await client.delete<ApiResponse<null>>(`/v1/chat/model-configs/${id}`)
  return response.data.data
}

export async function testModelConfig(id: number) {
  const response = await client.post<ApiResponse<Record<string, unknown>>>(`/v1/chat/model-configs/${id}/test`)
  return response.data.data
}

export async function uploadStorageObject(payload: { bucket: string; objectKey: string; file: File }) {
  const formData = new FormData()
  formData.append('bucket', payload.bucket)
  formData.append('objectKey', payload.objectKey)
  formData.append('file', payload.file)
  const response = await client.post<ApiResponse<Record<string, unknown>>>('/v1/chat/storage/objects/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}

export async function downloadStorageObject(bucket: string, objectKey: string) {
  const response = await client.get<Blob>('/v1/chat/storage/objects/download', {
    params: {
      bucket,
      objectKey
    },
    responseType: 'blob'
  })
  return response
}

export async function streamChat(
  payload: ChatCompletionRequest,
  handlers: {
    onEvent: (event: ChatEvent) => void
    onError: (message: string) => void
    onDone?: () => void
  }
) {
  const response = await fetch('/api/v1/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildCommonHeaders()
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok || !response.body) {
    handlers.onError(`流式请求失败: ${response.status}`)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      handlers.onDone?.()
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() ?? ''

    for (const chunk of chunks) {
      const lines = chunk.split('\n')
      const nameLine = lines.find((line) => line.startsWith('event:'))
      const dataLine = lines.find((line) => line.startsWith('data:'))
      if (!dataLine) {
        continue
      }
      const type = nameLine?.replace('event:', '').trim() ?? 'delta'
      try {
        const payload = JSON.parse(dataLine.replace('data:', '').trim()) as ChatEvent
        handlers.onEvent({ ...payload, type: payload.type ?? (type as ChatEvent['type']) })
      } catch (error) {
        handlers.onError(error instanceof Error ? error.message : '流式事件解析失败')
      }
    }
  }
}
