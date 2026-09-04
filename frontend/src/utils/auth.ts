import type { AuthSession, UserStatus } from '@/types/auth'

export const AUTH_STORAGE_KEY = 'career-platform.session'

function isUserStatus(value: unknown): value is UserStatus {
  return value === 'ACTIVE' || value === 'DISABLED'
}

function isAuthSession(value: unknown): value is AuthSession {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.token === 'string' &&
    candidate.token.length > 0 &&
    typeof candidate.userId === 'number' &&
    Number.isFinite(candidate.userId) &&
    typeof candidate.username === 'string' &&
    candidate.username.length > 0 &&
    isUserStatus(candidate.status)
  )
}

export function getSession(): AuthSession | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) {
      return null
    }

    const parsed: unknown = JSON.parse(raw)
    if (isAuthSession(parsed)) {
      return parsed
    }

    clearSession()
  } catch {
    clearSession()
  }

  return null
}

export function setSession(session: AuthSession): void {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.removeItem(AUTH_STORAGE_KEY)
}

export function getToken(): string | null {
  return getSession()?.token ?? null
}

export function isAuthenticated(): boolean {
  return getSession() !== null
}
