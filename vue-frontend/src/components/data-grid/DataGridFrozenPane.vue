<script setup lang="ts">
/**
 * 左侧冻结分栏
 * 包含：checkbox 列 / 行号列 / 冻结的数据列
 *
 * 这个组件不再管理自己的滚动 —— 它只是外层 .data-grid__scroll 容器里的
 * 一个 sticky-left 的子元素。纵向滚动由外层容器统一处理，左右 pane 在同一个
 * 滚动容器里天然同步，无需 JS 同步。
 *
 * Header (grid-view__head) 通过 CSS sticky top 纵向钉住；左 pane 整体通过
 * CSS sticky left 横向钉住，top-left corner 自动双向 sticky。
 */
import { ElCheckbox } from 'element-plus'

import DataGridColumnHeader from './DataGridColumnHeader.vue'
import DataGridRow from './DataGridRow.vue'
import type { SortDir } from './helpers/use-grid-filter-sort'
import type { DataGridColumn } from '@/types'

interface DisplayRow {
  __originalIndex: number
  data: Record<string, unknown>
}

defineProps<{
  /** 冻结的列（左 pane 渲染） */
  frozenCols: readonly DataGridColumn[]
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
  /** 行选中状态查询 */
  isRowSelected: (originalIndex: number) => boolean
  /** 全选当前所有可视行的 checkbox 状态 */
  allCheckboxState: 'none' | 'partial' | 'all'
  width: number
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
  toggleRow: [originalIndex: number]
  toggleAll: []
}>()

function getRowNumberClass(num: number): string {
  return num > 9999 ? 'grid-view__row-count grid-view__row-count--small' : 'grid-view__row-count'
}
</script>

<template>
  <div class="grid-view__left" :style="{ width: width + 'px' }">
    <!-- Header: sticky top via CSS -->
    <div class="grid-view__head">
      <div class="data-grid__head-checkbox">
        <ElCheckbox
          :model-value="allCheckboxState === 'all'"
          :indeterminate="allCheckboxState === 'partial'"
          @change="emit('toggleAll')"
        />
      </div>
      <div class="grid-view__head-row-identifier">#</div>
      <DataGridColumnHeader
        v-for="col in frozenCols"
        :key="col.id"
        :col="col"
        :width="getColWidth(col.id, col.type)"
        :sort-dir="getSortDir(col.id)"
        :editable="editable"
        @cycle-sort="(c: string) => emit('cycleSort', c)"
        @start-resize="(c: string, x: number, w: number) => emit('startResize', c, x, w)"
        @rename="(c: string, l: string) => emit('rename', c, l)"
        @hide="(c: string) => emit('hide', c)"
        @remove="(c: string) => emit('remove', c)"
        @edit-options="(c: string) => emit('editOptions', c)"
        @move-column="(f: string, t: string) => emit('moveColumn', f, t)"
      />
    </div>

    <!-- Body: 直接渲染，padding 由虚拟滚动驱动 -->
    <div class="grid-view__rows" :style="{ paddingTop: topPadding + 'px', paddingBottom: bottomPadding + 'px' }">
      <div
        v-for="(row, idx) in rows.slice(visibleStart, visibleEnd)"
        :key="row.__originalIndex"
        class="grid-view__row"
        :class="{ 'grid-view__row--selected': isRowSelected(row.__originalIndex) }"
        :style="{ height: rowHeight + 'px' }"
      >
        <div class="data-grid__row-checkbox">
          <ElCheckbox
            :model-value="isRowSelected(row.__originalIndex)"
            @change="emit('toggleRow', row.__originalIndex)"
          />
        </div>
        <div class="grid-view__row-info">
          <span :class="getRowNumberClass(visibleStart + idx + 1)">
            {{ visibleStart + idx + 1 }}
          </span>
        </div>
        <DataGridRow
          v-if="frozenCols.length > 0"
          :visible-index="visibleStart + idx"
          :original-index="row.__originalIndex"
          :row-data="row.data"
          :cols="frozenCols"
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
  </div>
</template>
