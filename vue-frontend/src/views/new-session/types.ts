import type { Block, PreviewResponse, PlanDraft } from '@/types'

export interface ChatMessage {
  role: 'ai' | 'user'
  text: string
  planningSnapshot?: PlanningSnapshot
  plan?: Block[]
  planDraft?: PlanDraft
  execCard?: ExecSession
  summary?: string
  tokens?: number
  sourceText?: string
  isGreeting?: boolean
  preview?: PreviewResponse | 'loading' | null
  interpretation?: string | 'loading' | null
  interpretationDone?: boolean
  suggestions?: string[]
}

export interface StepItem {
  label: string
  success: boolean
}

export type PlanningStatus = 'idle' | 'running' | 'completed' | 'needs_input' | 'failed'

export interface PlanningTraceEntry {
  id: number
  kind: 'status' | 'step' | 'thinking' | 'error'
  text: string
  createdAt: number
  success?: boolean
}

export interface PlanningSnapshot {
  status: Exclude<PlanningStatus, 'idle'>
  steps: StepItem[]
  thinkingText: string
  traceEntries: PlanningTraceEntry[]
  sessionId: string
}

export interface ExecSession {
  id: string
  status: 'running' | 'paused' | 'completed' | 'failed'
  dataState: 'pending' | 'ready' | 'empty'
  syncState: 'idle' | 'syncing'
  lastSyncedAt: number | null
  steps: { label: string; status: 'done' | 'running' | 'fail' }[]
  rows: Record<string, unknown>[]
  dims: { id: string; label: string; type: string }[]
  totalRows: number
  errorMsg: string | null
  auditResult?: {
    pass: boolean
    score: number
    issues: string[]
    suggestions: string[]
  }
}

export interface AskDialogState {
  visible: boolean
  question: string
  suggestions: string[]
}
