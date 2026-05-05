import request from '@/utils/request'
import type { ApiResponse, PageResult, Plan, Session, Block } from '@/types'

export interface PlanCreateRequest {
  name: string
  description?: string
  sourceText?: string
  blockChain?: Block[]
  tags?: string[]
}

export function listPlans(params?: { pageNum?: number; pageSize?: number }) {
  return request.get<unknown, ApiResponse<PageResult<Plan>>>('/plans', { params })
}

export function createPlan(data: PlanCreateRequest) {
  return request.post<unknown, ApiResponse<Plan>>('/plans', data)
}

export function getPlan(id: string) {
  return request.get<unknown, ApiResponse<Plan>>(`/plans/${id}`)
}

export function updatePlan(id: string, data: Partial<PlanCreateRequest>) {
  return request.put(`/plans/${id}`, data)
}

export function deletePlan(id: string) {
  return request.delete(`/plans/${id}`)
}

export function executePlan(id: string) {
  return request.post<unknown, ApiResponse<Session>>(`/plans/${id}/execute`)
}

/** 从官方方案库克隆一份到当前用户的方案 */
export function createPlanFromPreset(presetId: string) {
  return request.post<unknown, ApiResponse<Plan>>(`/plans/from-preset/${presetId}`)
}
