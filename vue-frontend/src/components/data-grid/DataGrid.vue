<script setup lang="ts">
/**
 * DataGrid 顶层 orchestrator
 *
 * 职责：
 *  - 把 currentView (tableCols/tableData) + user_extra_cols (extraCols/extraValues) 合并成统一的 mergedCols/mergedRows
 *  - 注入 __originalIndex 到每行
 *  - 应用 search/sort（局部状态，不影响后端）
 *  - 应用列显隐/列顺序/列重命名/列宽（localStorage 持久化）
 *  - 行 checkbox 多选（内存态）
 *  - 单元格选中 + 双击进入编辑 + 提交 → 调用 props.onCellEdit
 *  - 增列对话框、导出对话框
 *
 * 组件协作：
 *   DataGridToolbar → 搜索/列显隐/+增列/导出
 *   DataGridFrozenPane (左) + DataGridScrollPane (右) → 双 pane 滚动同步
 *   AddExtraColDialog / ExportDialog
 */
import { computed, provide, ref, watch, type Ref } from 'vue'
import { ElAlert } from 'element-plus'

import DataGridToolbar from './DataGridToolbar.vue'
import DataGridFrozenPane from './DataGridFrozenPane.vue'
import DataGridScrollPane from './DataGridScrollPane.vue'
import AddExtraColDialog from './AddExtraColDialog.vue'
import ExportDialog, { type ExportConfirmPayload } from './ExportDialog.vue'

import { useColumnState } from './helpers/use-column-state'
import { useGridFilterSort } from './helpers/use-grid-filter-sort'
import { useRowSelection } from './helpers/use-row-selection'
import { useVirtualScroll } from './helpers/use-virtual-scroll'
import {
  CHECKBOX_COL_WIDTH,
  COL_SCALES_KEY,
  FROZEN_MIN_SCROLL_WIDTH,
  LS_KEY,
  ROW_HEIGHT,
  ROW_NUMBER_COL_WIDTH,
  type RowHeightMode,
} from './constants'

import type {
  ColumnDim,
  DataGridColumn,
  ExtraColCreateRequest,
  ExtraColUpdateRequest,
  UserExtraCol,
} from '@/types'

import '../data-grid/styles/grid-view.css'

const props = defineProps<{
  /** 来自 currentView.dims */
  tableCols: readonly ColumnDim[]
  /** 来自 currentView.data */
  tableData: readonly Record<string, unknown>[]
  /** 用户增列定义 */
  extraCols: readonly UserExtraCol[]
  /** 用户增列值，key 是 rowIndex 字符串 */
  extraValues: Readonly<Record<string, Record<string, unknown>>>
  /** 是否允许编辑（终态才允许） */
  editable: boolean
  /** 当前会话状态（用于显示 paused 警告 banner） */
  status: string
  sessionId: string
  /** 编辑回调：原数组下标 / 列 id / 新值 */
  onCellEdit: (rowIndex: number, field: string, value: unknown) => Promise<void>
  /** 增列回调 */
  onAddExtraCol: (req: ExtraColCreateRequest) => Promise<void>
  /** 改增列回调 */
  onRenameExtraCol: (colId: string, req: ExtraColUpdateRequest) => Promise<void>
  /** 删增列回调 */
  onRemoveExtraCol: (colId: string) => Promise<void>
  /** 导出回调（由父组件拼装 url 实际调 api） */
  onExport: (payload: ExportConfirmPayload) => Promise<void>
}>()

// ── 1. 合并列：原始列 + 用户增列 ────────────────────────
const mergedCols = computed<DataGridColumn[]>(() => {
  const orig: DataGridColumn[] = props.tableCols.map(c => ({
    id: c.id,
    label: c.label,
    type: c.type,
    isExtra: false,
  }))
  const extra: DataGridColumn[] = props.extraCols.map(c => ({
    id: c.id,
    label: c.label,
    type: c.type,
    options: c.options,
    isExtra: true,
  }))
  return [...orig, ...extra]
})

// ── 1b. Score 列动态 scale ──────────────────────────────
// 扫描 tableData，对每个 type=score 的列算出实际 max 作为 scale，
// ScoreCell 用它把 value 换算为 0-1 ratio 驱动 bar 宽度与颜色。
// 这样 0-10 / 0-100 / 0-1000 等不同数据范围都能自适应，不再硬编码。
const colScales = computed<Record<string, number>>(() => {
  const result: Record<string, number> = {}
  for (const col of props.tableCols) {
    if (col.type !== 'score') continue
    let max = 0
    for (const row of props.tableData) {
      const v = Number(row[col.id])
      if (Number.isFinite(v) && v > max) max = v
    }
    result[col.id] = max > 0 ? max : 10
  }
  return result
})

provide(COL_SCALES_KEY, colScales)

// ── 2. 列状态（宽度/显隐/顺序/原始列重命名）──────────
const sessionIdRef: Ref<string> = computed(() => props.sessionId) as unknown as Ref<string>
const colState = useColumnState({
  sessionId: sessionIdRef,
  allCols: mergedCols,
})

// ── 3. 合并行数据 + 注入 __originalIndex ────────────
interface DisplayRow {
  __originalIndex: number
  data: Record<string, unknown>
}

const mergedRowsWithIndex = computed<Record<string, unknown>[]>(() => {
  return props.tableData.map((row, i) => {
    const merged: Record<string, unknown> = { ...row }
    const ev = props.extraValues[String(i)]
    if (ev) Object.assign(merged, ev)
    merged.__originalIndex = i
    return merged
  })
})

// ── 4. 搜索 + 排序 ────────────────────────────────
const filterSort = useGridFilterSort({ rowsRef: mergedRowsWithIndex })

const displayRows = computed<DisplayRow[]>(() =>
  filterSort.filteredSortedRows.value.map((r) => ({
    __originalIndex: r.__originalIndex as number,
    data: r,
  })),
)

// ── 5. 行多选 ────────────────────────────────────
const rowSelection = useRowSelection()

const visibleOriginalIndices = computed(() => displayRows.value.map(r => r.__originalIndex))
const allCheckboxState = computed(() => rowSelection.checkboxState(visibleOriginalIndices.value))

function toggleAll() {
  const state = allCheckboxState.value
  if (state === 'all') {
    rowSelection.clear()
  } else {
    rowSelection.selectAll(visibleOriginalIndices.value)
  }
}

// ── 6. 行高 ──────────────────────────────────────
const rowHeightMode = ref<RowHeightMode>(
  (localStorage.getItem(LS_KEY.ROW_HEIGHT) as RowHeightMode) || 'default',
)

watch(rowHeightMode, (v) => {
  localStorage.setItem(LS_KEY.ROW_HEIGHT, v)
})

const rowHeight = computed(() => ROW_HEIGHT[rowHeightMode.value])

// ── 7. 冻结列计算 ────────────────────────────────
// 默认冻结第一列（如果有的话）。canFitFrozenColumns: 可滚动区不能 < 300px
const frozenColCount = ref(1)
const containerWidth = ref(800)
const containerRef = ref<HTMLElement>()

function updateContainerWidth() {
  if (containerRef.value) {
    containerWidth.value = containerRef.value.clientWidth
  }
}

watch([containerRef, mergedCols], () => {
  setTimeout(updateContainerWidth, 0)
})

if (typeof window !== 'undefined') {
  window.addEventListener('resize', updateContainerWidth)
}

const visibleCols = computed<DataGridColumn[]>(() => colState.visibleCols.value)

const frozenCols = computed<DataGridColumn[]>(() => {
  const all = visibleCols.value
  let count = Math.min(frozenColCount.value, all.length)
  // 检查可滚动区宽度是否够 300px，否则强制取消冻结
  while (count > 0) {
    const frozenWidth =
      CHECKBOX_COL_WIDTH +
      ROW_NUMBER_COL_WIDTH +
      all
        .slice(0, count)
        .reduce((sum, c) => sum + colState.getColWidth(c.id, c.type), 0)
    if (containerWidth.value - frozenWidth >= FROZEN_MIN_SCROLL_WIDTH) break
    count--
  }
  return all.slice(0, count)
})

const scrollCols = computed<DataGridColumn[]>(() => {
  const all = visibleCols.value
  return all.slice(frozenCols.value.length)
})

const frozenPaneWidth = computed(() => {
  return (
    CHECKBOX_COL_WIDTH +
    ROW_NUMBER_COL_WIDTH +
    frozenCols.value.reduce((sum, c) => sum + colState.getColWidth(c.id, c.type), 0)
  )
})

// ── 8. 选中态 + 编辑态 ──────────────────────────
const selectedCell = ref<{ rowIndex: number; colId: string } | null>(null)
const editingCell = ref<{ rowIndex: number; colId: string } | null>(null)

function selectCell(rowIndex: number, colId: string) {
  selectedCell.value = { rowIndex, colId }
  if (editingCell.value && (editingCell.value.rowIndex !== rowIndex || editingCell.value.colId !== colId)) {
    editingCell.value = null
  }
  // 让根容器获得焦点，以便后续 keydown 命中 onGridKeydown
  containerRef.value?.focus({ preventScroll: true })
}

function startEdit(rowIndex: number, colId: string) {
  if (!props.editable) return
  selectedCell.value = { rowIndex, colId }
  editingCell.value = { rowIndex, colId }
}

function cancelEdit() {
  editingCell.value = null
}

async function commitCell(originalIndex: number, colId: string, value: unknown) {
  editingCell.value = null
  try {
    await props.onCellEdit(originalIndex, colId, value)
  } catch (e) {
    console.warn('[DataGrid] cell edit failed:', e)
  }
}

// ── 8b. 键盘导航 ──────────────────────────────────
/** 把选中行滚入可视区（纵向） */
function ensureCellVisible(rowIndex: number) {
  const el = scrollContainer.value
  if (!el) return
  const headerH = 33
  const rowTop = headerH + rowIndex * rowHeight.value
  const rowBottom = rowTop + rowHeight.value
  // 如果超出顶部
  if (rowTop < el.scrollTop + headerH) {
    el.scrollTop = Math.max(0, rowTop - headerH)
    return
  }
  // 如果超出底部
  if (rowBottom > el.scrollTop + el.clientHeight) {
    el.scrollTop = rowBottom - el.clientHeight
  }
}

function onGridKeydown(e: KeyboardEvent) {
  // 编辑态交给 cell input 处理（它们有 @keydown.enter/esc）
  if (editingCell.value) return
  if (!selectedCell.value) return

  const cols = visibleCols.value
  const rows = displayRows.value
  if (cols.length === 0 || rows.length === 0) return

  const { rowIndex, colId } = selectedCell.value
  const colIdx = cols.findIndex(c => c.id === colId)
  if (colIdx === -1) return

  let nextRow = rowIndex
  let nextColIdx = colIdx
  let handled = true

  switch (e.key) {
    case 'ArrowUp':
      nextRow = Math.max(0, rowIndex - 1)
      break
    case 'ArrowDown':
      nextRow = Math.min(rows.length - 1, rowIndex + 1)
      break
    case 'ArrowLeft':
      nextColIdx = Math.max(0, colIdx - 1)
      break
    case 'ArrowRight':
      nextColIdx = Math.min(cols.length - 1, colIdx + 1)
      break
    case 'Home':
      nextColIdx = 0
      break
    case 'End':
      nextColIdx = cols.length - 1
      break
    case 'Tab':
      nextColIdx = e.shiftKey
        ? Math.max(0, colIdx - 1)
        : Math.min(cols.length - 1, colIdx + 1)
      break
    case 'Enter':
      if (props.editable) startEdit(rowIndex, colId)
      break
    case 'Escape':
      selectedCell.value = null
      break
    default:
      handled = false
  }

  if (handled) {
    e.preventDefault()
    if (nextRow !== rowIndex || nextColIdx !== colIdx) {
      selectCell(nextRow, cols[nextColIdx].id)
      ensureCellVisible(nextRow)
    }
  }
}

// ── 9. 列宽 resize ──────────────────────────────
let resizing: { colId: string; startX: number; startWidth: number } | null = null

function onStartResize(colId: string, startX: number, startWidth: number) {
  resizing = { colId, startX, startWidth }
  window.addEventListener('mousemove', onResizing)
  window.addEventListener('mouseup', onEndResize)
}

function onResizing(e: MouseEvent) {
  if (!resizing) return
  const delta = e.clientX - resizing.startX
  colState.setWidth(resizing.colId, resizing.startWidth + delta)
}

function onEndResize() {
  resizing = null
  window.removeEventListener('mousemove', onResizing)
  window.removeEventListener('mouseup', onEndResize)
}

// ── 10. 列重命名 / 显隐 / 删除 ────────────────
async function handleRename(colId: string, newLabel: string) {
  const col = mergedCols.value.find(c => c.id === colId)
  if (!col) return
  if (col.isExtra) {
    // 增列改名走后端
    try {
      await props.onRenameExtraCol(colId, { label: newLabel })
    } catch (e) {
      console.warn('[DataGrid] rename extra col failed:', e)
    }
  } else {
    // 原始列只在前端 localStorage 改"显示名"
    colState.setRename(colId, newLabel)
  }
}

function handleHide(colId: string) {
  colState.setVisible(colId, false)
}

async function handleRemove(colId: string) {
  try {
    await props.onRemoveExtraCol(colId)
  } catch (e) {
    console.warn('[DataGrid] remove extra col failed:', e)
  }
}

function handleToggleCol(colId: string, visible: boolean) {
  colState.setVisible(colId, visible)
}

// ── 11. 增列对话框 ────────────────────────────────
const addColDialogVisible = ref(false)
const addColLoading = ref(false)

function openAddColDialog() {
  addColDialogVisible.value = true
}

async function handleAddCol(req: ExtraColCreateRequest) {
  addColLoading.value = true
  try {
    await props.onAddExtraCol(req)
    addColDialogVisible.value = false
  } catch (e) {
    console.warn('[DataGrid] add extra col failed:', e)
  } finally {
    addColLoading.value = false
  }
}

// ── 11b. 编辑增列对话框（复用同一个 AddExtraColDialog，mode='edit'）────
const editColDialogVisible = ref(false)
const editColLoading = ref(false)
const editColInitial = ref<UserExtraCol | null>(null)

function openEditColDialog(colId: string) {
  const col = props.extraCols.find(c => c.id === colId)
  if (!col) return
  // 深拷贝 options，避免 readonly 冲突
  editColInitial.value = {
    id: col.id,
    label: col.label,
    type: col.type,
    options: col.options ? [...col.options] : undefined,
  }
  editColDialogVisible.value = true
}

async function handleEditColConfirm(colId: string, req: ExtraColUpdateRequest) {
  editColLoading.value = true
  try {
    await props.onRenameExtraCol(colId, req)
    editColDialogVisible.value = false
  } catch (e) {
    console.warn('[DataGrid] edit extra col failed:', e)
  } finally {
    editColLoading.value = false
  }
}

// ── 11c. 列拖拽排序 ──────────────────────────────
function handleMoveColumn(fromColId: string, toColId: string) {
  colState.moveColumn(fromColId, toColId)
}

// ── 12. 导出对话框 ────────────────────────────────
const exportDialogVisible = ref(false)

function openExportDialog() {
  exportDialogVisible.value = true
}

async function handleExport(payload: ExportConfirmPayload) {
  exportDialogVisible.value = false
  await props.onExport(payload)
}

const colRenamesForExport = computed<Record<string, string>>(() => {
  const result: Record<string, string> = {}
  for (const col of mergedCols.value) {
    if (!col.isExtra) {
      const rn = colState.getRename(col.id)
      if (rn) result[col.id] = rn
    }
  }
  return result
})

// ── 13. 虚拟滚动 ────────────────────────────────
// 单滚动容器架构：外层 .data-grid__scroll 是唯一的滚动元素，
// 左右两个 pane 都在这个容器内。虚拟滚动直接监听这个容器的 scrollTop。
// 左右两个 pane 用同一份 visibleStart/End + topPadding/bottomPadding 渲染，
// 因为它们在同一个 scroll container 里，所以天然纵向同步。
const scrollContainer = ref<HTMLElement | null>(null)

const virtual = useVirtualScroll({
  containerRef: scrollContainer,
  rowCount: computed(() => displayRows.value.length),
  rowHeight,
})

const totalRowsCount = computed(() => props.tableData.length)
const visibleRowsCount = computed(() => displayRows.value.length)

// 暴露给父组件
defineExpose({
  clearSelection: () => rowSelection.clear(),
})
</script>

<template>
  <div
    ref="containerRef"
    class="grid-view"
    :class="`grid-view--row-height-${rowHeightMode}`"
    role="grid"
    tabindex="0"
    @keydown="onGridKeydown"
  >
    <ElAlert
      v-if="status === 'paused'"
      type="warning"
      :closable="false"
      show-icon
      style="border-radius: 0; flex-shrink: 0"
    >
      <template #title>
        当前为暂停状态，编辑后的数据会在「继续执行」时被传入下游 block，可能影响最终结果
      </template>
    </ElAlert>

    <DataGridToolbar
      :total-rows="totalRowsCount"
      :visible-rows="visibleRowsCount"
      :selected-count="rowSelection.selectedCount.value"
      :search-value="filterSort.search.value"
      :row-height-mode="rowHeightMode"
      :all-cols="colState.orderedCols.value"
      :is-visible="colState.isVisible"
      :editable="editable"
      @update:search-value="(v: string) => filterSort.search.value = v"
      @update:row-height-mode="(v: RowHeightMode) => rowHeightMode = v"
      @toggle-col="handleToggleCol"
      @add-extra-col="openAddColDialog"
      @export="openExportDialog"
    />

    <div ref="scrollContainer" class="data-grid__scroll">
      <div class="data-grid__grid-inner">
        <DataGridFrozenPane
          :frozen-cols="frozenCols"
          :rows="displayRows"
          :get-col-width="colState.getColWidth"
          :get-sort-dir="filterSort.getSortDir"
          :editing-cell="editingCell"
          :selected-cell="selectedCell"
          :editable="editable"
          :row-height="rowHeight"
          :top-padding="virtual.topPadding.value"
          :bottom-padding="virtual.bottomPadding.value"
          :visible-start="virtual.visibleStart.value"
          :visible-end="virtual.visibleEnd.value"
          :is-row-selected="rowSelection.isSelected"
          :all-checkbox-state="allCheckboxState"
          :width="frozenPaneWidth"
          @start-edit="startEdit"
          @commit="commitCell"
          @cancel="cancelEdit"
          @select="selectCell"
          @cycle-sort="filterSort.cycleSort"
          @start-resize="onStartResize"
          @rename="handleRename"
          @hide="handleHide"
          @remove="handleRemove"
          @edit-options="openEditColDialog"
          @move-column="handleMoveColumn"
          @toggle-row="rowSelection.toggle"
          @toggle-all="toggleAll"
        />

        <DataGridScrollPane
          :scroll-cols="scrollCols"
          :rows="displayRows"
          :get-col-width="colState.getColWidth"
          :get-sort-dir="filterSort.getSortDir"
          :editing-cell="editingCell"
          :selected-cell="selectedCell"
          :editable="editable"
          :row-height="rowHeight"
          :top-padding="virtual.topPadding.value"
          :bottom-padding="virtual.bottomPadding.value"
          :visible-start="virtual.visibleStart.value"
          :visible-end="virtual.visibleEnd.value"
          :is-row-selected="rowSelection.isSelected"
          @start-edit="startEdit"
          @commit="commitCell"
          @cancel="cancelEdit"
          @select="selectCell"
          @cycle-sort="filterSort.cycleSort"
          @start-resize="onStartResize"
          @rename="handleRename"
          @hide="handleHide"
          @remove="handleRemove"
          @edit-options="openEditColDialog"
          @move-column="handleMoveColumn"
        />
      </div>
    </div>

    <div v-if="visibleRowsCount === 0" class="data-grid__empty">
      <div class="data-grid__empty-icon">📭</div>
      <div>{{ filterSort.search.value ? '没有匹配的行' : '暂无数据' }}</div>
    </div>

    <AddExtraColDialog
      v-model:visible="addColDialogVisible"
      :loading="addColLoading"
      @confirm="handleAddCol"
    />

    <AddExtraColDialog
      v-model:visible="editColDialogVisible"
      mode="edit"
      :initial="editColInitial"
      :loading="editColLoading"
      @confirm-edit="handleEditColConfirm"
    />

    <ExportDialog
      v-model:visible="exportDialogVisible"
      :total-rows="totalRowsCount"
      :visible-rows="visibleRowsCount"
      :selected-row-indices="Array.from(rowSelection.selected.value)"
      :visible-cols="visibleCols"
      :all-cols="colState.orderedCols.value"
      :search-value="filterSort.search.value"
      :sort-state="filterSort.sortState.value"
      :col-renames="colRenamesForExport"
      @confirm="handleExport"
    />
  </div>
</template>
