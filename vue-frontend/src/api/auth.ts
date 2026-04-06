import request from '@/utils/request'
import type { ApiResponse, LoginResponse, UserProfile } from '@/types'

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  name?: string
}

export function login(data: LoginRequest) {
  return request.post<unknown, ApiResponse<LoginResponse>>('/auth/login', data)
}

export function register(data: RegisterRequest) {
  return request.post<unknown, ApiResponse<LoginResponse>>('/auth/register', data)
}

export function getUserProfile() {
  return request.get<unknown, ApiResponse<UserProfile>>('/user/profile')
}
