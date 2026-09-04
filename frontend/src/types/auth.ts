export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface AuthSession {
  token: string
  userId: number
  username: string
  status: UserStatus
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
}

export interface LoginResponse extends AuthSession {}

export interface RegisterResponse {
  id: number
  username: string
  status: UserStatus
}
