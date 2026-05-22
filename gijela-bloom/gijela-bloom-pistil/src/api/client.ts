import axios from 'axios'
import { ApiResponse, MenuNode } from '../types/api'
import { getToken, removeToken, setToken } from '../utils/auth'
import { ElMessage } from 'element-plus'

// 延迟导入 router 避免循环依赖
const getRouter = async () => {
  const { default: router } = await import('../router')
  return router
}

// Use VITE_API_BASE when explicitly set (production or custom), otherwise use relative '/api'
// so the Vite dev server proxy can forward requests and avoid CORS in development.
const env = import.meta.env as Record<string, any>
const baseUrl = env.VITE_API_BASE || '/api'
const instance = axios.create({
  baseURL: baseUrl,
  timeout: 10000,
  withCredentials: true
})

let refreshingPromise: Promise<string | null> | null = null

function getDeviceId(): string {
  try {
    const key = 'app_device_id'
    let id = localStorage.getItem(key)
    if (!id) {
      id = (globalThis.crypto && 'randomUUID' in globalThis.crypto)
        ? (globalThis.crypto as any).randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`
      localStorage.setItem(key, id)
    }
    return id
  } catch {
    return 'web-device'
  }
}

function getClientLabel(): string {
  try {
    const ua = navigator.userAgent || 'web'
    const platform = navigator.platform || 'unknown'
    return `${platform} | ${ua}`
  } catch {
    return 'web'
  }
}

function extractAccessToken(resp: any): string | null {
  const r = resp && resp.data ? resp.data : resp
  if (!r) return null
  if (typeof r === 'object') {
    const payload = (r as any).data
    if (payload && typeof payload === 'object') {
      return payload.accessToken || payload.access_token || payload.token || null
    }
    return (r as any).accessToken || (r as any).access_token || (r as any).token || null
  }
  return null
}

async function refreshAccessToken(): Promise<string | null> {
  if (refreshingPromise) return refreshingPromise
  refreshingPromise = (async () => {
    try {
      const r = await instance.post<ApiResponse<any>>('/v1/auth/refresh', null, {
        headers: {},
        withCredentials: true,
        // 标记为刷新请求，避免拦截器递归重试
        _skipAutoRefresh: true
      } as any)
      const token = extractAccessToken((r as any).data)
      if (token) setToken(token)
      return token
    } catch {
      return null
    } finally {
      refreshingPromise = null
    }
  })()
  return refreshingPromise
}

export async function tryRefreshAccessToken(): Promise<string | null> {
  return refreshAccessToken()
}

async function redirectToLogin(message?: string) {
  removeToken()
  if (window.location.pathname !== '/login') {
    if (message) ElMessage.warning(message)
    try {
      const router = await getRouter()
      router.push('/login')
    } catch {
      window.location.href = '/login'
    }
  }
}

// 请求拦截器
instance.interceptors.request.use(async config => {
  config.headers = config.headers || {}
  config.headers['X-Device-Id'] = getDeviceId()
  config.headers['X-Client-Label'] = getClientLabel()

  const token = getToken()
  
  // 登录请求不需要 token
  if (config.url?.includes('/v1/auth/login') || config.url?.includes('/v1/auth/refresh')) {
    return config
  }
  
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  
  return config
}, err => Promise.reject(err))

// 响应拦截器
instance.interceptors.response.use(
  async res => {
    // 检查业务状态码
    if (res.data && (res.data.code === 2001 || res.data.code === 2002 || res.data.code === 2004)) {
      const cfg: any = res.config || {}
      if (!cfg._skipAutoRefresh && !cfg._retry) {
        cfg._retry = true
        const newToken = await refreshAccessToken()
        if (newToken) {
          cfg.headers = cfg.headers || {}
          cfg.headers['Authorization'] = `Bearer ${newToken}`
          return instance.request(cfg)
        }
      }
      const message = res.data.code === 2001 ? '未登录，请先登录' : '登录已过期，请重新登录'
      await redirectToLogin(message)
      return Promise.reject(new Error(res.data.msg || message))
    }
    return res
  },
  async err => {
    const cfg: any = err?.config || {}
    if (err.response && err.response.status === 401 && !cfg._skipAutoRefresh && !cfg._retry) {
      cfg._retry = true
      const newToken = await refreshAccessToken()
      if (newToken) {
        cfg.headers = cfg.headers || {}
        cfg.headers['Authorization'] = `Bearer ${newToken}`
        return instance.request(cfg)
      }
      await redirectToLogin('认证失败，请重新登录')
    }
    return Promise.reject(err)
  }
)

export async function loginApi(username: string, password: string) {
  // Return the full axios response so callers can inspect headers and different response shapes
  return await instance.post<ApiResponse<any>>('/v1/auth/login', { username, password })
}

export async function logoutApi() {
  return await instance.post<ApiResponse<any>>('/v1/auth/logout', null, {
    withCredentials: true,
    _skipAutoRefresh: true
  } as any)
}

export async function fetchMenuTree(): Promise<MenuNode[] | null> {
  const r = await instance.get<ApiResponse<MenuNode[]>>('/v1/menus/tree')
  return r.data?.data || null
}

export async function fetchMyMenus(): Promise<MenuNode[] | null> {
  try {
  const r = await instance.get<ApiResponse<MenuNode[]>>('/v1/auth/me/menus')
    // r.data may be ApiResponse or MenuNode[]; prefer r.data.data when it's an ApiResponse
    if (r && (r as any).data) {
      const payload = (r as any).data
      if (Array.isArray(payload)) return payload
      if (payload && Array.isArray(payload.data)) return payload.data
    }
    return null
  } catch (e) {
    // fallback: null and caller may call fetchMenuTree
    return null
  }
}

export async function fetchUserInfo() {
  // Try a common me endpoint; adapt if backend differs
  try {
    const resp = await instance.get<ApiResponse<any>>('/v1/auth/me')
    return resp.data?.data || resp.data || null
  } catch (e) {
    // some backends don't expose /me; caller will handle null
    return null
  }
}

export default instance
