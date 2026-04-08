import request from '@/utils/request'
import type {
  ApiResponse,
  PageResult,
  DashboardOverview,
  EchotikApiKey,
  EchotikApiKeyForm,
  Tier,
  PresetPackage,
  PresetPackageForm,
  LlmConfig,
  LlmConfigForm,
  UserProfile,
} from '@/types'

// ==================== 运营看板 ====================

export function getDashboardOverview() {
  return request.get<unknown, ApiResponse<DashboardOverview>>('/admin/dashboard/overview')
}

export function getDashboardApiKeys() {
  return request.get<unknown, ApiResponse<EchotikApiKey[]>>('/admin/dashboard/api-keys')
}

// ==================== 用户管理 ====================

export interface AdminUserListParams {
  pageNum?: number
  pageSize?: number
  status?: string
  role?: string
  keyword?: string
}

export function listAdminUsers(params: AdminUserListParams) {
  return request.get<unknown, ApiResponse<PageResult<UserProfile>>>('/admin/users', { params })
}

export function updateAdminUser(id: string, data: { status?: string; tierId?: string; role?: string }) {
  return request.put(`/admin/users/${id}`, data)
}

// ==================== 等级配置 ====================

export function listTiers() {
  return request.get<unknown, ApiResponse<Tier[]>>('/admin/tiers')
}

export function createTier(data: Partial<Tier>) {
  return request.post('/admin/tiers', data)
}

export function updateTier(id: string, data: Partial<Tier>) {
  return request.put(`/admin/tiers/${id}`, data)
}

export function deleteTier(id: string) {
  return request.delete(`/admin/tiers/${id}`)
}

// ==================== Echotik API 密钥 ====================

export function listApiKeys() {
  return request.get<unknown, ApiResponse<EchotikApiKey[]>>('/admin/api-keys')
}

export function createApiKey(data: EchotikApiKeyForm) {
  return request.post('/admin/api-keys', data)
}

export function updateApiKey(id: string, data: Partial<EchotikApiKeyForm>) {
  return request.put(`/admin/api-keys/${id}`, data)
}

export function deleteApiKey(id: string) {
  return request.delete(`/admin/api-keys/${id}`)
}

export function toggleApiKey(id: string) {
  return request.patch(`/admin/api-keys/${id}/toggle`)
}

// ==================== 预设套餐 ====================

export function listPresets() {
  return request.get<unknown, ApiResponse<PresetPackage[]>>('/admin/presets')
}

export function createPreset(data: PresetPackageForm) {
  return request.post('/admin/presets', data)
}

export function updatePreset(id: string, data: Partial<PresetPackageForm>) {
  return request.put(`/admin/presets/${id}`, data)
}

export function deletePreset(id: string) {
  return request.delete(`/admin/presets/${id}`)
}

// ==================== LLM 配置 ====================

export function listLlmConfigs() {
  return request.get<unknown, ApiResponse<LlmConfig[]>>('/admin/llm-config')
}

export function createLlmConfig(data: LlmConfigForm) {
  return request.post('/admin/llm-config', data)
}

export function updateLlmConfig(id: string, data: Partial<LlmConfigForm>) {
  return request.put(`/admin/llm-config/${id}`, data)
}

export function deleteLlmConfig(id: string) {
  return request.delete(`/admin/llm-config/${id}`)
}

export function testLlmConfig(id: string) {
  return request.post<unknown, ApiResponse<{ success: boolean; latency_ms: number; model: string; message: string }>>(`/admin/llm-config/${id}/test`)
}
