import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '../utils/auth'
import {
  RagAliasReviewRequest,
  RagAliasReviewResponseData,
  RagApiResponse,
  RagIndexRebuildRequest,
  RagIndexRebuildResponseData,
  RagIndexStatusResponseData,
  RagQueryRequest,
  RagQueryResponseData
} from '../types/rag'

const getRouter = async () => {
  const { default: router } = await import('../router')
  return router
}

const env = import.meta.env as Record<string, any>
const ragBaseUrl = env.VITE_RAG_API_BASE || '/api'

const ragClient = axios.create({
  baseURL: ragBaseUrl,
  timeout: 10000
})

ragClient.interceptors.request.use(async config => {
  const token = getToken()

  if (!token && window.location.pathname !== '/login') {
    try {
      const router = await getRouter()
      router.push('/login')
    } catch (e) {
      window.location.href = '/login'
    }
    return Promise.reject(new Error('未登录'))
  }

  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }

  return config
}, err => Promise.reject(err))

ragClient.interceptors.response.use(
  async res => {
    if (res.data && (res.data.code === 1101 || res.status === 401)) {
      removeToken()
      try {
        localStorage.removeItem('user')
      } catch (e) {
        // ignore
      }

      if (window.location.pathname !== '/login') {
        ElMessage.warning('登录已失效，请重新登录')
        try {
          const router = await getRouter()
          router.push('/login')
        } catch (e) {
          window.location.href = '/login'
        }
      }
      return Promise.reject(new Error(res.data.message || '未认证'))
    }
    return res
  },
  async err => {
    if (err.response?.status === 401) {
      removeToken()
      try {
        localStorage.removeItem('user')
      } catch (e) {
        // ignore
      }

      if (window.location.pathname !== '/login') {
        ElMessage.error('RAG 接口认证失败，请重新登录')
        try {
          const router = await getRouter()
          router.push('/login')
        } catch (e) {
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(err)
  }
)

export async function queryRag(payload: RagQueryRequest) {
  const response = await ragClient.post<RagApiResponse<RagQueryResponseData>>('/rag/query', payload)
  return response.data
}

export async function rebuildRagIndex(payload: RagIndexRebuildRequest) {
  const response = await ragClient.post<RagApiResponse<RagIndexRebuildResponseData>>('/rag/index/rebuild-t1', payload)
  return response.data
}

export async function fetchRagIndexStatus(params: { jobId?: string; bizDate?: string }) {
  const response = await ragClient.get<RagApiResponse<RagIndexStatusResponseData>>('/rag/index/status', { params })
  return response.data
}

export async function submitRagAliasReview(payload: RagAliasReviewRequest) {
  const response = await ragClient.post<RagApiResponse<RagAliasReviewResponseData>>('/rag/review/alias', payload)
  return response.data
}

export { ragClient }
