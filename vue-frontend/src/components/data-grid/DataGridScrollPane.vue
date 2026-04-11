<script setup lang="ts">
/**
 * 右侧可滚动分栏
 *
 * 这个组件不再管理自己的滚动 —— 它只是外层 .data-grid__scroll 容器里的
 * 一个 flex 子元素。横向和纵向滚动都由外层容器统一处理：
 *  - 横向：右 pane 自然横向位移（因为它是 flex 容器里不 sticky 的子元素）
 *  - 纵向：Header (grid-view__head) 通过 CSS sticky top 钉住
 *
 * 关键：header 和 body 必须是同一个 DOM 兄弟链里的元素，这样横向滚动时
 * header 会和 body 一起移动；纵向滚动时 header 独立 sticky top 钉住。
 */
import DataGridHeader from './DataGridHeader.vue'
import DataGridRow from './DataGridRow.vue'
import type { SortDir } from './helpers/use-grid-filter-sort'
import type { DataGridColumn } from '@/types'

interface DisplayRow {
  __originalIndex: number
  data: Record<string, unknown>
}

defineProps<{
  /** 非冻结列（右 pane 渲染） */
  scrollCols: readonly DataGridColumn[]
  rows: readonly DisplayRow[]
  getColWidth: (colId: string, type: DataGridColumn['type']) => number
  getSortDir: (colId: string) => SortDir
  editingCell: { rowIndex: number; colId: string } | null
  selectedCell: { rowIndex: number; colId: string } | null
  editable: boolean
  rowHeight: number
  topPadding: number
  bottomPadding: number
  visibleStart: number
  visibleEnd: number
  isRowSelected: (originalIndex: number) => boolean
}>()

const emit = defineEmits<{
  startEdit: [rowIndex: number, colId: string]
  commit: [originalIndex: number, colId: string, value: unknown]
  cancel: []
  select: [rowIndex: number, colId: string]
  cycleSort: [colId: string]
  startResize: [colId: string, startX: number, startWidth: number]
  rename: [colId: string, newLabel: string]
  hide: [colId: string]
  remove: [colId: string]
  editOptions: [colId: string]
  moveColumn: [fromColId: string, toColId: string]
}>()
</script>

<template>
  <div class="grid-view__right">
    <DataGridHeader
      :cols="scrollCols"
      :get-col-width="getColWidth"
      :get-sort-dir="getSortDir"
      :editable="editable"
      @cycle-sort="(c: string) => emit('cycleSort', c)"
      @start-resize="(c: string, x: number, w: number) => emit('startResize', c, x, w)"
      @rename="(c: string, l: string) => emit('rename', c, l)"
      @hide="(c: string) => emit('hide', c)"
      @remove="(c: string) => emit('remove', c)"
      @edit-options="(c: string) => emit('editOptions', c)"
      @move-column="(f: string, t: string) => emit('moveColumn', f, t)"
    />

    <div class="grid-view__rows" :style="{ paddingTop: topPadding + 'px', paddingBottom: bottomPadding + 'px' }">
      <DataGridRow
        v-for="(row, idx) in rows.slice(visibleStart, visibleEnd)"
        :key="row.__originalIndex"
        :visible-index="visibleStart + idx"
        :original-index="row.__originalIndex"
        :row-data="row.data"
        :cols="scrollCols"
        :get-col-width="getColWidth"
        :editing-cell="editingCell"
        :selected-cell="selectedCell"
        :editable="editable"
        :selected="isRowSelected(row.__originalIndex)"
        @start-edit="(r, c) => emit('startEdit', r, c)"
        @commit="(o, c, v) => emit('commit', o, c, v)"
        @cancel="emit('cancel')"
        @select="(r, c) => emit('select', r, c)"
      />
    </div>
  </div>
</template>
