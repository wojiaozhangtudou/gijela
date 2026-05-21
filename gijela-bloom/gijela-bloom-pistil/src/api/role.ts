import instance from './client'
import { ApiResponse } from '../types/api'

export async function pageRoles(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/roles/page', payload)
  return r.data
}

export async function saveRole(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/roles', payload)
  return r.data
}

export async function deleteRoles(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/roles/delete', payload)
  return r.data
}

export async function changeRoleStatus(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/roles/status', payload)
  return r.data
}

export async function getRoleById(id: string) {
  const r = await instance.get<ApiResponse<any>>(`/v1/roles/${id}`)
  return r.data
}

// 获取角色已绑定的菜单ID列表
export async function getRoleMenus(roleId: string) {
  // 根据 OpenAPI：GET /api/v1/roles/menu-ids?roleId=xxx
  const r = await instance.get<ApiResponse<any>>('/v1/roles/menu-ids', { params: { roleId } })
  return r.data
}

// 分配角色菜单权限：POST /v1/roles/perms
export async function assignRoleMenus(payload: { roleId: string; menuIds: Array<string | number> }) {
  // Coerce menuIds to numbers when they are numeric strings to match common backend expectations
  const body = { ...payload, menuIds: Array.isArray(payload.menuIds) ? payload.menuIds.map(m => {
    const n = Number(m)
    return Number.isNaN(n) ? m : n
  }) : [] }
  const r = await instance.post<ApiResponse<any>>('/v1/roles/perms', body)
  return r.data
}

export default { pageRoles, saveRole, deleteRoles, changeRoleStatus, getRoleById, getRoleMenus, assignRoleMenus }
