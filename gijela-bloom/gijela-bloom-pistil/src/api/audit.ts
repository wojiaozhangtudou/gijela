import instance from './client'
import { ApiResponse } from '../types/api'

export async function pageAuditLogs(payload: any) {
  // backend API path is relative to baseURL; baseURL already points to '/api' in dev
  const r = await instance.post<ApiResponse<any>>('/v1/audit-logs/page', payload)
  return r.data
}

export default { pageAuditLogs }
