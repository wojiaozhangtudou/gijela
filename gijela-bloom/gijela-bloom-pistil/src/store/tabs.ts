import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<Array<{ title: string; path: string; closable?: boolean }>>([])
  const active = ref<string>('')
  const STORAGE_KEY = 'app_tabs_v1'

  function normalizePath(path: string): string {
    if (!path) return path
    const p = path.replace(/\/+$/, '') || '/'
    // 统一别名路径，避免刷新后同页面出现两个标签
    if (p === '/sys/users') return '/system/users'
    if (p === '/sys/sessions') return '/system/sessions'
    return p
  }

  function dedupeTabs(input: Array<{ title: string; path: string; closable?: boolean }>) {
    const map = new Map<string, { title: string; path: string; closable?: boolean }>()
    for (const it of input || []) {
      if (!it || !it.path) continue
      const key = normalizePath(it.path)
      if (!map.has(key)) {
        map.set(key, { ...it, path: key })
      } else {
        const old = map.get(key)!
        map.set(key, {
          ...old,
          // 非 closable（固定标签）优先保留
          closable: (old.closable === false || it.closable === false) ? false : (old.closable ?? it.closable)
        })
      }
    }
    return Array.from(map.values())
  }

  function persist() {
    try {
      const payload = { tabs: tabs.value, active: active.value }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
    } catch (e) {
      // ignore storage errors
    }
  }

  function init(defaults?: { title: string; path: string; closable?: boolean }[]) {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        const parsed = JSON.parse(raw)
        if (parsed && Array.isArray(parsed.tabs) && parsed.tabs.length) {
          const normalized = dedupeTabs(parsed.tabs)
          tabs.value.splice(0, tabs.value.length, ...normalized)
          active.value = normalizePath(parsed.active || (normalized[0] && normalized[0].path) || '')
          if (!tabs.value.find(t => t.path === active.value) && tabs.value.length) {
            active.value = tabs.value[0].path
          }
          persist()
          return
        }
      }
    } catch (e) {
      // ignore parse errors
    }
    if (defaults && defaults.length) {
      tabs.value.splice(0, tabs.value.length, ...defaults)
      active.value = defaults[0].path
    } else if (!tabs.value.length) {
      tabs.value.push({ title: 'Dashboard', path: '/dashboard', closable: false })
      active.value = '/dashboard'
    }
    persist()
  }

  function open(path: string, title?: string) {
    if (!path) return
    const np = normalizePath(path)
    const found = tabs.value.find((t) => t.path === np)
    if (!found) {
      tabs.value.push({ title: title || np, path: np, closable: true })
    }
    active.value = np
  persist()
  }

  function remove(path: string) {
    const np = normalizePath(path)
    const idx = tabs.value.findIndex((t) => t.path === np)
    if (idx >= 0) tabs.value.splice(idx, 1)
    if (active.value === np) {
      const last = tabs.value[tabs.value.length - 1]
      active.value = last ? last.path : ''
    }
  persist()
  }

  function closeAll() {
    const keep = tabs.value.filter((t) => !t.closable)
    tabs.value.splice(0, tabs.value.length, ...keep)
    active.value = keep.length ? keep[0].path : ''
  persist()
  }

  function closeOthers() {
    const keep = tabs.value.filter((t) => t.path === active.value || !t.closable)
    tabs.value.splice(0, tabs.value.length, ...keep)
  persist()
  }

  // update a single tab's title (used when menu labels become available)
  function updateTitle(path: string, title: string) {
    const np = normalizePath(path)
    const t = tabs.value.find((x) => x.path === np)
    if (t && title && title !== t.title) {
      t.title = title
      persist()
    }
  }

  return { tabs, active, init, open, remove, closeAll, closeOthers, updateTitle }
})
