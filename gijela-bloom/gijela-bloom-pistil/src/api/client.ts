import axios from 'axios'
import { ApiResponse, MenuNode } from '../types/api'
import { getToken } from '../utils/auth'
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
  timeout: 10000
})

// 请求拦截器
instance.interceptors.request.use(async config => {
  const token = localStorage.getItem('token')
  
  // 登录请求不需要 token
  if (config.url?.includes('/v1/auth/login')) {
    return config
  }
  
  // 非登录请求需要 token
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

// 响应拦截器
instance.interceptors.response.use(
  async res => {
    // 检查业务状态码
    if (res.data && (res.data.code === 2001 || res.data.code === 2002)) {
      // 2001: 未登录, 2002: 令牌无效或过期
      const message = res.data.code === 2001 ? '未登录，请先登录' : '登录已过期，请重新登录'
      
      // 清理本地存储
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      
      // 避免重复跳转
      if (window.location.pathname !== '/login') {
        ElMessage.warning(message)
        try {
          const router = await getRouter()
          router.push('/login')
        } catch (e) {
          // 降级方案
          window.location.href = '/login'
        }
      }
      return Promise.reject(new Error(res.data.msg || message))
    }
    return res
  },
  async err => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      
      if (window.location.pathname !== '/login') {
        ElMessage.error('认证失败，请重新登录')
        try {
          const router = await getRouter()
          router.push('/login')
        } catch (e) {
          // 降级方案
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(err)
  }
)

export async function loginApi(username: string, password: string) {
  // Return the full axios response so callers can inspect headers and different response shapes
  return await instance.post<ApiResponse<any>>('/v1/auth/login', { username, password })
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
