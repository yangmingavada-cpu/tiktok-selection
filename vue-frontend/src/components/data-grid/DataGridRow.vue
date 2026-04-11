<script setup lang="ts">
/**
 * 单行容器
 * 渲染一组 DataGridCell；同步行号 / checkbox（仅左 pane）。
 */
import DataGridCell from './DataGridCell.vue'
import type { DataGridColumn } from '@/types'

const props = defineProps<{
  /** 已经过 search/sort 的可视下标 */
  visibleIndex: number
  /** 原数组下标（提交编辑时回传） */
  originalIndex: number
  /** 行数据（合并 currentView.data + extraValues 后） */
  rowData: Record<string, unknown>
  /** 当前 pane 要渲染的列 */
  cols: readonly DataGridColumn[]
  /** 列宽查询函数 */
  getColWidth: (colId: string, type: DataGridColumn['type']) => number
  /** 当前正在编辑的 (rowIndex, colId) */
  editingCell: { rowIndex: number; colId: string } | null
  /** 当前选中的 (rowIndex, colId) */
  selectedCell: { rowIndex: number; colId: string } | null
  /** 是否可编辑（终态才允许） */
  editable: boolean
  /** 是否高亮（搜索匹配 / 选中行） */
  selected: boolean
  hover?: boolean
}>()

const emit = defineEmits<{
  startEdit: [rowIndex: number, colId: string]
  commit: [originalIndex: number, colId: string, value: unknown]
  cancel: []
  select: [rowIndex: number, colId: string]
}>()

function isCellEditing(colId: string): boolean {
  return (
    props.editingCell !== null &&
    props.editingCell.rowIndex === props.visibleIndex &&
    props.editingCell.colId === colId
  )
}

function isCellSelected(colId: string): boolean {
  return (
    props.selectedCell !== null &&
    props.selectedCell.rowIndex === props.visibleIndex &&
    props.selectedCell.colId === colId
  )
}
</script>

<template>
  <div
    class="grid-view__row"
    :class="{ 'grid-view__row--selected': selected, 'grid-view__row--hover': hover }"
  >
    <DataGridCell
      v-for="col in cols"
      :key="col.id"
      :value="rowData[col.id]"
      :col="col"
      :row-index="visibleIndex"
      :original-index="originalIndex"
      :editing="isCellEditing(col.id)"
      :selected="isCellSelected(col.id)"
      :editable="editable"
      :width="getColWidth(col.id, col.type)"
      @start-edit="(r, c) => emit('startEdit', r, c)"
      @commit="(o, c, v) => emit('commit', o, c, v)"
      @cancel="emit('cancel')"
      @select="(r, c) => emit('select', r, c)"
    />
  </div>
</template>
