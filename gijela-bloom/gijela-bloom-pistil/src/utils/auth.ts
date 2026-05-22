let tokenMemory: string | null = null

export function getToken(): string | null {
  return tokenMemory
}

export function setToken(t: string | null) {
  tokenMemory = t || null
}

export function removeToken() {
  tokenMemory = null
}
