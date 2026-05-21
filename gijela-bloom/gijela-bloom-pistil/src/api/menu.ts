import instance from './client'
import { ApiResponse } from '../types/api'

export async function pageMenus(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/menus/page', payload)
  return r.data
}

export async function saveMenu(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/menus', payload)
  return r.data
}

export async function deleteMenusApi(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/menus/delete', payload)
  return r.data
}

export async function changeMenuStatus(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/menus/status', payload)
  return r.data
}

// 获取全部菜单（平铺）
export async function listAllMenus() {
  const r = await instance.get<ApiResponse<any>>('/v1/menus')
  return r.data
}

// 获取菜单树（若后端支持专门接口）
export async function getMenuTree() {
  try {
    const r = await instance.get<ApiResponse<any>>('/v1/menus/tree')
    return r.data
  } catch (e) {
    // 回退使用全部菜单自行组装
    const all = await listAllMenus()
    return all
  }
}

export default { pageMenus, saveMenu, deleteMenusApi, changeMenuStatus, listAllMenus, getMenuTree }
