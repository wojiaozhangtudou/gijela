import instance from './client'
import { ApiResponse } from '../types/api'

export async function pageLoginSessions(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/auth/sessions/page', payload)
  return r.data
}

export async function listLoginSessions(userId?: number) {
  const params: any = {}
  if (typeof userId === 'number') params.userId = userId
  const r = await instance.get<ApiResponse<any>>('/v1/auth/sessions', { params })
  return r.data
}

export async function kickoutLoginSession(sessionId: string) {
  const r = await instance.post<ApiResponse<any>>(`/v1/auth/sessions/${sessionId}/kickout`)
  return r.data
}

export async function kickoutAllLoginSessions(userId: number) {
  const r = await instance.post<ApiResponse<any>>('/v1/auth/sessions/kickout-all', null, { params: { userId } })
  return r.data
}

export default { pageLoginSessions, listLoginSessions, kickoutLoginSession, kickoutAllLoginSessions }
