import { defineStore } from 'pinia'
import { ref } from 'vue'
import { loginApi, fetchUserInfo, fetchMenuTree } from '../api/client'
import { getUserAvatar } from '../api/user'
import { useTabsStore } from './tabs'
import { setToken, removeToken } from '../utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getTokenFromStorage())
    const lastError = ref<string | null>(null)

  function getTokenFromStorage(): string | null {
    try { return localStorage.getItem('token') } catch { return null }
  }

  async function login(username: string, password: string) {
    try {
      lastError.value = null
        const resp = await loginApi(username, password)
      // axios response: resp.data is the ApiResponse, resp.headers may contain authorization
      const r = resp && (resp as any).data ? (resp as any).data : resp
      // handle various possible shapes of token in backend responses
      // common shapes: { data: { token } }, { data: { access_token } }, { token }, or raw string
      let tokenStr: string | null = null
      if (!r) {
        return false
      }
      // If API returned ApiResponse-like object
      if (typeof r === 'object') {
        // r.data could be the payload
        const payload = (r as any).data
        if (typeof payload === 'string') {
          tokenStr = payload
        } else if (payload && typeof payload === 'object') {
          tokenStr = payload.token || payload.access_token || payload.accessToken || null
        }
        // fallback: r may directly contain token
        tokenStr = tokenStr || (r as any).token || null
      } else if (typeof r === 'string') {
        tokenStr = r
      }

      // also check response headers for Authorization or x-auth-token
      try {
        const hdrs = (resp as any).headers || {}
        const authHeader = hdrs['authorization'] || hdrs['Authorization'] || hdrs['x-auth-token'] || hdrs['X-Auth-Token']
        if (!tokenStr && typeof authHeader === 'string') {
          // header may be 'Bearer <token>'
          const parts = authHeader.split(' ')
          tokenStr = parts.length > 1 ? parts[1] : parts[0]
        }
      } catch (e) {
        // ignore header parsing errors
      }


      if (tokenStr) {
        token.value = tokenStr
        setToken(tokenStr)
        // load user info and menus after login
        await loadInitialData()
        return true
      }
    // try to extract backend error message if available
    const backendMsg = (r && ((r as any).message || (r as any).msg)) || ((r && (r as any).data) && (((r as any).data as any).message || ((r as any).data as any).msg)) || null
    if (backendMsg) lastError.value = String(backendMsg)
    console.warn('login: token not found in response', r)
    return false
    } catch (err) {
      console.error('login error', err)
      // if axios error with response body, try to extract message
      const e = err as any
      if (e && e.response && e.response.data) {
        const payload = e.response.data
        const backendMsg = (payload && (payload.message || payload.msg)) || null
        if (backendMsg) lastError.value = String(backendMsg)
      }
      return false
    }
  }

  function logout() {
    token.value = null
    removeToken()
    try {
      const tabs = useTabsStore()
      // close all tabs and clear persisted key
      tabs.closeAll()
      localStorage.removeItem('app_tabs_v1')
    } catch (e) {
      // ignore if tabs store not available
    }
  }

  const profile = ref<any>(null)
  const menus = ref<any[]>([])
  const externalLinks = ref<any[]>([])

  function ensureDefaultExternalLinks(list: any[]): any[] {
    const current = Array.isArray(list) ? [...list] : []
    const defaultChatFlowPath = (import.meta as any).env?.VITE_CHAT_FLOW_URL || 'http://localhost:5175/workflows'
    const exists = current.some((item: any) => {
      const p = String(item?.path || '')
      return p.includes('5175/workflows') || p === defaultChatFlowPath || String(item?.name || '').includes('Chat Flow')
    })
    if (!exists) {
      current.push({
        id: 'ext-chat-flow',
        name: 'Chat Flow',
        path: defaultChatFlowPath,
        icon: 'Link',
        status: '1',
        type: 'S'
      })
    }
    return current
  }

  function splitMenus(tree: any[]): { normal: any[]; external: any[] } {
    const external: any[] = []
    function walk(nodes: any[]): any[] {
      if (!Array.isArray(nodes)) return []
      const out: any[] = []
      for (const n of nodes) {
        if (!n) continue
        if (n.type === 'S') {
          // 仅启用状态进入外链区；停用的不展示
          if (String(n.status ?? '1') === '1') external.push(n)
          continue
        }
        const cloned = { ...n }
        if (cloned.children) cloned.children = walk(cloned.children)
        out.push(cloned)
      }
      return out
    }
    const normal = walk(tree)
    return { normal, external }
  }

  async function loadInitialData() {
    try {
      const info = await fetchUserInfo()
      profile.value = info || null
      // if avatar not present in profile, try to fetch via dedicated avatar endpoint
      try {
        if (profile.value && !profile.value.avatar && (profile.value.id || profile.value.userId)) {
          const id = profile.value.id ?? profile.value.userId
          const avResp: any = await getUserAvatar(id)
          if (avResp && avResp.code === 0 && avResp.data) {
            const d = avResp.data
            if (typeof d === 'string') {
              if (d.startsWith('data:')) profile.value.avatar = d
              else profile.value.avatar = 'data:image/png;base64,' + d
            } else if (typeof d === 'object') {
              // try common fields
              const b = d.base64 || d.avatarBase64 || d.data || null
              const mime = d.mime || d.contentType || d.type || 'image/png'
              if (b && typeof b === 'string') {
                if (b.startsWith('data:')) profile.value.avatar = b
                else profile.value.avatar = `data:${mime};base64,` + b
              }
            }
          }
        }
      } catch (e) {
        // ignore avatar fetch errors
      }
    } catch (e) {
      profile.value = null
    }
    try {
      // Prefer fetching current user's authorized menu tree
      const { fetchMyMenus } = await import('../api/client')
      let m = await fetchMyMenus()
      if (!m) {
        // fallback to public/full menu tree
        const { fetchMenuTree } = await import('../api/client')
        m = await fetchMenuTree()
      }
  const full = m || []
  const { normal, external } = splitMenus(full)
  menus.value = normal
  externalLinks.value = ensureDefaultExternalLinks(external)
    } catch (e) {
  menus.value = []
  externalLinks.value = ensureDefaultExternalLinks([])
    }

  // ensure default Dashboard exists (use i18n key 'message.dashboard')
  const hasDashboard = menus.value && menus.value.find((x: any) => x.path === '/dashboard' || x.name === 'message.dashboard')
    if (!hasDashboard) {
      menus.value = [{ id: 'home', name: 'message.dashboard', path: '/dashboard', icon: 'House' }, ...(menus.value || [])]
    }
  }

  return { token, login, logout, profile, menus, externalLinks, loadInitialData, lastError }
})
