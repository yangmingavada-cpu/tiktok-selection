// ============================================================
// Unified API response wrapper
// ============================================================
export interface ApiResponse<T = unknown> {
  code: string
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

// ============================================================
// Block chain
// ============================================================
export interface BlockConfig {
  [key: string]: unknown
}

export interface Block {
  blockId: string
  type?: string
  label?: string
  seq?: number
  config: BlockConfig
}

// ============================================================
// Session
// ============================================================
export type SessionStatus =
  | 'created'
  | 'in_progress'
  | 'paused'
  | 'completed'
  | 'failed'
  | 'cancelled'

export interface ConversationSnapshot {
  messages: ChatMessage[]
  qaHistory: Array<{ q: string; a: string }>
  planningSummary: string
  savedAt: string
}

export interface ChatMessage {
  role: 'user' | 'ai'
  text?: string
  plan?: Block[]
  interpretation?: string
  preview?: 'loading' | { status: 'ok' | 'empty' | 'error'; message: string }
  timestamp?: string
}

export interface Session {
  id: string
  userId: string
  title: string
  status: SessionStatus
  currentStep: number
  sourceText: string
  sourceType: string
  sourcePlanId: string | null
  blockChain: Block[]
  echotikApiCalls: number
  llmInputTokens: number
  llmOutputTokens: number
  llmTotalTokens: number
  startTime: string | null
  completeTime: string | null
  createTime: string
  updateTime: string
  currentView: CurrentView | null
  conversationSnapshot?: ConversationSnapshot
  remark?: string
}

export interface CurrentView {
  data: Record<string, unknown>[]
  dims: ColumnDim[]
  totalCount: number
  sortBy?: Record<string, string>
  summary?: Record<string, unknown>
}

export interface ColumnDim {
  id: string
  label: string
  type: 'string' | 'number' | 'percent' | 'score'
}

export type StepStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped' | 'paused'

export interface SessionStep {
  id: number
  sessionId: string
  branchId: string | null
  seq: number
  blockId: string
  blockConfig: BlockConfig
  label: string
  inputCount: number
  outputCount: number
  echotikApiCalls: number
  llmTokens: number
  durationMs: number
  status: StepStatus
  errorMessage: string | null
}

// ============================================================
// User & Auth
// ============================================================
export interface LoginResponse {
  token: string
  userId: string
  email: string
  name: string
  role: string
  tierName: string
}

export interface UserProfile {
  id: string
  email: string
  name: string
  role: string
  tierDisplayName: string
  status: string
}

// ============================================================
// Plan
// ============================================================
export interface Plan {
  id: string
  userId: string
  name: string
  description: string
  sourceText: string
  blockChain: Block[]
  tags: string[]
  sourceType: string
  useCount: number
  createTime: string
  updateTime: string
}

// ============================================================
// Admin — Tiers
// ============================================================
export interface Tier {
  id: string
  name: string
  displayName: string
  priceMonthly: number
  monthlyApiQuota: number | null
  monthlyTokenQuota: number | null
  maxConcurrentSessions: number
  maxApiPerSession: number
  maxTokenPerSession: number
  maxProductsPerQuery: number
  maxSavedPlans: number
  sortOrder: number
  active: boolean
}

// ============================================================
// Admin — Echotik API Keys
// ============================================================
export interface EchotikApiKey {
  id: string
  name: string
  apiKeyMasked: string
  apiSecretMasked: string
  totalCalls: number | null
  remainingCalls: number | null
  alertThreshold: number
  active: boolean
  belowThreshold: boolean
  lastUsedTime: string | null
}

export interface EchotikApiKeyForm {
  name: string
  apiKey: string
  apiSecret: string
  totalCalls: number | null | undefined
  remainingCalls: number | null | undefined
  alertThreshold: number
  active: boolean
}

// ============================================================
// Admin — Presets
// ============================================================
export interface PresetPackage {
  id: string
  pkgCode: string
  nameZh: string
  nameEn: string
  description: string
  blockChain: Block[]
  tags: string[]
  useCount: number
  sortOrder: number
  active: boolean
}

export interface PresetPackageForm {
  pkgCode: string
  nameZh: string
  nameEn: string
  description: string
  blockChain: Block[]
  tags: string[]
  sortOrder: number
  active: boolean
}

// ============================================================
// Admin — LLM Config
// ============================================================
export type LlmProvider = 'openai' | 'anthropic' | 'deepseek' | 'openrouter' | 'siliconflow' | 'other'

export interface LlmConfig {
  id: string
  name: string
  provider: LlmProvider
  baseUrl: string
  apiKeyMasked: string
  model: string
  maxTokens: number
  priority: number
  active: boolean
}

export interface LlmConfigForm {
  name: string
  provider: LlmProvider
  baseUrl: string
  apiKey: string
  model: string
  maxTokens: number
  priority: number
  active: boolean
}

// ============================================================
// Admin — Dashboard
// ============================================================
export interface DashboardOverview {
  totalUsers: number
  todayNewUsers: number
  totalSessions: number
  todaySessions: number
  runningSessions: number
  totalEchotikCalls: number
  totalLlmTokens: number
  apiKeyPoolStatus: {
    total: number
    active: number
    lowQuotaCount: number
  }
}

// ============================================================
// Intent parsing
// ============================================================
export interface IntentParseRequest {
  userText: string
  sessionContext?: { blockChain: Block[] }
  buildSessionId?: string
  agentThreadId?: string
  conversationSummary?: string
  qaHistory?: { q: string; a: string }[]
}

export interface PlanDraft {
  market: string
  category: string
  price_range: string
  filters?: string
  scoring_dimensions?: string
  output_count?: number
  strategy_notes?: string
}

export interface IntentParseResponse {
  success: boolean
  type?: 'block_chain' | 'needs_input' | 'action' | 'plan_draft'
  agentThreadId?: string
  blockChain?: Block[]
  partialBlockChain?: Block[]
  action?: string
  summary?: string
  conversationSummary?: string
  ambiguities?: string[]
  llmTokensUsed?: number
  iterations?: number
  message?: string
  suggestions?: string[]
  plan?: PlanDraft
}

export interface PreviewResponse {
  hasData: boolean
  sampleCount: number
  blockId: string
  status: 'ok' | 'empty' | 'skipped' | 'error'
  message: string
}

export interface InterpretResponse {
  success: boolean
  interpretation: string
  llmTokensUsed?: number
  message?: string
}
