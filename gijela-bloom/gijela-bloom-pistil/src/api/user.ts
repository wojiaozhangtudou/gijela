import instance from './client'
import { ApiResponse } from '../types/api'

export async function pageUsers(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/users/page', payload)
  return r.data
}

export async function saveUser(payload: any) {
  try {
    const r = await instance.post<ApiResponse<any>>('/v1/users', payload)
    return r.data
  } catch (e: any) {
    // If backend returns non-2xx with a JSON body, axios throws and the payload is on e.response.data
    if (e && e.response && e.response.data) return e.response.data
    // rethrow if no structured response available
    throw e
  }
}

export async function deleteUsers(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/users/delete', payload)
  return r.data
}

export async function changeUserStatus(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/users/status', payload)
  return r.data
}

export async function resetUserPwd(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/users/reset-pwd', payload)
  return r.data
}

export async function getUserById(id: string | number) {
  const r = await instance.get<ApiResponse<any>>(`/v1/users/${id}`)
  return r.data
}

export async function assignUserDepts(id: string | number, ids: any[]) {
  // backend expects { ids: [...] }
  const r = await instance.post<ApiResponse<any>>(`/v1/users/${id}/depts`, { ids })
  return r.data
}

export async function listUserDepts(id: string | number) {
  const r = await instance.get<ApiResponse<any>>(`/v1/users/${id}/depts`)
  return r.data
}

export async function assignUserPosts(id: string | number, ids: any[]) {
  // backend expects { ids: [...] }
  const r = await instance.post<ApiResponse<any>>(`/v1/users/${id}/posts`, { ids })
  return r.data
}

export async function listUserPosts(id: string | number) {
  const r = await instance.get<ApiResponse<any>>(`/v1/users/${id}/posts`)
  return r.data
}

export async function getCurrentUser() {
  const r = await instance.get<ApiResponse<any>>('/v1/auth/me')
  return r.data
}

export async function updateAvatar(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/users/avatar', payload)
  return r.data
}

export async function getUserAvatar(id: string | number) {
  const r = await instance.get<ApiResponse<any>>(`/v1/users/${id}/avatar`)
  return r.data
}

export default { pageUsers, saveUser, deleteUsers, changeUserStatus, resetUserPwd, updateAvatar, getUserAvatar }
