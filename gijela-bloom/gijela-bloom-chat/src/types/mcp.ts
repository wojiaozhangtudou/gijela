export type McpTransport = 'streamable_http' | 'sse' | 'stdio'
export type McpAuthType = 'none' | 'bearer'
export type McpStatus = 'unknown' | 'ok' | 'error'

export interface McpServerSummary {
  id: number
  name: string
  displayName: string
  description: string | null
  transport: McpTransport
  endpoint: string | null
  command: string | null
  /** 列表统一展示用：streamable_http/sse → endpoint；stdio → command */
  endpointOrCommand: string | null
  authType: McpAuthType
  hasToken: boolean
  enabled: boolean
  status: McpStatus
  statusMessage: string | null
  toolCount: number | null
  lastTestedAt: string | null
  updatedAt: string | null
}

export interface McpServerDetail
  extends Omit<McpServerSummary, 'hasToken' | 'toolCount' | 'endpointOrCommand'> {
  args: string[]
  env: Record<string, string>
  workingDir: string | null
  authTokenMask: string | null
  tools: Array<Record<string, any>>
  createdAt: string | null
}

export interface McpServerSaveRequest {
  name: string
  displayName: string
  description?: string | null
  transport: McpTransport
  /** streamable_http / sse 必填，stdio 忽略 */
  endpoint?: string | null
  /** stdio 必填，其他 transport 忽略 */
  command?: string | null
  args?: string[] | null
  env?: Record<string, string> | null
  workingDir?: string | null
  authType: McpAuthType
  /** null = 编辑时不变；空串 = 清空；其它 = 覆盖 */
  authToken?: string | null
  enabled?: boolean
}

export interface McpTestResult {
  ok: boolean
  message?: string
  serverInfo?: { name?: string; version?: string; protocolVersion?: string }
  capabilities?: Record<string, any>
}
