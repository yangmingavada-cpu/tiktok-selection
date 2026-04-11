import request from '@/utils/request'
import type {
  ApiResponse,
  Block,
  ExtraColCreateRequest,
  ExtraColUpdateRequest,
  PageResult,
  Session,
  SessionStep,
  UserExtraCol,
} from '@/types'
import { TIMEOUT } from '@/constants'

export interface SessionCreateRequest {
  blockChain: Block[]
  sourceType?: string
  sourcePlanId?: string
  sourceText?: string
  title?: string
  agentThreadId?: string
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

export interface ExportSessionParams {
  format?: 'xlsx' | 'csv'
  fields?: string
  order?: string
  search?: string
  rowIndices?: string
  renames?: string
}

/**
 * 导出会话结果。
 * 不传 params 时全量导出（向后兼容旧调用）。
 */
export async function exportSessionExcel(
  id: string,
  title?: string,
  params?: ExportSessionParams,
) {
  const res = await request.get(`/sessions/${id}/export`, {
    params,
    responseType: 'blob',
    timeout: TIMEOUT.EXPORT_EXCEL,
  })
  const ext = params?.format ?? 'xlsx'
  const mime =
    ext === 'csv'
      ? 'text/csv;charset=utf-8'
      : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  const blob = new Blob([res as unknown as BlobPart], { type: mime })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = (title || id) + '.' + ext
  link.click()
  URL.revokeObjectURL(url)
}

// ============================================================
// 单元格编辑 + 用户增列 CRUD
// ============================================================

export interface CellUpdatePayload {
  rowIndex: number
  field: string
  value: unknown
}

export interface CellUpdateResponse {
  row: Record<string, unknown>
  updateTime: string
}

/** PATCH /sessions/{id}/cells —— 编辑一个单元格（原始列或用户增列） */
export function updateSessionCell(id: string, body: CellUpdatePayload) {
  return request.patch<unknown, ApiResponse<CellUpdateResponse>>(
    `/sessions/${id}/cells`,
    body,
  )
}

/** POST /sessions/{id}/extra-cols —— 新增用户列 */
export function addExtraCol(id: string, body: ExtraColCreateRequest) {
  return request.post<unknown, ApiResponse<UserExtraCol>>(
    `/sessions/${id}/extra-cols`,
    body,
  )
}

/** PATCH /sessions/{id}/extra-cols/{colId} —— 重命名 / 改 options */
export function renameExtraCol(id: string, colId: string, body: ExtraColUpdateRequest) {
  return request.patch<unknown, ApiResponse<UserExtraCol>>(
    `/sessions/${id}/extra-cols/${colId}`,
    body,
  )
}

/** DELETE /sessions/{id}/extra-cols/{colId} —— 删除增列 + 清理所有行的对应值 */
export function removeExtraCol(id: string, colId: string) {
  return request.delete(`/sessions/${id}/extra-cols/${colId}`)
}
