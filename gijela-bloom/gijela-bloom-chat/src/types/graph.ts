export interface GraphExtractTextRequest {
  graphSpace?: string
  title?: string
  inputText: string
  llmModel?: string
  extractMode?: string
  importMode?: string
  promptOverride?: string
  maxTokens?: number
}

export interface GraphExtractEntity {
  entityName: string
  normalizedName: string
  entityType?: string
  entityDescription?: string
}

export interface GraphExtractRelationship {
  sourceEntity: string
  targetEntity: string
  relationshipDescription?: string
  relationshipStrength?: number
  relationshipId?: string
}

export interface GraphExtractStats {
  entityCount?: number
  relationshipCount?: number
}

export interface GraphExtractPreviewResponse {
  previewId: string
  graphSpace: string
  title?: string
  sourceType?: string
  extractMode?: string
  importMode?: string
  status?: string
  entities?: GraphExtractEntity[]
  relationships?: GraphExtractRelationship[]
  warnings?: string[]
  stats?: GraphExtractStats
}

export interface GraphImportRequest {
  previewId: string
  graphSpace?: string
  importMode?: string
}

export interface GraphImportResultResponse {
  previewId: string
  graphSpace: string
  importBatchId: string
  importMode?: string
  importedEntities?: number
  importedRelationships?: number
  status?: string
}

export interface GraphEntityPageRequest {
  graphSpace?: string
  page?: number
  pageSize?: number
  keyword?: string
  entityType?: string
}

export interface GraphEntityTypeListRequest {
  graphSpace?: string
}

export interface GraphEntityTypeListResponse {
  graphSpace: string
  count?: number
  items?: string[]
}

export interface CreateGraphSpaceRequest {
  graphSpace: string
}

export interface GraphSpaceItem {
  graphSpace: string
  entityCount?: number
  relationshipCount?: number
}

export interface GraphSpaceListResponse {
  defaultSpace?: string
  count?: number
  items?: GraphSpaceItem[]
}

export interface GraphRelationshipPageRequest {
  graphSpace?: string
  page?: number
  pageSize?: number
  keyword?: string
  entityType?: string
}

export interface GraphRelationshipItem {
  relationshipId: string
  sourceEntity: string
  targetEntity: string
  relationshipDescription?: string
  relationshipStrength?: number
  graphSpace?: string
  updatedAt?: string
}

export interface GraphRelationshipPageResponse {
  graphSpace: string
  page?: number
  pageSize?: number
  total?: number
  items?: GraphRelationshipItem[]
}

export interface GraphOneHopQueryRequest {
  graphSpace?: string
  centerEntity: string
  limitNodes?: number
  limitEdges?: number
}

export interface GraphOneHopQueryResponse {
  graphSpace: string
  centerEntity: string
  nodeCount?: number
  edgeCount?: number
  nodes?: GraphEntityItem[]
  edges?: GraphRelationshipItem[]
}

export interface GraphMutationResultResponse {
  graphSpace: string
  action?: string
  affected?: number
}

export interface GraphDeleteEntitiesRequest {
  graphSpace?: string
  normalizedNames: string[]
}

export interface GraphDeleteRelationshipsRequest {
  graphSpace?: string
  relationshipIds: string[]
}

export interface GraphDeleteImportBatchRequest {
  graphSpace?: string
  importBatchId: string
}

export interface GraphEntityItem {
  entityName: string
  normalizedName: string
  entityType?: string
  entityDescription?: string
  graphSpace?: string
  updatedAt?: string
}

export interface GraphEntityPageResponse {
  graphSpace: string
  page?: number
  pageSize?: number
  total?: number
  items?: GraphEntityItem[]
}