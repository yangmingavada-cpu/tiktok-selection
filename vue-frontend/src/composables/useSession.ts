import { computed, ref, shallowRef, reactive, readonly, watch, onUnmounted, toValue } from 'vue'
import type { MaybeRefOrGetter } from 'vue'
import { ElMessage } from 'element-plus'
import {
  addExtraCol as apiAddExtraCol,
  cancelSession as apiCancel,
  deleteSessionCol as apiDeleteCol,
  deleteSessionRows as apiDeleteRows,
  exportSessionExcel,
  getSession,
  getSessionSteps,
  removeExtraCol as apiRemoveExtraCol,
  renameExtraCol as apiRenameExtraCol,
  renameSessionCol as apiRenameCol,
  resumeSession as apiResume,
  updateSessionCell,
  type ExportSessionParams,
} from '@/api/session'
import { createPlan } from '@/api/plan'
import type {
  ColumnDim,
  CurrentView,
  ExtraColCreateRequest,
  ExtraColUpdateRequest,
  Session,
  SessionStep,
  UserExtraCol,
  UserExtraColsPayload,
} from '@/types'
import { STORAGE_KEY } from '@/constants'

export function useSession(sessionIdGetter: MaybeRefOrGetter<string>) {
  const session = reactive<Partial<Session>>({})
  const steps = ref<SessionStep[]>([])
  const tableData = ref<Record<string, unknown>[]>([])
  const tableCols = ref<ColumnDim[]>([])
  const currentView = shallowRef<CurrentView | null>(null)
  const extraCols = ref<UserExtraCol[]>([])
  const extraValues = ref<Record<string, Record<string, unknown>>>({})

  /** 终态：SSE 不再覆盖 tableData，allow 编辑 */
  const TERMINAL = ['completed', 'failed', 'cancelled']
  /** 可编辑状态白名单（与后端 EDITABLE_STATUS 保持一致） */
  const EDITABLE = ['completed', 'paused', 'failed', 'cancelled']

  const isEditable = computed(() => EDITABLE.includes(session.status ?? ''))

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
    }))
  }

  function applyUserExtraCols(payload: UserExtraColsPayload | null | undefined) {
    if (!payload) {
      extraCols.value = []
      extraValues.value = {}
      return
    }
    extraCols.value = Array.isArray(payload.cols) ? payload.cols : []
    extraValues.value = payload.values && typeof payload.values === 'object'
      ? payload.values
      : {}
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
      applyUserExtraCols(res.data?.userExtraCols ?? null)
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
      // 终态守卫：会话已结束时，忽略迟到的 SSE 事件，避免覆盖用户编辑
      if (TERMINAL.includes(session.status ?? '')) {
        fetchSteps()
        return
      }
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
  function handleExport(params?: ExportSessionParams) {
    return exportSessionExcel(toValue(sessionIdGetter), session.title, params)
  }

  /**
   * 单元格行内编辑
   * 区分原始列 vs 用户增列：
   *  - 原始列：写到 currentView.data，本地乐观更新 tableData
   *  - 用户增列：写到 user_extra_cols.values，本地乐观更新 extraValues
   * 失败时回滚到旧值并抛异常给调用方处理
   */
  async function updateCell(rowIndex: number, field: string, value: unknown) {
    const id = toValue(sessionIdGetter)
    if (!id) return
    const isExtra = extraCols.value.some(c => c.id === field)

    if (isExtra) {
      const key = String(rowIndex)
      const oldRow = { ...(extraValues.value[key] || {}) }
      const newRow = { ...oldRow }
      if (value === null || value === undefined || value === '') {
        delete newRow[field]
      } else {
        newRow[field] = value
      }
      // 乐观更新：替换整个 map 触发响应式
      extraValues.value = { ...extraValues.value, [key]: newRow }
      try {
        await updateSessionCell(id, { rowIndex, field, value })
      } catch (e) {
        // 回滚
        extraValues.value = { ...extraValues.value, [key]: oldRow }
        ElMessage.error('单元格保存失败')
        throw e
      }
    } else {
      const oldRow = { ...tableData.value[rowIndex] }
      tableData.value[rowIndex] = { ...oldRow, [field]: value }
      try {
        await updateSessionCell(id, { rowIndex, field, value })
      } catch (e) {
        tableData.value[rowIndex] = oldRow
        ElMessage.error('单元格保存失败')
        throw e
      }
    }
  }

  async function addExtraCol(req: ExtraColCreateRequest) {
    const id = toValue(sessionIdGetter)
    if (!id) return
    try {
      const res = await apiAddExtraCol(id, req)
      if (res.data) {
        extraCols.value = [...extraCols.value, res.data]
        ElMessage.success('已新增列')
      }
    } catch (e) {
      ElMessage.error('新增列失败')
      throw e
    }
  }

  async function renameExtraCol(colId: string, req: ExtraColUpdateRequest) {
    const id = toValue(sessionIdGetter)
    if (!id) return
    try {
      await apiRenameExtraCol(id, colId, req)
      extraCols.value = extraCols.value.map(c =>
        c.id === colId ? { ...c, ...req } : c,
      )
      ElMessage.success('已更新列')
    } catch (e) {
      ElMessage.error('更新列失败')
      throw e
    }
  }

  async function removeExtraCol(colId: string) {
    const id = toValue(sessionIdGetter)
    if (!id) return
    try {
      await apiRemoveExtraCol(id, colId)
      extraCols.value = extraCols.value.filter(c => c.id !== colId)
      // 同步清掉本地 values 里的对应键
      const next: Record<string, Record<string, unknown>> = {}
      for (const [rowKey, rowMap] of Object.entries(extraValues.value)) {
        if (rowMap[colId] !== undefined) {
          const cleaned = { ...rowMap }
          delete cleaned[colId]
          next[rowKey] = cleaned
        } else {
          next[rowKey] = rowMap
        }
      }
      extraValues.value = next
      ElMessage.success('已删除列')
    } catch (e) {
      ElMessage.error('删除列失败')
      throw e
    }
  }

  /**
   * 批量删除行
   * 后端会重映射 userExtraCols.values 的 key，前端直接用返回值覆盖本地状态
   */
  async function deleteRows(rowIndices: number[]) {
    const id = toValue(sessionIdGetter)
    if (!id || rowIndices.length === 0) return
    try {
      const res = await apiDeleteRows(id, rowIndices)
      // 本地 tableData 按被删下标过滤（index 从小到大过滤是安全的，因为 filter 遍历原数组）
      const toDelete = new Set(rowIndices)
      tableData.value = tableData.value.filter((_, i) => !toDelete.has(i))
      // userExtraCols 整体用后端返回覆盖（避免前端再算 shift 出错）
      if (res.data?.userExtraCols) {
        applyUserExtraCols(res.data.userExtraCols)
      }
      // currentView 缓存同步
      if (currentView.value) {
        currentView.value = {
          ...currentView.value,
          data: tableData.value,
          totalCount: res.data?.totalCount ?? tableData.value.length,
        }
      }
      ElMessage.success(`已删除 ${res.data?.deletedCount ?? rowIndices.length} 行`)
    } catch (e) {
      ElMessage.error('删除行失败')
      throw e
    }
  }

  /**
   * 删除列（统一入口：自动判断原始列 / 用户增列，后端统一处理）
   */
  async function deleteCol(field: string) {
    const id = toValue(sessionIdGetter)
    if (!id) return
    try {
      const res = await apiDeleteCol(id, field)
      const isExtra = res.data?.isExtra ?? false
      if (isExtra) {
        extraCols.value = extraCols.value.filter(c => c.id !== field)
        const next: Record<string, Record<string, unknown>> = {}
        for (const [rowKey, rowMap] of Object.entries(extraValues.value)) {
          if (rowMap[field] !== undefined) {
            const cleaned = { ...rowMap }
            delete cleaned[field]
            next[rowKey] = cleaned
          } else {
            next[rowKey] = rowMap
          }
        }
        extraValues.value = next
      } else {
        tableCols.value = tableCols.value.filter(c => c.id !== field)
        tableData.value = tableData.value.map((row) => {
          const next = { ...row }
          delete next[field]
          return next
        })
        if (currentView.value) {
          const dims = (currentView.value.dims || []).filter(d => d.id !== field)
          currentView.value = {
            ...currentView.value,
            dims,
            data: tableData.value,
          }
        }
      }
      ElMessage.success('已删除列')
    } catch (e) {
      ElMessage.error('删除列失败')
      throw e
    }
  }

  /**
   * 重命名列（统一入口：自动判断原始列 / 用户增列）
   */
  async function renameCol(field: string, label: string) {
    const id = toValue(sessionIdGetter)
    if (!id) return
    try {
      const res = await apiRenameCol(id, field, label)
      const isExtra = res.data?.isExtra ?? false
      if (isExtra) {
        extraCols.value = extraCols.value.map(c => (c.id === field ? { ...c, label } : c))
      } else {
        tableCols.value = tableCols.value.map(c => (c.id === field ? { ...c, label } : c))
        if (currentView.value) {
          const dims = (currentView.value.dims || []).map(d =>
            d.id === field ? { ...d, label } : d,
          )
          currentView.value = {
            ...currentView.value,
            dims,
          }
        }
      }
      ElMessage.success('已更新列名')
    } catch (e) {
      ElMessage.error('列名保存失败')
      throw e
    }
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
    extraCols.value = []
    extraValues.value = {}
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
    extraCols: readonly(extraCols),
    extraValues: readonly(extraValues),
    messages: readonly(messages),
    isEditable,
    fetchSession,
    fetchSteps,
    pushMsg,
    handleExport,
    handleSavePlan,
    resumeSession,
    cancelSession,
    updateCell,
    addExtraCol,
    renameExtraCol,
    removeExtraCol,
    deleteRows,
    deleteCol,
    renameCol,
  }
}
