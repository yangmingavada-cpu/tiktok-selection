import type { SessionStatus } from '@/types'

// ============================================================
// Session status labels & tag types
// ============================================================
export const SESSION_STATUS_LABEL: Record<SessionStatus, string> = {
  created: '已创建',
  in_progress: '运行中',
  paused: '已暂停',
  completed: '已完成',
  failed: '失败',
  cancelled: '已取消',
}

export const SESSION_STATUS_TAG: Record<SessionStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
  created: 'info',
  in_progress: '',
  paused: 'warning',
  completed: 'success',
  failed: 'danger',
  cancelled: 'info',
}

export function getStatusLabel(status: string): string {
  return SESSION_STATUS_LABEL[status as SessionStatus] ?? status
}

export function getStatusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  return SESSION_STATUS_TAG[status as SessionStatus] ?? 'info'
}

// ============================================================
// Timeouts (ms)
// ============================================================
export const TIMEOUT = {
  DEFAULT: 15_000,
  INTENT_PARSE: 660_000,
  INTENT_PREVIEW: 30_000,
  INTENT_INTERPRET: 90_000,
  EXPORT_EXCEL: 60_000,
} as const

// ============================================================
// Pagination defaults
// ============================================================
export const PAGE_SIZE = {
  DEFAULT: 20,
  SMALL: 10,
  LARGE: 50,
  DASHBOARD: 5,
} as const

export const PAGE_SIZES = [10, 20, 50] as const

// ============================================================
// Storage keys
// ============================================================
export const STORAGE_KEY = {
  TOKEN: 'token',
  USER_ROLE: 'userRole',
} as const
