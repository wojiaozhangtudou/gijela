import client from '@/api/client'
import type { ApiResponse } from '@/types/chat'
import type {
  CreateGraphSpaceRequest,
  GraphDeleteEntitiesRequest,
  GraphDeleteImportBatchRequest,
  GraphDeleteRelationshipsRequest,
  GraphEntityPageRequest,
  GraphEntityPageResponse,
  GraphEntityTypeListRequest,
  GraphEntityTypeListResponse,
  GraphExtractPreviewResponse,
  GraphExtractTextRequest,
  GraphImportRequest,
  GraphImportResultResponse,
  GraphMutationResultResponse,
  GraphOneHopQueryRequest,
  GraphOneHopQueryResponse,
  GraphRelationshipPageRequest,
  GraphRelationshipPageResponse,
  GraphSpaceItem,
  GraphSpaceListResponse
} from '@/types/graph'

export async function listGraphSpaces() {
  const response = await client.get<ApiResponse<GraphSpaceListResponse>>('/v1/chat/graph/spaces')
  return response.data.data
}

export async function createGraphSpace(payload: CreateGraphSpaceRequest) {
  const response = await client.post<ApiResponse<GraphSpaceItem>>('/v1/chat/graph/spaces', payload)
  return response.data.data
}

export async function deleteGraphSpace(graphSpace: string) {
  const response = await client.delete<ApiResponse<GraphMutationResultResponse>>(`/v1/chat/graph/spaces/${encodeURIComponent(graphSpace)}`)
  return response.data.data
}

export async function extractGraphText(payload: GraphExtractTextRequest) {
  const response = await client.post<ApiResponse<GraphExtractPreviewResponse>>('/v1/chat/graph/extract/text', payload)
  return response.data.data
}

export async function extractGraphFile(payload: {
  file: File
  graphSpace?: string
  title?: string
  llmModel?: string
  extractMode?: string
  importMode?: string
  promptOverride?: string
  maxTokens?: number
}) {
  const formData = new FormData()
  formData.append('file', payload.file)
  if (payload.graphSpace) {
    formData.append('graphSpace', payload.graphSpace)
  }
  if (payload.title) {
    formData.append('title', payload.title)
  }
  if (payload.llmModel) {
    formData.append('llmModel', payload.llmModel)
  }
  if (payload.extractMode) {
    formData.append('extractMode', payload.extractMode)
  }
  if (payload.importMode) {
    formData.append('importMode', payload.importMode)
  }
  if (payload.promptOverride) {
    formData.append('promptOverride', payload.promptOverride)
  }
  if (typeof payload.maxTokens === 'number') {
    formData.append('maxTokens', String(payload.maxTokens))
  }
  const response = await client.post<ApiResponse<GraphExtractPreviewResponse>>('/v1/chat/graph/extract/file', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}

export async function importGraphPreview(payload: GraphImportRequest, idempotencyKey: string) {
  const response = await client.post<ApiResponse<GraphImportResultResponse>>('/v1/chat/graph/import', payload, {
    headers: {
      'Idempotency-Key': idempotencyKey
    }
  })
  return response.data.data
}

export async function pageGraphEntities(payload: GraphEntityPageRequest) {
  const response = await client.post<ApiResponse<GraphEntityPageResponse>>('/v1/chat/graph/entities/page', payload)
  return response.data.data
}

export async function listGraphEntityTypes(payload: GraphEntityTypeListRequest) {
  const response = await client.post<ApiResponse<GraphEntityTypeListResponse>>('/v1/chat/graph/entities/types', payload)
  return response.data.data
}

export async function pageGraphRelationships(payload: GraphRelationshipPageRequest) {
  const response = await client.post<ApiResponse<GraphRelationshipPageResponse>>('/v1/chat/graph/relationships/page', payload)
  return response.data.data
}

export async function queryGraphOneHop(payload: GraphOneHopQueryRequest) {
  const response = await client.post<ApiResponse<GraphOneHopQueryResponse>>('/v1/chat/graph/view/one-hop', payload)
  return response.data.data
}

export async function deleteGraphEntities(payload: GraphDeleteEntitiesRequest) {
  const response = await client.post<ApiResponse<GraphMutationResultResponse>>('/v1/chat/graph/entities/delete', payload)
  return response.data.data
}

export async function deleteGraphRelationships(payload: GraphDeleteRelationshipsRequest) {
  const response = await client.post<ApiResponse<GraphMutationResultResponse>>('/v1/chat/graph/relationships/delete', payload)
  return response.data.data
}

export async function deleteGraphImportBatch(payload: GraphDeleteImportBatchRequest) {
  const response = await client.post<ApiResponse<GraphMutationResultResponse>>('/v1/chat/graph/import-batches/delete', payload)
  return response.data.data
}