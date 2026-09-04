import axios, { type AxiosError, type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'

import router from '@/router'
import { clearSession, getToken } from '@/utils/auth'

export interface ApiErrorResponse {
  code?: string
  message?: string
  timestamp?: string
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  return Boolean(
    value &&
      typeof value === 'object' &&
      typeof (value as Record<string, unknown>).message === 'string',
  )
}

export function getApiErrorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (isApiErrorResponse((error as AxiosError<ApiErrorResponse>)?.response?.data)) {
    return (error as AxiosError<ApiErrorResponse>).response?.data?.message ?? fallback
  }

  if (axios.isAxiosError(error) && error.message) {
    return error.message
  }

  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    const message = getApiErrorMessage(error)

    if (status === 401) {
      clearSession()
      ElMessage.error(message)

      const currentPath = typeof window === 'undefined' ? '' : window.location.pathname
      const isAuthPage = currentPath === '/login' || currentPath === '/register'
      if (!isAuthPage) {
        const redirect = router.currentRoute.value.fullPath
        void router.replace({ name: 'login', query: redirect === '/login' ? undefined : { redirect } })
      }
    } else {
      ElMessage.error(message)
    }

    return Promise.reject(error)
  },
)

export default apiClient
