import instance from './client'
import { ApiResponse } from '../types/api'

export async function pagePosts(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/posts/page', payload)
  return r.data
}

export async function savePost(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/posts', payload)
  return r.data
}

export async function deletePosts(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/posts/delete', payload)
  return r.data
}

export async function changePostStatus(payload: any) {
  const r = await instance.post<ApiResponse<any>>('/v1/posts/status', payload)
  return r.data
}

export default { pagePosts, savePost, deletePosts, changePostStatus }
