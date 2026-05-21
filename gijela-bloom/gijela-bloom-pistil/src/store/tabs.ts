import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<Array<{ title: string; path: string; closable?: boolean }>>([])
  const active = ref<string>('')
  const STORAGE_KEY = 'app_tabs_v1'

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
          tabs.value.splice(0, tabs.value.length, ...parsed.tabs)
          active.value = parsed.active || (parsed.tabs[0] && parsed.tabs[0].path) || ''
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
    const found = tabs.value.find((t) => t.path === path)
    if (!found) {
      tabs.value.push({ title: title || path, path, closable: true })
    }
    active.value = path
  persist()
  }

  function remove(path: string) {
    const idx = tabs.value.findIndex((t) => t.path === path)
    if (idx >= 0) tabs.value.splice(idx, 1)
    if (active.value === path) {
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
    const t = tabs.value.find((x) => x.path === path)
    if (t && title && title !== t.title) {
      t.title = title
      persist()
    }
  }

  return { tabs, active, init, open, remove, closeAll, closeOthers, updateTitle }
})
