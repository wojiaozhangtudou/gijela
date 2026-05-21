import client from './client'
import type { ApiResponse } from '@/types/chat'
import type { SkillDetail, SkillSummary } from '@/types/skill'

const BASE = '/v1/chat/admin/skills'

export async function listSkills(): Promise<SkillSummary[]> {
  const r = await client.get<ApiResponse<SkillSummary[]>>(BASE)
  return r.data?.data || []
}

export async function getSkillDetail(name: string): Promise<SkillDetail | null> {
  const r = await client.get<ApiResponse<SkillDetail>>(`${BASE}/${encodeURIComponent(name)}`)
  return r.data?.data || null
}

export async function toggleSkill(name: string, enabled: boolean): Promise<boolean> {
  const r = await client.post<ApiResponse<boolean>>(`${BASE}/${encodeURIComponent(name)}/toggle`, { enabled })
  return Boolean(r.data?.data)
}

export async function reloadSkills(): Promise<string[]> {
  const r = await client.post<ApiResponse<string[]>>(`${BASE}/reload`, {})
  return r.data?.data || []
}
