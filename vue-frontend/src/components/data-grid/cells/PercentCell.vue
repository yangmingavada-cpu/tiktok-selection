<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { formatPercentForDisplay, parseNumberFromInput } from '../helpers/cell-renderers'
import type { DataGridColumn } from '@/types'

const props = defineProps<{
  value: unknown
  col: DataGridColumn
  editing: boolean
  editable: boolean
}>()

const emit = defineEmits<{
  commit: [value: unknown]
  cancel: []
}>()

const draft = ref('')
const inputRef = ref<HTMLInputElement>()

const num = computed(() => {
  const n = Number(props.value)
  return Number.isFinite(n) ? n : null
})

const display = computed(() => formatPercentForDisplay(props.value))

const barWidth = computed(() => {
  if (num.value === null) return 0
  return Math.max(0, Math.min(100, num.value))
})

watch(
  () => props.editing,
  async (editing) => {
    if (editing) {
      draft.value = num.value !== null ? String(num.value) : ''
      await nextTick()
      inputRef.value?.focus()
      inputRef.value?.select()
    }
  },
  { immediate: true },
)

function commit() {
  const trimmed = draft.value.trim()
  if (trimmed === '') {
    if (props.value === null || props.value === undefined) {
      emit('cancel')
      return
    }
    emit('commit', null)
    return
  }
  const parsed = parseNumberFromInput(trimmed)
  if (parsed === null) {
    ElMessage.error('请输入有效的数字')
    inputRef.value?.focus()
    return
  }
  if (typeof props.value === 'number' && parsed === props.value) {
    emit('cancel')
    return
  }
  emit('commit', parsed)
}
</script>

<template>
  <input
    v-if="editing"
    ref="inputRef"
    v-model="draft"
    class="data-grid__cell-input data-grid__cell-input--number"
    type="text"
    inputmode="decimal"
    @blur="commit"
    @keydown.enter.prevent="commit"
    @keydown.esc.prevent="emit('cancel')"
  />
  <div v-else class="data-grid__cell-percent">
    <span class="data-grid__cell-percent-num">{{ display }}</span>
    <div class="data-grid__cell-percent-bar">
      <div class="data-grid__cell-percent-bar-fill" :style="{ width: barWidth + '%' }" />
    </div>
  </div>
</template>
