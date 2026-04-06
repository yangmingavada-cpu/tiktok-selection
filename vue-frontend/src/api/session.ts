import request from '@/utils/request'
import type { ApiResponse, PageResult, Session, SessionStep, Block } from '@/types'
import { TIMEOUT } from '@/constants'

export interface SessionCreateRequest {
  blockChain: Block[]
  sourceType?: string
  sourcePlanId?: string
  sourceText?: string
  title?: string
}

export function createSession(data: SessionCreateRequest) {
  return request.post<unknown, ApiResponse<Session>>('/sessions', data)
}

export function listSessions(params: { pageNum?: number; pageSize?: number; status?: string }) {
  return request.get<unknown, ApiResponse<PageResult<Session>>>('/sessions', { params })
}

export function getSession(id: string) {
  return request.get<unknown, ApiResponse<Session>>(`/sessions/${id}`)
}

export function removeSession(id: string) {
  return request.delete(`/sessions/${id}`)
}

export function updateSession(id: string, data: { title?: string; remark?: string }) {
  return request.patch<unknown, ApiResponse<Session>>(`/sessions/${id}`, data)
}

export function executeSession(id: string) {
  return request.post(`/sessions/${id}/execute`)
}

export function getSessionSteps(id: string) {
  return request.get<unknown, ApiResponse<SessionStep[]>>(`/sessions/${id}/steps`)
}

export function resumeSession(id: string) {
  return request.post(`/sessions/${id}/resume`)
}

export function cancelSession(id: string) {
  return request.post(`/sessions/${id}/cancel`)
}

export function saveConversationSnapshot(id: string, snapshot: object) {
  return request.post(`/sessions/${id}/conversation-snapshot`, snapshot)
}

export async function exportSessionExcel(id: string, title?: string) {
  const res = await request.get(`/sessions/${id}/export`, {
    responseType: 'blob',
    timeout: TIMEOUT.EXPORT_EXCEL,
  })
  const blob = new Blob([res as unknown as BlobPart], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = (title || id) + '.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}
