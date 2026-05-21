import instance from './client'
import { ApiResponse } from '../types/api'

export async function pageDepts(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/depts/page', payload)
  return r.data
}

export async function saveDept(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/depts', payload)
  return r.data
}

export async function deleteDepts(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/depts/delete', payload)
  return r.data
}

export async function changeDeptStatus(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/depts/status', payload)
  return r.data
}

export async function fetchDeptTree() {
  const r = await instance.get<ApiResponse<any>>('/v1/depts/tree')
  return r.data
}

export async function fetchDeptSelectTree() {
  const r = await instance.get<ApiResponse<any>>('/v1/depts/select-tree')
  return r.data
}

export default { pageDepts, saveDept, deleteDepts, changeDeptStatus, fetchDeptTree, fetchDeptSelectTree }
