import { ElMessage } from 'element-plus'

export interface ExternalMenuItem {
  id: string | number
  name?: string
  path?: string
  icon?: string
  status?: string | number
  [k: string]: any
}

function isValidPath(p: string) {
  return /^(https?:\/\/|\/)/i.test(p)
}

export function resolveUrl(raw: string) {
  if (/^https?:\/\//i.test(raw)) return raw
  if (raw.startsWith('/')) return window.location.origin + raw
  return ''
}

export function openExternal(item: ExternalMenuItem) {
  if (!item || !item.path) return
  const raw = item.path.trim()
  if (!isValidPath(raw)) {
    ElMessage.error('非法外链路径')
    return
  }
  const url = resolveUrl(raw)
  if (!url) {
    ElMessage.error('无法解析外链地址')
    return
  }
  try {
    window.open(url, '_blank', 'noopener')
  } catch (e) {
    ElMessage.error('浏览器阻止了新标签打开')
  }
}
