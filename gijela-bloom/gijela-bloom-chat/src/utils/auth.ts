export function getToken(): string | null {
  try {
    return localStorage.getItem('token')
  } catch {
    return null
  }
}

export function buildCommonHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'X-Tenant-Id': 'demo-tenant',
    'X-Request-Id': typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}`,
    'X-Operator': 'demo-user'
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}
