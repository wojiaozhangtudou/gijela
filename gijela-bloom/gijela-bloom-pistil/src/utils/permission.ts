import { useUserStore } from '../store/user'

function findPermission(nodes: any[], perm: string): boolean {
  if (!nodes || !nodes.length) return false
  for (const n of nodes) {
    if (n.permission && String(n.permission) === String(perm)) return true
    if (n.children && n.children.length) {
      if (findPermission(n.children, perm)) return true
    }
  }
  return false
}

function collectPermissions(nodes: any[], set: Set<string>) {
  if (!nodes || !nodes.length) return
  for (const n of nodes) {
    if (n.permission) set.add(String(n.permission))
    if (n.children && n.children.length) collectPermissions(n.children, set)
  }
}

export function getUserPermissions(): string[] {
  try {
    const store = useUserStore()
    const menus = store.menus || []
    const s = new Set<string>()
    collectPermissions(menus, s)
    return Array.from(s)
  } catch (e) {
    return []
  }
}

export function hasPermission(permission: string): boolean {
  if (!permission) return false
  try {
    const store = useUserStore()
    const menus = store.menus || []
    return findPermission(menus, permission)
  } catch (e) {
    return false
  }
}

export function hasAnyPermission(perms: string[] | string): boolean {
  const arr = Array.isArray(perms) ? perms : String(perms).split(',').map(s => s.trim()).filter(Boolean)
  if (!arr.length) return false
  for (const p of arr) if (hasPermission(p)) return true
  return false
}

export function hasAllPermissions(perms: string[] | string): boolean {
  const arr = Array.isArray(perms) ? perms : String(perms).split(',').map(s => s.trim()).filter(Boolean)
  if (!arr.length) return false
  for (const p of arr) if (!hasPermission(p)) return false
  return true
}

// default export for compatibility
export default { hasPermission, hasAnyPermission, hasAllPermissions, getUserPermissions }
