export type RagBook = 'SAN_GUO' | 'SHUI_HU' | 'HONG_LOU' | 'XI_YOU_JI'

export type RagQueryType = 'COUNT' | 'QA'

export type RagIndexJobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export type RagAliasReviewAction = 'MERGE' | 'REJECT'

export interface RagApiResponse<T> {
  code: number
  message: string
  traceId: string
  data: T
}

export interface RagQueryRequest {
  queryText: string
  queryType: RagQueryType
  book: RagBook
  maxHops: number
  aliasMerge?: boolean
  needEvidence?: boolean
  timeoutMs?: number
}

export interface RagEvidence {
  book: RagBook
  chapter: string
  position: string
  snippet: string
}

export interface RagCountResult {
  canonicalName: string
  aliases: string[]
  count: number
}

export interface RagQueryResponseData {
  answerText: string
  answerType: RagQueryType
  confidence: number
  countResult: RagCountResult | null
  evidences: RagEvidence[]
  degraded: boolean
  degradeReason?: string | null
}

export interface RagIndexRebuildRequest {
  bizDate: string
  books: RagBook[]
  force?: boolean
  operator: string
}

export interface RagIndexRebuildResponseData {
  jobId: string
  status: RagIndexJobStatus
  nextRetryAt?: string | null
}

export interface RagIndexStatusStats {
  chunks: number
  entities: number
  relations: number
  aliasMerged: number
}

export interface RagIndexStatusResponseData {
  jobId: string
  bizDate: string
  status: RagIndexJobStatus
  retryCount: number
  startedAt: string
  finishedAt?: string | null
  stats?: RagIndexStatusStats | null
}

export interface RagAliasReviewRequest {
  reviewId: string
  canonicalName: string
  alias: string
  action: RagAliasReviewAction
  reason: string
  reviewer: string
}

export interface RagAliasReviewResponseData {
  reviewId: string
  status: 'DONE'
  appliedAt: string
}
