export function getToken(): string | null {
  try { return localStorage.getItem('token') } catch { return null }
}

export function setToken(t: string) {
  try { localStorage.setItem('token', t) } catch {}
}

export function removeToken() {
  try { localStorage.removeItem('token') } catch {}
}
