<script setup lang="ts">
/**
 * 单 pane 内的表头行
 * 渲染一组 DataGridColumnHeader；左 pane 还会有行号格 + checkbox 全选
 */
import DataGridColumnHeader from './DataGridColumnHeader.vue'
import type { SortDir } from './helpers/use-grid-filter-sort'
import type { DataGridColumn } from '@/types'

defineProps<{
  cols: readonly DataGridColumn[]
  getColWidth: (colId: string, type: DataGridColumn['type']) => number
  getSortDir: (colId: string) => SortDir
  editable: boolean
}>()

defineEmits<{
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
  <div class="grid-view__head">
    <DataGridColumnHeader
      v-for="col in cols"
      :key="col.id"
      :col="col"
      :width="getColWidth(col.id, col.type)"
      :sort-dir="getSortDir(col.id)"
      :editable="editable"
      @cycle-sort="(c: string) => $emit('cycleSort', c)"
      @start-resize="(c: string, x: number, w: number) => $emit('startResize', c, x, w)"
      @rename="(c: string, l: string) => $emit('rename', c, l)"
      @hide="(c: string) => $emit('hide', c)"
      @remove="(c: string) => $emit('remove', c)"
      @edit-options="(c: string) => $emit('editOptions', c)"
      @move-column="(f: string, t: string) => $emit('moveColumn', f, t)"
    />
  </div>
</template>
