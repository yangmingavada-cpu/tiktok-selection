<script setup lang="ts">
/**
 * 单元格 dispatcher
 * 按 col.type 分流到具体 cell renderer 组件。
 * 参考 Baserow GridViewCell 的双组件渲染策略简化版。
 */
import { computed } from 'vue'

import StringCell from './cells/StringCell.vue'
import NumberCell from './cells/NumberCell.vue'
import PercentCell from './cells/PercentCell.vue'
import ScoreCell from './cells/ScoreCell.vue'
import TagCell from './cells/TagCell.vue'
import type { DataGridColumn } from '@/types'

const props = defineProps<{
  value: unknown
  col: DataGridColumn
  rowIndex: number   // displayRow 的可视下标（DataGridRow 注入）
  originalIndex: number   // 原数组下标（用于编辑回传）
  editing: boolean
  selected: boolean
  editable: boolean
  invalid?: boolean
  width: number
}>()

const emit = defineEmits<{
  startEdit: [rowIndex: number, colId: string]
  commit: [originalIndex: number, colId: string, value: unknown]
  cancel: []
  select: [rowIndex: number, colId: string]
}>()

const cellComponent = computed(() => {
  switch (props.col.type) {
    case 'number':
      return NumberCell
    case 'percent':
      return PercentCell
    case 'score':
      return ScoreCell
    case 'tag':
      return TagCell
    default:
      return StringCell
  }
})

const cellClass = computed(() => ({
  'grid-view__cell': true,
  active: props.selected,
  editing: props.editing,
  invalid: props.invalid,
  'grid-view__cell--editable': props.editable,
}))

function onClick() {
  emit('select', props.rowIndex, props.col.id)
}

function onDoubleClick() {
  if (!props.editable) return
  emit('startEdit', props.rowIndex, props.col.id)
}

function onCommit(value: unknown) {
  emit('commit', props.originalIndex, props.col.id, value)
}

function onCancel() {
  emit('cancel')
}
</script>

<template>
  <div
    class="grid-view__column"
    :style="{ width: width + 'px' }"
    @click="onClick"
    @dblclick="onDoubleClick"
  >
    <div :class="cellClass">
      <component
        :is="cellComponent"
        :value="value"
        :col="col"
        :editing="editing"
        :editable="editable"
        @commit="onCommit"
        @cancel="onCancel"
      />
    </div>
  </div>
</template>
