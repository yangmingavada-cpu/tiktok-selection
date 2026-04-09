<template>
  <div class="new-session-page">
    <div class="page-header">
      <el-button :icon="ArrowLeft" text @click="$router.back()">返回</el-button>
      <h2>新建选品任务</h2>
    </div>

    <div class="main-layout">
      <SessionHistoryRail
        :sessions="historySessions"
        :loading="historyLoading"
        :hydrated-from-cache="historyHydratedFromCache"
        :cache-updated-at="historyCacheUpdatedAt"
        @refresh="refreshHistorySessions"
        @delete-session="handleDeleteSessionSoft"
        @restore-conversation="handleRestoreConversation"
        @create-new="resetConversation"
      />

      <!-- 左：AI 对话区 -->
      <div class="chat-panel">
        <div class="chat-header">
          <el-icon class="ai-icon"><MagicStick /></el-icon>
          <span>AI 选品助手</span>
          <el-tag v-if="pendingPlan" type="warning" size="small" style="margin-left:auto">
            待确认方案
          </el-tag>
        </div>

        <div class="chat-messages" ref="messagesRef">
          <ChatMessage
            v-for="(msg, i) in messages"
            :key="i"
            :msg="msg"
            :creating="creating"
            :confirming-draft="confirmingDraft"
            @reject-plan="rejectPlanWithGuide"
            @confirm-plan="confirmPlan"
            @confirm-draft="handleConfirmDraft"
            @reject-draft="handleRejectDraft"
            @apply-audit-suggestions="submitPrompt"
          />

          <OnboardingGuide
            v-if="showOnboarding"
            :disabled="thinking"
            @fill-prompt="fillStarterPrompt"
            @run-prompt="runStarterPrompt"
          />

          <PlanningProgressBubble
            v-if="thinking && planningStatus !== 'idle'"
            :status="planningStatus"
            :steps="steps"
            :thinking-text="thinkingText"
            :trace-entries="planningTrace"
            :session-id="planningAgentThreadId || planningSessionId"
          />

          <!-- AI 思考中 -->
          <div v-if="false && thinking" class="message-row ai">
            <div class="avatar">🤖</div>
            <div class="bubble-wrap">
              <div class="bubble ai progress-bubble">
                <div v-if="steps.length === 0" class="thinking-dots"><span /><span /><span /></div>
                <div v-else class="step-list">
                  <div v-for="(step, i) in steps" :key="i" class="step-item" :class="step.success ? 'done' : 'fail'">
                    <span class="step-icon">{{ step.success ? '✓' : '✗' }}</span>
                    <span class="step-label">{{ step.label }}</span>
                  </div>
                  <div class="step-item running">
                    <span class="step-icon spin">⟳</span>
                    <span class="step-label">AI 正在思考下一步...</span>
                  </div>
                  <div v-if="thinkingText" class="thinking-preview">{{ thinkingText }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input-area">
          <el-input
            v-model="inputText"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 8 }"
            placeholder="先试预设方案，或直接描述你的需求，例如：泰国家居品类，近期销量增长快、评分高的商品，推荐Top20"
            :disabled="thinking"
            resize="none"
            @keydown.enter.exact.prevent="handleSend"
          />
          <el-button
            type="primary"
            :loading="thinking"
            :disabled="!inputText.trim() || thinking"
            class="send-btn"
            @click="handleSend"
          >
            <el-icon><Position /></el-icon>
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>

  <AskUserDialog
    :dialog="askDialog"
    @select-option="handleAskOption"
    @custom-reply="handleAskCustom"
    @close="askDialog.visible = false"
  />

  <ResultDialog
    :visible="resultDialogVisible"
    :session="resultSession"
    @close="resultDialogVisible = false"
    @export-excel="exportExcel"
    @save-plan="savePlan"
    @continue-tuning="resultDialogVisible = false; rejectPlanWithGuide()"
  />
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, MagicStick, Position } from '@element-plus/icons-vue'
import { parseIntent, previewBlockChain, interpretBlockChainStream, confirmPlanDraft } from '@/api/intent'
import { createSession, executeSession, exportSessionExcel, getSession, removeSession, saveConversationSnapshot } from '@/api/session'
import type { Block, PreviewResponse, Session as ApiSession } from '@/types'
import { useUserStore } from '@/stores/user'
import { useSessionHistory, getCachedSessionDetail } from '@/composables/useSessionHistory'

import ChatMessage from './new-session/chat-message.vue'
import AskUserDialog from './new-session/ask-user-dialog.vue'
import OnboardingGuide from './new-session/onboarding-guide.vue'
import PlanningProgressBubble from './new-session/planning-progress-bubble.vue'
import ResultDialog from './new-session/result-dialog.vue'
import SessionHistoryRail from './new-session/session-history-rail.vue'
import { GREETING } from './new-session/constants'
import { answerPlanQuestion, buildPlanAdjustmentGuide, buildPlanInterpretation } from './new-session/plan-advisor'
import type {
  AskDialogState,
  ChatMessage as ChatMsg,
  ExecSession,
  PlanningStatus,
  PlanningSnapshot,
  PlanningTraceEntry,
  StepItem,
} from './new-session/types'

// ── State ───────────────────────────────────────────
const messagesRef = ref<HTMLElement>()
const inputText = shallowRef('')
const thinking = shallowRef(false)
const creating = shallowRef(false)
const pendingPlan = shallowRef(false)
const messages = ref<ChatMsg[]>([])
const steps = ref<StepItem[]>([])
const thinkingText = shallowRef('')
let eventSource: EventSource | null = null
const lastBlockChain = shallowRef<Block[] | null>(null)

const askDialog = ref<AskDialogState>({ visible: false, question: '', suggestions: [] })
const qaHistory = ref<{ q: string; a: string }[]>([])
const originalRequest = shallowRef('')
const PLANNING_IDLE_TIMEOUT_MS = 200_000
let planningActivityTimer: number | null = null
let activeBuildSessionId = ''
const planningStatus = shallowRef<PlanningStatus>('idle')
const planningSessionId = shallowRef('')
const planningThreadId = shallowRef('')
const planningAgentThreadId = shallowRef('')
const planningConversationSummary = shallowRef('')
// 方案生成后保存线程ID，供"继续调整"复用（resetPlanningThread 会清空 planningThreadId）
const lastPlanThreadId = shallowRef('')
const lastPlanAgentThreadId = shallowRef('')
const confirmingDraft = shallowRef(false)
const planningTrace = ref<PlanningTraceEntry[]>([])
let planningTraceId = 0
let lastThinkingTraceText = ''
let archivedPlanningSessionId = ''
const userStore = useUserStore()
const conversationCacheKey = computed(() => userStore.userId || userStore.token || 'anonymous')
const {
  sessions: allConversations,
  loading: historyLoading,
  hydratedFromCache: historyHydratedFromCache,
  cacheUpdatedAt: historyCacheUpdatedAt,
  refresh: refreshHistorySessions,
  prependSession,
  removeSessionFromHistory,
} = useSessionHistory(conversationCacheKey, {
  statusFilter: ['created', 'in_progress', 'paused', 'completed', 'failed'],
  cachePrefix: 'conversation-history'
})

// ── 新建对话：重置全部状态 ────────────────────────
function resetConversation() {
  finishPlanning()
  resetPlanningThread()
  eventSource?.close()
  eventSource = null
  execEventSource?.close()
  clearResultSyncLoop()

  messages.value = []
  steps.value = []
  lastBlockChain.value = null
  pendingPlan.value = false
  qaHistory.value = []
  inputText.value = ''
  thinking.value = false
  creating.value = false
  thinkingText.value = ''
  confirmingDraft.value = false
  planningTrace.value = []
  originalRequest.value = ''
  lastPlanThreadId.value = ''
  lastPlanAgentThreadId.value = ''
  archivedPlanningSessionId = ''

  addMessage({ role: 'ai', text: GREETING, isGreeting: true })
}

// 对话历史：只显示未执行或规划中的会话
const historySessions = computed(() => allConversations.value)

const showOnboarding = computed(() =>
  !thinking.value &&
  !pendingPlan.value &&
  messages.value.length === 1 &&
  Boolean(messages.value[0]?.isGreeting),
)

const resultSession = ref<ExecSession | null>(null)
const resultDialogVisible = shallowRef(false)
let execEventSource: EventSource | null = null
let resultSyncTimer: number | null = null
let resultSyncInFlight = false
let resultSyncAttempts = 0

// ── Helpers ─────────────────────────────────────────
function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

function addMessage(msg: ChatMsg) {
  messages.value.push(msg)
  scrollToBottom()
}

function getLatestPlanMessage(): ChatMsg | undefined {
  return [...messages.value].reverse().find(msg => Array.isArray(msg.plan) && msg.plan.length > 0)
}

function getLatestUserRequest(): string {
  return [...messages.value].reverse().find(msg => msg.role === 'user' && msg.text.trim())?.text.trim() || ''
}

function mapExecStatus(status?: string): ExecSession['status'] {
  if (status === 'paused') return 'paused'
  if (status === 'failed' || status === 'cancelled') return 'failed'
  if (status === 'completed') return 'completed'
  return 'running'
}

function applyResultCurrentView(detail: ApiSession) {
  if (!resultSession.value) {
    resultSession.value = {
      id: detail.id,
      status: mapExecStatus(detail.status),
      dataState: 'pending',
      syncState: 'idle',
      lastSyncedAt: null,
      steps: [],
      rows: [],
      dims: [],
      totalRows: 0,
      errorMsg: null,
    }
  }

  const target = resultSession.value
  target.status = mapExecStatus(detail.status)
  target.lastSyncedAt = Date.now()

  if (detail.currentView) {
    target.rows = detail.currentView.data || []
    target.dims = detail.currentView.dims || []
    target.totalRows = detail.currentView.totalCount ?? detail.currentView.data?.length ?? 0
    target.dataState = target.totalRows > 0 ? 'ready' : 'empty'
  } else if (target.status === 'completed' || target.status === 'paused') {
    target.dataState = 'pending'
  }

  if (detail.auditResult) {
    target.auditResult = detail.auditResult
  }
}

async function syncResultSession(sessionId: string) {
  const target = resultSession.value
  if (!target || target.id !== sessionId) return
  target.syncState = 'syncing'
  try {
    const res = await getSession(sessionId)
    if (res.data) {
      applyResultCurrentView(res.data)
    }
  } finally {
    if (resultSession.value?.id === sessionId) {
      resultSession.value.syncState = 'idle'
    }
  }
}

function hasRenderableResult(session: ExecSession | null): boolean {
  if (!session) return false
  return session.rows.length > 0 || session.dims.length > 0 || session.totalRows > 0
}

function clearResultSyncLoop() {
  if (resultSyncTimer !== null) {
    window.clearTimeout(resultSyncTimer)
    resultSyncTimer = null
  }
  resultSyncAttempts = 0
}

function scheduleResultSync(sessionId: string, delayMs = 0) {
  clearResultSyncLoop()
  resultSyncTimer = window.setTimeout(() => {
    void runResultSyncLoop(sessionId)
  }, delayMs)
}

async function runResultSyncLoop(sessionId: string) {
  const target = resultSession.value
  if (!target || target.id !== sessionId || resultSyncInFlight) return

  resultSyncInFlight = true
  try {
    await syncResultSession(sessionId)
    resultSyncAttempts += 1
  } finally {
    resultSyncInFlight = false
  }

  const latest = resultSession.value
  if (!latest || latest.id !== sessionId) return

  if (hasRenderableResult(latest)) {
    latest.dataState = 'ready'
    clearResultSyncLoop()
    return
  }

  if (latest.status === 'failed') {
    clearResultSyncLoop()
    return
  }

  const maxAttempts = latest.status === 'running' ? 90 : 25
  if (resultSyncAttempts >= maxAttempts) {
    latest.dataState =
      latest.status === 'completed' || latest.status === 'paused'
        ? 'empty'
        : 'pending'
    clearResultSyncLoop()
    return
  }

  scheduleResultSync(sessionId, latest.status === 'running' ? 1200 : 800)
}

async function openResultDialog() {
  const sessionId = resultSession.value?.id
  if (!sessionId) return
  if (resultSession.value) {
    resultSession.value.dataState = hasRenderableResult(resultSession.value) ? 'ready' : 'pending'
  }
  resultDialogVisible.value = true
  scheduleResultSync(sessionId)
}

function fillStarterPrompt(prompt: string) {
  if (thinking.value) return
  inputText.value = prompt
  scrollToBottom()
}

function appendPlanningTrace(
  kind: PlanningTraceEntry['kind'],
  text: string,
  success?: boolean,
) {
  const normalized = text.trim()
  if (!normalized) return
  planningTrace.value.push({
    id: ++planningTraceId,
    kind,
    text: normalized,
    createdAt: Date.now(),
    success,
  })
  scrollToBottom()
}

function updateThinkingTrace(text: string) {
  const normalized = text.trim()
  if (!normalized || normalized === lastThinkingTraceText) return
  lastThinkingTraceText = normalized
  const lastEntry = planningTrace.value[planningTrace.value.length - 1]
  if (lastEntry?.kind === 'thinking') {
    lastEntry.text = normalized
    lastEntry.createdAt = Date.now()
  } else {
    appendPlanningTrace('thinking', normalized)
  }
}

function archivePlanningSnapshot(status: Exclude<PlanningStatus, 'idle'>) {
  const sessionId = planningSessionId.value
  if (!sessionId || archivedPlanningSessionId === sessionId) return
  const snapshot: PlanningSnapshot = {
    status,
    sessionId,
    thinkingText: thinkingText.value,
    steps: steps.value.map(step => ({ ...step })),
    traceEntries: planningTrace.value.map(entry => ({ ...entry })),
  }
  archivedPlanningSessionId = sessionId
  addMessage({ role: 'ai', text: '', planningSnapshot: snapshot })
}

function clearPlanningActivityTimer() {
  if (planningActivityTimer !== null) {
    window.clearTimeout(planningActivityTimer)
    planningActivityTimer = null
  }
}

function closePlanningStream() {
  eventSource?.close()
  eventSource = null
}

function finishPlanning() {
  thinking.value = false
  clearPlanningActivityTimer()
  closePlanningStream()
  activeBuildSessionId = ''
  planningStatus.value = 'idle'
  planningSessionId.value = ''
}

function resetPlanningThread() {
  console.log('[Thread] resetPlanningThread: planningThreadId cleared, agentThreadId preserved =', planningAgentThreadId.value)
  planningThreadId.value = ''
  // agentThreadId 和 conversationSummary 不清空，保证整个对话生命周期内可续接
  // 只有 resetConversation()（新建对话）才会全部清空
}

function updatePlanningThreadMeta(data?: { agentThreadId?: string; conversationSummary?: string }) {
  const prev = planningAgentThreadId.value
  if (data?.agentThreadId) planningAgentThreadId.value = data.agentThreadId
  if (typeof data?.conversationSummary === 'string') {
    planningConversationSummary.value = data.conversationSummary
  }
  console.log('[Thread] updatePlanningThreadMeta:', { prev, new: planningAgentThreadId.value, fromResponse: data?.agentThreadId })
}

function schedulePlanningGuard(sessionId: string) {
  clearPlanningActivityTimer()
  planningActivityTimer = window.setTimeout(() => {
    if (!thinking.value || activeBuildSessionId !== sessionId) return
    planningStatus.value = 'failed'
    appendPlanningTrace('error', `本轮规划在 ${PLANNING_IDLE_TIMEOUT_MS / 1000} 秒内没有新的进展。`)
    archivePlanningSnapshot('failed')
    finishPlanning()
    addMessage({ role: 'ai', text: 'AI 规划暂时超时，请重试，或把条件拆得更具体一点。' })
  }, PLANNING_IDLE_TIMEOUT_MS)
}

function startPlanning(sessionId: string) {
  thinking.value = true
  pendingPlan.value = false
  steps.value = []
  thinkingText.value = ''
   planningStatus.value = 'running'
   planningSessionId.value = sessionId
  planningTrace.value = []
  planningTraceId = 0
  lastThinkingTraceText = ''
  archivedPlanningSessionId = ''
  activeBuildSessionId = sessionId
   appendPlanningTrace('status', '已创建规划会话，等待 AI 给出第一步。')
   openProgressStream(sessionId)
   schedulePlanningGuard(sessionId)
}

function touchPlanning(sessionId: string) {
  if (thinking.value && activeBuildSessionId === sessionId) {
    schedulePlanningGuard(sessionId)
  }
}

function buildContextText(newMsg: string): string {
  const history = messages.value
    .filter(m => !m.isGreeting)
    .slice(-8)
    .map(m => {
      if (m.role === 'user') return `用户: ${m.text}`
      if (m.plan) {
        const planDesc = m.summary ? m.summary.slice(0, 150) : `含 ${m.plan.length} 个步骤的选品方案`
        return `AI助手: 提出了方案（${planDesc}），用户要求继续调整`
      }
      return `AI助手: ${(m.text || m.summary || '').slice(0, 120).replaceAll('\n', ' ')}`
    })
    .join('\n')
  return history ? `[对话历史]\n${history}\n---\n当前请求: ${newMsg}` : newMsg
}

function openProgressStream(sessionId: string) {
  closePlanningStream()
  steps.value = []
  thinkingText.value = ''
  const es = new EventSource(`/api/intent/progress/${sessionId}`)
  eventSource = es
  es.addEventListener('step', (e: MessageEvent) => {
    try {
      const d = JSON.parse(e.data)
      touchPlanning(sessionId)
      const label = d.label ?? d.tool ?? '已完成一步'
      const success = d.success !== false
      steps.value.push({ label, success })
      appendPlanningTrace('step', label, success)
      thinkingText.value = ''
      scrollToBottom()
    } catch { /* skip */ }
  })
  es.addEventListener('thinking', (e: MessageEvent) => {
    try {
      const d = JSON.parse(e.data)
      touchPlanning(sessionId)
      thinkingText.value = d.text || ''
      updateThinkingTrace(d.text || '')
      scrollToBottom()
    } catch { /* skip */ }
  })
  es.addEventListener('done', () => {
    appendPlanningTrace('status', '规划阶段的 SSE 已结束。')
    if (activeBuildSessionId === sessionId) finishPlanning()
  })
  es.addEventListener('error', (e: MessageEvent) => {
    try {
      const d = JSON.parse(e.data)
      planningStatus.value = 'failed'
      steps.value.push({ label: d.message || '规划失败', success: false })
      thinkingText.value = d.message || ''
      appendPlanningTrace('error', d.message || '规划失败')
      archivePlanningSnapshot('failed')
      scrollToBottom()
    } catch { /* skip */ }
    if (activeBuildSessionId === sessionId) finishPlanning()
  })
  es.onerror = () => {
    if (activeBuildSessionId === sessionId) finishPlanning()
  }
}

function generateBuildSessionId(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), b => b.toString(16).padStart(2, '0')).join('')
}

// ── Handle plan result from AI ──────────────────────
function handlePlanResult(data: { blockChain: Block[]; summary?: string; llmTokensUsed?: number }, sourceText: string) {
  planningStatus.value = 'completed'
  appendPlanningTrace('status', '规划完成，已生成可确认的方案。')
  archivePlanningSnapshot('completed')
  finishPlanning()

  // 保存规划线程信息用于解读蒸馏和"继续调整"复用（resetPlanningThread 会清空）
  const savedThreadId = planningThreadId.value
  const savedAgentThreadId = planningAgentThreadId.value
  lastPlanThreadId.value = savedThreadId
  lastPlanAgentThreadId.value = savedAgentThreadId
  console.log('[handlePlanResult] saved threads:', { planningThreadId: savedThreadId, agentThreadId: savedAgentThreadId })

  resetPlanningThread()
  lastBlockChain.value = data.blockChain
  qaHistory.value = []
  originalRequest.value = ''
  const msgIndex = messages.value.length
    addMessage({
      role: 'ai',
      text: '',
      plan: data.blockChain,
      summary: data.summary || '',
      tokens: data.llmTokensUsed ?? 0,
      sourceText,
      preview: 'loading',
      interpretation: 'loading',
      interpretationDone: false,
    })
    pendingPlan.value = true

    // 并行启动：预览数据 + AI 流式解读
    previewBlockChain(data.blockChain)
      .then(res => {
        messages.value[msgIndex].preview = res.data ?? null
      })
      .catch(() => { messages.value[msgIndex].preview = null })

    // AI 流式解读（失败则 fallback 到本地模板）
    let streamedText = ''
    interpretBlockChainStream(
      data.blockChain,
      (token: string) => {
        streamedText += token
        messages.value[msgIndex].interpretation = streamedText
      },
      () => {
        messages.value[msgIndex].interpretation = streamedText || buildPlanInterpretation(data.blockChain)
        messages.value[msgIndex].interpretationDone = true
      },
      (err: string) => {
        console.warn('AI interpretation failed, using local template:', err)
        messages.value[msgIndex].interpretation = buildPlanInterpretation(data.blockChain)
        messages.value[msgIndex].interpretationDone = true
      },
      {
        userId: userStore.userId,
        sessionId: savedThreadId || undefined,
        agentThreadId: savedAgentThreadId || undefined,
      }
    )
  }

function handleNeedsInput(data: { message?: string; suggestions?: string[]; partialBlockChain?: Block[] }, text: string) {
  planningStatus.value = 'needs_input'
  if (!originalRequest.value) originalRequest.value = text
  if (!planningThreadId.value) planningThreadId.value = planningSessionId.value || activeBuildSessionId
  lastBlockChain.value = data.partialBlockChain ?? lastBlockChain.value
  const question = data.message || '请补充信息以继续规划'
  appendPlanningTrace('status', 'AI 判断当前信息还不够，准备向用户追问。')
  appendPlanningTrace('step', question, false)
  archivePlanningSnapshot('needs_input')
  finishPlanning()
  addMessage({ role: 'ai', text: question })
  askDialog.value = { visible: true, question, suggestions: data.suggestions || [] }
}

// ── Send message ────────────────────────────────────
async function submitPrompt(rawText: string) {
  const text = rawText.trim()
  if (!text || thinking.value) return

  resetPlanningThread()
  inputText.value = ''
  addMessage({ role: 'user', text })

  const buildSessionId = generateBuildSessionId()
  planningThreadId.value = buildSessionId
  startPlanning(buildSessionId)

  const sentAgentThreadId = planningAgentThreadId.value || lastPlanAgentThreadId.value || undefined
  console.log('[submitPrompt] sending:', { buildSessionId, agentThreadId: sentAgentThreadId, hasBlockChain: !!lastBlockChain.value, qaHistoryLen: qaHistory.value.length })

  try {
  const contextText = buildContextText(text)
  const res = await parseIntent({
      userText: contextText,
      buildSessionId,
      agentThreadId: sentAgentThreadId,
      conversationSummary: planningConversationSummary.value || undefined,
      sessionContext: lastBlockChain.value ? { blockChain: lastBlockChain.value } : undefined,
      qaHistory: qaHistory.value.length > 0 ? qaHistory.value : undefined,
    })
    const data = res.data
    console.log('[submitPrompt] response:', { type: data?.type, success: data?.success, agentThreadId: data?.agentThreadId, hasBlockChain: !!data?.blockChain?.length })

    if (data?.type === 'plan_draft' && data.plan) {
      updatePlanningThreadMeta(data)
      finishPlanning()
      addMessage({ role: 'ai', text: '', planDraft: data.plan })
      return
    }
    if (data?.type === 'needs_input') {
      updatePlanningThreadMeta(data)
      handleNeedsInput(data, text)
      return
    }
    if (data?.success && data?.blockChain?.length) {
      updatePlanningThreadMeta(data)
      handlePlanResult({ blockChain: data.blockChain, summary: data.summary, llmTokensUsed: data.llmTokensUsed }, text)
    } else if (data?.success && data?.message) {
      // Type A 回复：AI 用文字回答问题，没有返回 blockChain（不是失败）
      console.log('[submitPrompt] text response (Type A):', data.message.substring(0, 80))
      updatePlanningThreadMeta(data)
      finishPlanning()
      addMessage({ role: 'ai', text: data.message })
    } else {
      console.warn('[submitPrompt] no blockChain, treating as failed:', data?.message)
      planningStatus.value = 'failed'
      originalRequest.value = ''
      resetPlanningThread()
      appendPlanningTrace('error', data?.message || '规划未生成有效方案。')
      archivePlanningSnapshot('failed')
      addMessage({ role: 'ai', text: data?.message || '方案未生成，请尝试更具体地描述你的需求。' })
      finishPlanning()
    }
  } catch {
    console.error('[submitPrompt] request failed')
    planningStatus.value = 'failed'
    originalRequest.value = ''
    resetPlanningThread()
    appendPlanningTrace('error', '网络请求失败，请检查连接后重试。')
    archivePlanningSnapshot('failed')
    addMessage({ role: 'ai', text: '网络请求失败，请检查连接后重试。' })
    finishPlanning()
  }
}

// tryAnswerPendingPlanQuestion 已移除：所有消息统一发到后端 AI 处理
// 后端意图识别模块（类型A/B/C）区分询问方案和要求调整

async function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  await submitPrompt(text)
}

async function runStarterPrompt(prompt: string) {
  await submitPrompt(prompt)
}

// ── Ask user flow ───────────────────────────────────
function handleAskOption(option: string) {
  const question = askDialog.value.question
  askDialog.value.visible = false
  qaHistory.value.push({ q: question, a: option })
  sendAskReply(option)
}

function handleAskCustom(text: string) {
  const question = askDialog.value.question
  askDialog.value.visible = false
  qaHistory.value.push({ q: question, a: text })
  sendAskReply(text)
}

async function sendAskReply(answer: string) {
  addMessage({ role: 'user', text: answer })

  // 优先复用上一轮规划的 thread ID（"继续调整"场景），其次用当前规划 ID，最后新建
  const buildSessionId = planningThreadId.value || lastPlanThreadId.value || generateBuildSessionId()
  planningThreadId.value = buildSessionId
  startPlanning(buildSessionId)

  const sentAgentThreadId = planningAgentThreadId.value || lastPlanAgentThreadId.value || undefined
  console.log('[sendAskReply] sending:', { buildSessionId, agentThreadId: sentAgentThreadId, answer: answer.substring(0, 50) })

  try {
  const res = await parseIntent({
      userText: answer,
      buildSessionId,
      agentThreadId: sentAgentThreadId,
      conversationSummary: planningConversationSummary.value || undefined,
      qaHistory: qaHistory.value,
      sessionContext: lastBlockChain.value ? { blockChain: lastBlockChain.value } : undefined,
    })
    const data = res.data

    if (data?.type === 'plan_draft' && data.plan) {
      updatePlanningThreadMeta(data)
      finishPlanning()
      addMessage({ role: 'ai', text: '', planDraft: data.plan })
      return
    }
    if (data?.type === 'needs_input') {
      updatePlanningThreadMeta(data)
      handleNeedsInput(data, answer)
      return
    }
    if (data?.success && data?.blockChain?.length) {
      updatePlanningThreadMeta(data)
      handlePlanResult({ blockChain: data.blockChain, summary: data.summary, llmTokensUsed: data.llmTokensUsed }, originalRequest.value || answer)
    } else if (data?.success && data?.message) {
      // Type A 回复：AI 用文字回答问题
      console.log('[sendAskReply] text response (Type A):', data.message.substring(0, 80))
      updatePlanningThreadMeta(data)
      finishPlanning()
      addMessage({ role: 'ai', text: data.message })
    } else {
      planningStatus.value = 'failed'
      qaHistory.value = []
      originalRequest.value = ''
      resetPlanningThread()
      appendPlanningTrace('error', data?.message || '规划未生成有效方案。')
      archivePlanningSnapshot('failed')
      addMessage({ role: 'ai', text: data?.message || '方案未生成，请尝试更具体地描述你的需求。' })
      finishPlanning()
    }
  } catch {
    planningStatus.value = 'failed'
    qaHistory.value = []
    originalRequest.value = ''
    resetPlanningThread()
    appendPlanningTrace('error', '网络请求失败，请检查连接后重试。')
    archivePlanningSnapshot('failed')
    addMessage({ role: 'ai', text: '网络请求失败，请检查连接后重试。' })
    finishPlanning()
  }
}

// ── Plan actions ────────────────────────────────────
function rejectPlan() {
  const adjustingPendingPlan = pendingPlan.value
  pendingPlan.value = false
  qaHistory.value = []
  resetPlanningThread()
  const latestPlan = getLatestPlanMessage()
  const latestPreview = latestPlan?.preview
  const previewEmpty = latestPreview !== 'loading' && latestPreview?.status === 'empty'
  const resultEmpty =
    !adjustingPendingPlan &&
    resultSession.value?.dataState === 'empty'

  originalRequest.value = latestPlan?.sourceText?.trim() || getLatestUserRequest()

  let question = '你想先调整哪一项？我可以基于当前方案继续微调。'
  let suggestions = ['改价格区间', '改评分权重', '改推荐数量', '换个数据源']

  if (previewEmpty || resultEmpty) {
    question = '这版方案结果偏少，我来带你放宽条件。你想先调哪一项？'
    suggestions = ['放宽价格区间', '降低销量门槛', '降低评分要求', '扩大品类范围']
  }

  addMessage({ role: 'ai', text: question })
  askDialog.value = {
    visible: true,
    question,
    suggestions,
  }
}
void rejectPlan

function rejectPlanWithGuide() {
  console.log('[rejectPlanWithGuide] agentThreadId preserved:', planningAgentThreadId.value, 'lastPlan:', lastPlanAgentThreadId.value)
  const adjustingPendingPlan = pendingPlan.value
  pendingPlan.value = false
  qaHistory.value = []
  // 不调 resetPlanningThread()，保留 planningAgentThreadId 以便 AI 续接上下文

  const latestPlan = getLatestPlanMessage()
  const latestPreview = latestPlan?.preview
  const previewEmpty = latestPreview !== 'loading' && latestPreview?.status === 'empty'
  const resultEmpty =
    !adjustingPendingPlan &&
    resultSession.value?.dataState === 'empty'

  originalRequest.value = latestPlan?.sourceText?.trim() || getLatestUserRequest()

  const guide = latestPlan?.plan?.length
    ? buildPlanAdjustmentGuide(
        latestPlan.plan,
        previewEmpty || resultEmpty
          ? ({ status: 'empty', message: '当前结果偏少', sampleCount: 0, hasData: false, blockId: '' } as PreviewResponse)
          : latestPreview && latestPreview !== 'loading'
            ? latestPreview
            : null,
      )
    : null

  const question = guide?.question || '我已经按当前方案拆出了可调整项。你想先改哪一部分？'
  const suggestions = guide?.suggestions || ['改目标市场', '改筛选条件', '改评分权重', '改推荐数量']

  addMessage({ role: 'ai', text: question })
  askDialog.value = {
    visible: true,
    question,
    suggestions,
  }
}

async function confirmPlan(plan: unknown[], sourceText?: string) {
  console.log('[confirmPlan] agentThreadId:', planningAgentThreadId.value || lastPlanAgentThreadId.value)
  creating.value = true
  try {
    const titleSrc = sourceText || originalRequest.value || ''
    const res = await createSession({
      blockChain: plan as Block[],
      sourceText: titleSrc,
      title: titleSrc.substring(0, 30) || undefined,
      agentThreadId: planningAgentThreadId.value || lastPlanAgentThreadId.value || undefined,
    })
    const sessionId = res.data?.id
    pendingPlan.value = false
    planningThreadId.value = ''
    // 保留 agentThreadId，执行完成后用户还能继续对话
    lastBlockChain.value = plan as Block[]
    if (res.data) {
      prependSession(res.data)
    }
    if (sessionId) {
      void saveConversationSnapshotToSession(sessionId)
      const card: ExecSession = {
        id: sessionId,
        status: 'running',
        dataState: 'pending',
        syncState: 'idle',
        lastSyncedAt: null,
        steps: [],
        rows: [],
        dims: [],
        totalRows: 0,
        errorMsg: null,
      }
      resultSession.value = card
      addMessage({ role: 'ai', text: '', execCard: card })
      scheduleResultSync(sessionId, 300)
      openExecutionStream(sessionId)
      await executeSession(sessionId)
    }
  } catch {
    ElMessage.error('创建失败，请重试')
    addMessage({ role: 'ai', text: '❌ 创建任务失败，请重试。' })
  } finally {
    creating.value = false
  }
}

async function handleConfirmDraft() {
  confirmingDraft.value = true
  const buildSessionId = planningThreadId.value || lastPlanThreadId.value || generateBuildSessionId()
  planningThreadId.value = buildSessionId
  startPlanning(buildSessionId)
  const draftMsg = messages.value.findLast(m => m.planDraft)
  try {
    const res = await confirmPlanDraft({
      agentThreadId: planningAgentThreadId.value || lastPlanAgentThreadId.value || undefined,
      buildSessionId: buildSessionId || undefined,
      conversationSummary: planningConversationSummary.value || undefined,
      userText: originalRequest.value || undefined,
      qaHistory: qaHistory.value.length ? qaHistory.value : undefined,
      plan: draftMsg?.planDraft || undefined,
    })
    const data = res.data
    if (data?.type === 'plan_draft' && data.plan) {
      updatePlanningThreadMeta(data)
      finishPlanning()
      // 替换最后一条草稿消息
      const idx = messages.value.findLastIndex(m => m.planDraft)
      if (idx >= 0) messages.value[idx] = { role: 'ai', text: '', planDraft: data.plan }
    } else if (data?.type === 'needs_input') {
      updatePlanningThreadMeta(data)
      handleNeedsInput(data, '')
    } else if (data?.success && data?.blockChain?.length) {
      updatePlanningThreadMeta(data)
      // 移除草稿消息
      const idx = messages.value.findLastIndex(m => m.planDraft)
      if (idx >= 0) messages.value.splice(idx, 1)
      handlePlanResult({ blockChain: data.blockChain, summary: data.summary, llmTokensUsed: data.llmTokensUsed }, originalRequest.value)
    } else {
      planningStatus.value = 'failed'
      appendPlanningTrace('error', data?.message || '规划确认失败。')
      archivePlanningSnapshot('failed')
      addMessage({ role: 'ai', text: data?.message || '规划确认失败，请重试。' })
      finishPlanning()
    }
  } catch {
    planningStatus.value = 'failed'
    appendPlanningTrace('error', '网络请求失败，请检查连接后重试。')
    archivePlanningSnapshot('failed')
    addMessage({ role: 'ai', text: '规划确认请求失败，请重试。' })
    finishPlanning()
  } finally {
    confirmingDraft.value = false
  }
}

function handleRejectDraft() {
  const idx = messages.value.findLastIndex(m => m.planDraft)
  if (idx >= 0) messages.value.splice(idx, 1)
  // 保留线程 ID，AI 可续接 checkpoint 上下文继续调整（同 rejectPlanWithGuide）
  lastPlanThreadId.value = planningThreadId.value || lastPlanThreadId.value
  lastPlanAgentThreadId.value = planningAgentThreadId.value || lastPlanAgentThreadId.value
  resetPlanningThread()
  addMessage({ role: 'ai', text: '好的，请重新描述你的选品需求。' })
}

async function saveConversationSnapshotToSession(sessionId: string) {
  try {
    const snapshot = {
      messages: messages.value
        .filter(m => m.role === 'user' || (m.role === 'ai' && (m.text || m.plan)))
        .map(m => ({
          role: m.role,
          text: m.text || '',
          plan: m.plan,
          interpretation: typeof m.interpretation === 'string' ? m.interpretation : undefined,
        })),
      qaHistory: qaHistory.value,
      planningSummary: getLatestPlanMessage()?.summary || '',
      savedAt: new Date().toISOString(),
    }
    await saveConversationSnapshot(sessionId, snapshot)
  } catch {
    // 快照保存失败不影响主流程
  }
}

// ── Execution SSE ───────────────────────────────────
function openExecutionStream(sessionId: string) {
  execEventSource?.close()
  scheduleResultSync(sessionId, 300)
  const es = new EventSource(`/api/sessions/${sessionId}/subscribe`)
  execEventSource = es

  es.addEventListener('step_start', (e: MessageEvent) => {
    try {
      const d = JSON.parse(e.data)
      resultSession.value?.steps.push({ label: d.blockId || `步骤 ${d.currentStep}`, status: 'running' })
      if (!hasRenderableResult(resultSession.value)) {
        scheduleResultSync(sessionId, 200)
      }
    } catch { /* skip */ }
  })
    es.addEventListener('step_complete', (e: MessageEvent) => {
      try {
        const d = JSON.parse(e.data)
        const s = resultSession.value
        if (!s) return
        const last = s.steps[s.steps.length - 1]
        if (last?.status === 'running') last.status = 'done'
        if (Array.isArray(d.rows)) {
          s.rows = d.rows
          s.dims = d.dims || []
          s.totalRows = d.rowCount ?? d.rows.length
          s.dataState = s.totalRows > 0 ? 'ready' : 'pending'
        }
        if (!hasRenderableResult(s)) {
          scheduleResultSync(sessionId, 200)
        }
      } catch { /* skip */ }
    })
  es.addEventListener('step_fail', () => {
    const last = resultSession.value?.steps[resultSession.value.steps.length - 1]
    if (last?.status === 'running') last.status = 'fail'
  })
    es.addEventListener('session_complete', () => {
      if (resultSession.value) {
        resultSession.value.status = 'completed'
        if (!hasRenderableResult(resultSession.value)) {
          resultSession.value.dataState = 'pending'
        }
      }
      scheduleResultSync(sessionId)
      es.close(); execEventSource = null
    })
  es.addEventListener('session_paused', (e: MessageEvent) => {
    try {
      const d = JSON.parse(e.data)
        const s = resultSession.value
        if (!s) return
        s.status = 'paused'
        if (Array.isArray(d.rows)) {
          s.rows = d.rows
          s.dims = d.dims || []
          s.totalRows = d.rowCount ?? d.rows.length
          s.dataState = s.totalRows > 0 ? 'ready' : 'empty'
        }
        if (!hasRenderableResult(s)) {
          scheduleResultSync(sessionId, 200)
        }
      } catch { /* skip */ }
      es.close(); execEventSource = null
    })
    es.addEventListener('session_fail', (e: MessageEvent) => {
      try {
        const d = JSON.parse(e.data)
        if (resultSession.value) {
          resultSession.value.status = 'failed'
          resultSession.value.errorMsg = d.message || null
          resultSession.value.dataState = hasRenderableResult(resultSession.value) ? 'ready' : 'empty'
        }
      } catch { /* skip */ }
      es.close(); execEventSource = null
    })
  es.onerror = () => {
    if (resultSession.value?.status === 'running') resultSession.value.status = 'completed'
    if (!hasRenderableResult(resultSession.value)) {
      scheduleResultSync(sessionId)
    }
    es.close(); execEventSource = null
  }
}

async function exportExcel() {
  const s = resultSession.value
  if (!s?.id) return
  try {
    await exportSessionExcel(s.id, `选品结果_${new Date().toLocaleDateString('zh-CN').replaceAll('/', '-')}`)
  } catch {
    ElMessage.error('导出失败，请重试')
  }
}

function savePlan() {
  ElMessage.info('保存方案功能开发中')
}

async function handleDeleteHistorySession(session: ApiSession) {
  try {
    await ElMessageBox.confirm(
      `确认从历史会话中移除“${session.title || '未命名任务'}”吗？`,
      '删除历史会话',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
    await removeSession(session.id)
    removeSessionFromHistory(session.id)
    ElMessage.success('已从历史会话中移除')
    void refreshHistorySessions()
  } catch {
    // cancelled
  }
}
void handleDeleteHistorySession

async function handleDeleteSessionSoft(session: ApiSession) {
  try {
    await ElMessageBox.confirm(
      `确认将”${session.title || '未命名任务'}”从历史会话中移除吗？`,
      '移除历史会话',
      {
        type: 'warning',
        confirmButtonText: '移除',
        cancelButtonText: '取消',
      },
    )
    await removeSession(session.id)
    removeSessionFromHistory(session.id)
    ElMessage.success('已从历史会话中移除')
    void refreshHistorySessions()
  } catch {
    // cancelled
  }
}

/**
 * 恢复对话（从历史记录点击未执行的session）
 */
async function handleRestoreConversation(session: ApiSession) {
  try {
    // 1. 清空当前对话
    messages.value = []
    lastBlockChain.value = null
    pendingPlan.value = false
    planningTrace.value = []

    // 分层缓存：内存 → IndexedDB → 服务器，拉取完整 session（含 conversationSnapshot）
    let fullSession: ApiSession = session
    try {
      const cached = await getCachedSessionDetail(session.id, async (id) => {
        const res = await getSession(id)
        return res.data ?? null
      })
      if (cached) fullSession = cached
    } catch {
      // 降级：使用列表轻量数据
    }

    // 2. 从 fullSession 恢复数据
    planningThreadId.value = fullSession.id
    planningAgentThreadId.value = fullSession.id
    planningSessionId.value = fullSession.id

    // 3. 优先从 conversationSnapshot 恢复完整对话
    if (fullSession.conversationSnapshot?.messages?.length) {
      const snapshot = fullSession.conversationSnapshot

      // 恢复所有历史消息
      messages.value = snapshot.messages.map(msg => ({
        role: msg.role,
        text: msg.text || '',
        plan: msg.plan,
        interpretation: msg.interpretation,
        preview: msg.preview as any  // 类型转换，因为历史数据可能不完全匹配 PreviewResponse
      }))

      // 恢复 QA 历史
      if (snapshot.qaHistory) {
        qaHistory.value = snapshot.qaHistory
      }

      // 恢复 lastBlockChain
      const lastMsg = snapshot.messages[snapshot.messages.length - 1]
      if (lastMsg?.plan) {
        lastBlockChain.value = lastMsg.plan as Block[]
        pendingPlan.value = true
      }

      console.log(`恢复会话成功: ${snapshot.messages.length} 条消息`)
      ElMessage.success('会话已恢复')
    }
    // 4. 降级：从 sourceText + blockChain 恢复（旧数据）
    else if (fullSession.sourceText || fullSession.blockChain) {
      if (fullSession.sourceText) {
        addMessage({ role: 'user', text: fullSession.sourceText })
      }

      if (fullSession.blockChain?.length) {
        lastBlockChain.value = fullSession.blockChain as Block[]
        addMessage({
          role: 'ai',
          text: '',
          plan: fullSession.blockChain as Block[],
          summary: fullSession.title || '已恢复的方案',
          interpretation: buildPlanInterpretation(fullSession.blockChain as Block[])
        })
        pendingPlan.value = true
      } else {
        // 如果没有方案，显示欢迎消息
        addMessage({
          role: 'ai',
          text: '已恢复对话。请继续调整您的选品需求。'
        })
      }

      console.log('恢复会话（旧格式）: sourceText + blockChain')
      ElMessage.success('会话已恢复')
    }

    // 5. 滚动到底部
    await nextTick()
    scrollToBottom()

    // 6. 刷新历史记录缓存（确保其他地方的修改能同步显示）
    void refreshHistorySessions()
  } catch (error) {
    console.error('恢复会话失败:', error)
    ElMessage.error('恢复会话失败，请重试')
  }
}

onMounted(() => {
  addMessage({ role: 'ai', text: GREETING, isGreeting: true })
})

onUnmounted(() => {
  finishPlanning()
  execEventSource?.close()
  clearResultSyncLoop()
})
</script>

<style scoped>
.new-session-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 80px);
}

.page-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  flex-shrink: 0;
}
.page-header h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: 0.01em;
}

.main-layout {
  display: flex;
  gap: 20px;
  flex: 1;
  min-height: 0;
}

/* Chat Panel */
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 24px 50px rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  background:
    radial-gradient(circle at top left, rgba(99, 102, 241, 0.1), transparent 30%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.96) 0%, rgba(255, 255, 255, 0.9) 100%);
  flex-shrink: 0;
}
.ai-icon {
  color: #4f46e5;
  font-size: 18px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.86) 0%, rgba(241, 245, 249, 0.74) 100%);
}

.message-row { display: flex; align-items: flex-start; gap: 10px; }
.message-row.ai .avatar { font-size: 22px; }
.bubble-wrap { max-width: 72%; }
.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}
.bubble.ai {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-top-left-radius: 2px;
}
.avatar { font-size: 22px; flex-shrink: 0; margin-top: 2px; }

/* Thinking animation */
.bubble.progress-bubble { padding: 12px 16px; min-width: 120px; }
.thinking-preview {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 6px;
  padding: 4px 8px;
  max-height: 72px;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-all;
  border-left: 2px solid #e5e7eb;
  line-height: 1.5;
}
.thinking-dots { display: flex; gap: 5px; align-items: center; padding: 2px 0; }
.thinking-dots span {
  width: 7px; height: 7px; border-radius: 50%; background: #6b7280;
  animation: bounce 1.2s infinite;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); }
  40% { transform: translateY(-6px); }
}

.step-list { display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
.step-item { display: flex; align-items: center; gap: 8px; line-height: 1.4; }
.step-icon { flex-shrink: 0; width: 16px; text-align: center; font-size: 12px; }
.step-item.done .step-icon { color: #22c55e; }
.step-item.fail .step-icon { color: #ef4444; }
.step-item.running .step-icon { color: #6366f1; }
.step-item.running .step-label { color: #6b7280; }
.step-label { color: #374151; word-break: break-all; }

.spin { display: inline-block; animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

/* Input area */
.chat-input-area {
  display: flex;
  gap: 12px;
  padding: 16px 18px 18px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.94);
  flex-shrink: 0;
  align-items: flex-end;
}
.chat-input-area .el-textarea { flex: 1; }
.send-btn {
  flex-shrink: 0;
  height: 60px;
  padding: 0 22px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  box-shadow: 0 14px 28px rgba(79, 70, 229, 0.24);
}

/* Slide transition */
.slide-right-enter-active,
.slide-right-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.slide-right-enter-from,
.slide-right-leave-to { opacity: 0; transform: translateX(20px); }

@media (max-width: 960px) {
  .new-session-page {
    height: auto;
    min-height: calc(100vh - 80px);
  }

  .main-layout {
    flex-direction: column;
  }
}
</style>
