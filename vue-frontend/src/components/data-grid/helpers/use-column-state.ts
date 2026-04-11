/**
 * 列状态持久化 composable
 *
 * 管理：列宽 / 列显隐 / 列顺序 / 列重命名
 * 持久化到 localStorage，按 sessionId 隔离。
 *
 * 注意：用户增列的 label 改名走后端 PATCH，不在这里持久化；
 * 这里只管原始列的"显示名映射"（仅前端 + 导出生效）。
 */
import { computed, ref, watch, type Ref } from 'vue'

import { LS_KEY, DEFAULT_COL_WIDTH } from '../constants'
import type { DataGridColumn } from '@/types'

export interface ColumnConfig {
  width?: number
  visible?: boolean   // undefined 视为可见
  rename?: string     // 用户在前端重命名后的显示名
}

interface UseColumnStateOptions {
  sessionId: Ref<string>
  /** 所有列（原始 + 增列）。响应式。*/
  allCols: Ref<DataGridColumn[]>
}

function safeParseJson<T>(s: string | null, fallback: T): T {
  if (!s) return fallback
  try {
    const v = JSON.parse(s)
    return v ?? fallback
  } catch {
    return fallback
  }
}

export function useColumnState(opts: UseColumnStateOptions) {
  const configs = ref<Record<string, ColumnConfig>>({})
  const order = ref<string[]>([])

  const stateKey = computed(() => `${LS_KEY.COL_STATE}:${opts.sessionId.value}`)
  const orderKey = computed(() => `${LS_KEY.COL_ORDER}:${opts.sessionId.value}`)

  function load() {
    configs.value = safeParseJson<Record<string, ColumnConfig>>(localStorage.getItem(stateKey.value), {})
    order.value = safeParseJson<string[]>(localStorage.getItem(orderKey.value), [])
  }

  function persistConfigs() {
    try {
      localStorage.setItem(stateKey.value, JSON.stringify(configs.value))
    } catch {
      /* ignore quota errors */
    }
  }

  function persistOrder() {
    try {
      localStorage.setItem(orderKey.value, JSON.stringify(order.value))
    } catch {
      /* ignore */
    }
  }

  // sessionId 变化时重新加载
  watch(opts.sessionId, load, { immediate: true })

  // 列表变化时：补齐 order（新列追加到末尾，已删的列从 order 移除）
  watch(opts.allCols, (cols) => {
    const existingIds = new Set(cols.map(c => c.id))
    let changed = false
    // 移除不存在的
    const filtered = order.value.filter(id => existingIds.has(id))
    if (filtered.length !== order.value.length) {
      order.value = filtered
      changed = true
    }
    // 追加新列
    for (const col of cols) {
      if (!order.value.includes(col.id)) {
        order.value.push(col.id)
        changed = true
      }
    }
    if (changed) persistOrder()
  }, { immediate: true, deep: false })

  // ── Setters ──────────────────────────────────────────
  function setWidth(colId: string, width: number) {
    if (!configs.value[colId]) configs.value[colId] = {}
    configs.value[colId].width = Math.max(60, Math.round(width))
    persistConfigs()
  }

  function setVisible(colId: string, visible: boolean) {
    if (!configs.value[colId]) configs.value[colId] = {}
    configs.value[colId].visible = visible
    persistConfigs()
  }

  function setRename(colId: string, rename: string) {
    if (!configs.value[colId]) configs.value[colId] = {}
    if (rename) {
      configs.value[colId].rename = rename
    } else {
      delete configs.value[colId].rename
    }
    persistConfigs()
  }

  function moveColumn(fromColId: string, toColId: string) {
    const from = order.value.indexOf(fromColId)
    const to = order.value.indexOf(toColId)
    if (from < 0 || to < 0 || from === to) return
    const [moved] = order.value.splice(from, 1)
    order.value.splice(to, 0, moved)
    persistOrder()
  }

  // ── Computed views ───────────────────────────────────

  /** 应用列宽/重命名/顺序后的列列表（仍包含隐藏列） */
  const orderedCols = computed<DataGridColumn[]>(() => {
    const colMap = new Map(opts.allCols.value.map(c => [c.id, c]))
    const result: DataGridColumn[] = []
    for (const id of order.value) {
      const col = colMap.get(id)
      if (col) {
        const cfg = configs.value[id]
        result.push({
          ...col,
          label: cfg?.rename || col.label,
        })
      }
    }
    // 兜底：order 里没有的列追加在末尾
    for (const col of opts.allCols.value) {
      if (!order.value.includes(col.id)) {
        const cfg = configs.value[col.id]
        result.push({
          ...col,
          label: cfg?.rename || col.label,
        })
      }
    }
    return result
  })

  /** 仅可见的列（用于实际渲染） */
  const visibleCols = computed<DataGridColumn[]>(() =>
    orderedCols.value.filter(c => configs.value[c.id]?.visible !== false),
  )

  function getColWidth(colId: string, type: DataGridColumn['type']): number {
    return configs.value[colId]?.width ?? DEFAULT_COL_WIDTH[type] ?? 160
  }

  function isVisible(colId: string): boolean {
    return configs.value[colId]?.visible !== false
  }

  function getRename(colId: string): string | undefined {
    return configs.value[colId]?.rename
  }

  return {
    configs,
    order,
    orderedCols,
    visibleCols,
    setWidth,
    setVisible,
    setRename,
    moveColumn,
    getColWidth,
    isVisible,
    getRename,
  }
}
