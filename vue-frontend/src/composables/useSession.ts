import { ref, shallowRef, reactive, readonly, watch, onUnmounted, toValue } from 'vue'
import type { MaybeRefOrGetter } from 'vue'
import { ElMessage } from 'element-plus'
import { getSession, getSessionSteps, exportSessionExcel, resumeSession as apiResume, cancelSession as apiCancel } from '@/api/session'
import { createPlan } from '@/api/plan'
import type { Session, SessionStep, ColumnDim, CurrentView } from '@/types'
import { STORAGE_KEY } from '@/constants'

export function useSession(sessionIdGetter: MaybeRefOrGetter<string>) {
  const session = reactive<Partial<Session>>({})
  const steps = ref<SessionStep[]>([])
  const tableData = ref<Record<string, unknown>[]>([])
  const tableCols = ref<ColumnDim[]>([])
  const currentView = shallowRef<CurrentView | null>(null)

  interface SessionMessage {
    role: 'user' | 'system'
    content: string
    time: string
  }
  const messages = ref<SessionMessage[]>([])

  let eventSource: EventSource | null = null

  // ── Helpers ──────────────────────────────────────────
  function buildCols(dims: ColumnDim[]) {
    tableCols.value = dims.map(d => ({
      id: String(d.id || ''),
      label: String(d.label || d.id || ''),
      type: d.type || 'string',
      minWidth: d.type === 'number' || d.type === 'score' ? 100 : 140,
    } as ColumnDim & { minWidth: number }))
  }

  function applyCurrentView(cv: CurrentView | null) {
    if (!cv) return
    currentView.value = cv
    const dims = cv.dims || []
    const data = cv.data || []
    if (dims.length > 0) {
      buildCols(dims)
    } else if (data.length > 0) {
      tableCols.value = Object.keys(data[0]).map(k => ({
        id: k, label: k, type: 'string' as const,
      }))
    }
    tableData.value = data
  }

  function pushMsg(role: 'user' | 'system', content: string) {
    const d = new Date()
    const time = d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
    messages.value.push({ role, content, time })
  }

  // ── Fetch ─────────────────────────────────────────────
  async function fetchSession() {
    const id = toValue(sessionIdGetter)
    if (!id) return
    try {
      const res = await getSession(id)
      Object.assign(session, res.data || {})
      applyCurrentView(res.data?.currentView ?? null)
    } catch (e) {
      console.warn('[useSession] fetchSession failed:', e)
    }
  }

  async function fetchSteps() {
    const id = toValue(sessionIdGetter)
    if (!id) return
    try {
      const res = await getSessionSteps(id)
      steps.value = res.data || []
    } catch (e) {
      console.warn('[useSession] fetchSteps failed:', e)
    }
  }

  // ── SSE ───────────────────────────────────────────────
  function subscribeSSE() {
    const id = toValue(sessionIdGetter)
    if (!id) return
    const token = localStorage.getItem(STORAGE_KEY.TOKEN)
    const query = token ? '?token=' + token : ''
    eventSource = new EventSource(`/api/sessions/${id}/subscribe` + query)

    eventSource.addEventListener('step_start', (e: MessageEvent) => {
      const d = JSON.parse(e.data)
      pushMsg('system', `⏳ 开始执行: ${d.blockId}`)
      fetchSteps()
    })

    eventSource.addEventListener('step_complete', (e: MessageEvent) => {
      const d = JSON.parse(e.data)
      const rowCount = d.rowCount ?? d.rows?.length ?? '?'
      pushMsg('system', `✅ ${d.blockId} 完成，${rowCount} 条数据`)
      if (d.rows) {
        tableData.value = d.rows
        if (d.dims) buildCols(d.dims)
        else if (d.rows.length > 0 && tableCols.value.length === 0) {
          tableCols.value = Object.keys(d.rows[0]).map(k => ({
            id: k, label: k, type: 'string' as const,
          }))
        }
        currentView.value = {
          ...(currentView.value || { data: [], dims: [], totalCount: 0 }),
          data: d.rows,
          dims: d.dims || currentView.value?.dims || [],
          totalCount: d.rowCount ?? d.rows.length,
        }
      }
      fetchSteps()
    })

    eventSource.addEventListener('step_fail', (e: MessageEvent) => {
      const d = JSON.parse(e.data)
      pushMsg('system', `❌ 错误: ${d.blockId} — ${d.message || d.error || '未知错误'}`)
      fetchSteps()
    })

    eventSource.addEventListener('session_complete', () => {
      pushMsg('system', '🎉 选品任务已完成')
      fetchSession()
      fetchSteps()
    })

    eventSource.addEventListener('session_paused', (e: MessageEvent) => {
      const d = JSON.parse(e.data)
      pushMsg('system', `⏸ ${d.message || '执行已暂停，请查看数据后选择继续或修改方案'}`)
      if (d.rows) {
        tableData.value = d.rows
        if (d.dims) buildCols(d.dims)
        currentView.value = {
          ...(currentView.value || { data: [], dims: [], totalCount: 0 }),
          data: d.rows,
          dims: d.dims || currentView.value?.dims || [],
          totalCount: d.rowCount ?? d.rows.length,
        }
      }
      fetchSession()
      fetchSteps()
    })

    eventSource.addEventListener('session_fail', (e: MessageEvent) => {
      const d = JSON.parse(e.data)
      pushMsg('system', `❌ 任务失败: ${d.message || d.error || '未知错误'}`)
      fetchSession()
    })
  }

  function closeSSE() {
    eventSource?.close()
    eventSource = null
  }

  // ── Actions ───────────────────────────────────────────
  function handleExport() {
    exportSessionExcel(toValue(sessionIdGetter), session.title)
  }

  async function handleSavePlan(name: string, description: string): Promise<boolean> {
    try {
      await createPlan({
        name,
        description,
        sourceText: session.sourceText,
        blockChain: session.blockChain,
      })
      ElMessage.success('方案已保存')
      return true
    } catch {
      ElMessage.error('保存失败')
      return false
    }
  }

  async function resumeSession() {
    const id = toValue(sessionIdGetter)
    if (!id) return
    await apiResume(id)
    session.status = 'in_progress'
    pushMsg('system', '▶️ 已继续执行...')
  }

  async function cancelSession() {
    const id = toValue(sessionIdGetter)
    if (!id) return
    await apiCancel(id)
    await fetchSession()
    pushMsg('system', '🚫 执行已取消')
  }

  // ── Reactivity ────────────────────────────────────────
  function resetState() {
    messages.value = []
    steps.value = []
    tableData.value = []
    tableCols.value = []
    currentView.value = null
    // Reset session reactive without Object.keys delete hack
    const keys = Object.keys(session) as (keyof Session)[]
    for (const k of keys) {
      (session as Record<string, unknown>)[k] = undefined
    }
  }

  watch(
    () => toValue(sessionIdGetter),
    (newId, oldId) => {
      if (!newId) return
      if (oldId && oldId !== newId) {
        closeSSE()
        resetState()
      }
      fetchSession()
      fetchSteps()
      subscribeSSE()
    },
    { immediate: true },
  )

  onUnmounted(closeSSE)

  return {
    session,
    steps: readonly(steps),
    tableData: readonly(tableData),
    tableCols: readonly(tableCols),
    currentView: readonly(currentView),
    messages: readonly(messages),
    fetchSession,
    fetchSteps,
    pushMsg,
    handleExport,
    handleSavePlan,
    resumeSession,
    cancelSession,
  }
}
