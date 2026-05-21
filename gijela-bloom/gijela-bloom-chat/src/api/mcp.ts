import client from './client'
import type { ApiResponse } from '@/types/chat'
import type {
  McpServerDetail,
  McpServerSaveRequest,
  McpServerSummary,
  McpTestResult
} from '@/types/mcp'

const BASE = '/v1/chat/admin/mcp-servers'

export async function listMcpServers(): Promise<McpServerSummary[]> {
  const r = await client.get<ApiResponse<McpServerSummary[]>>(BASE)
  return r.data?.data || []
}

export async function getMcpServerDetail(name: string): Promise<McpServerDetail | null> {
  const r = await client.get<ApiResponse<McpServerDetail>>(`${BASE}/${encodeURIComponent(name)}`)
  return r.data?.data || null
}

export async function createMcpServer(body: McpServerSaveRequest): Promise<McpServerDetail | null> {
  const r = await client.post<ApiResponse<McpServerDetail>>(BASE, body)
  return r.data?.data || null
}

export async function updateMcpServer(
  name: string,
  body: McpServerSaveRequest
): Promise<McpServerDetail | null> {
  const r = await client.put<ApiResponse<McpServerDetail>>(`${BASE}/${encodeURIComponent(name)}`, body)
  return r.data?.data || null
}

export async function deleteMcpServer(name: string): Promise<boolean> {
  const r = await client.delete<ApiResponse<boolean>>(`${BASE}/${encodeURIComponent(name)}`)
  return Boolean(r.data?.data)
}

export async function toggleMcpServer(name: string, enabled: boolean): Promise<McpServerDetail | null> {
  const r = await client.post<ApiResponse<McpServerDetail>>(
    `${BASE}/${encodeURIComponent(name)}/toggle`,
    { enabled }
  )
  return r.data?.data || null
}

export async function testMcpServer(name: string): Promise<McpTestResult> {
  const r = await client.post<ApiResponse<McpTestResult>>(`${BASE}/${encodeURIComponent(name)}/test`, {})
  return r.data?.data || { ok: false, message: '空响应' }
}

export async function refreshMcpServerTools(name: string): Promise<McpServerDetail | null> {
  const r = await client.post<ApiResponse<McpServerDetail>>(
    `${BASE}/${encodeURIComponent(name)}/refresh-tools`,
    {}
  )
  return r.data?.data || null
}
