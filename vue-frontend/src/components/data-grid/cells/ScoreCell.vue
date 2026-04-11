<script setup lang="ts">
import { computed, inject, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { getScoreColor, parseNumberFromInput } from '../helpers/cell-renderers'
import { COL_SCALES_KEY } from '../constants'
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

// 从 DataGrid 顶层 inject 动态 scale（= 该列 tableData 里的 max 值）
// 拿不到时回退到 10（与旧行为兼容）
const colScales = inject(COL_SCALES_KEY, null)

const scale = computed(() => {
  if (!colScales) return 10
  return colScales.value[props.col.id] ?? 10
})

const num = computed(() => {
  const n = Number(props.value)
  return Number.isFinite(n) ? n : null
})

const display = computed(() => (num.value !== null ? num.value.toFixed(1) : ''))

// value / scale 得到 0-1 比例，同时驱动 bar 宽度和颜色，保证两者一致
const ratio = computed(() => {
  if (num.value === null) return 0
  const s = scale.value
  if (s <= 0) return 0
  return Math.max(0, Math.min(1, num.value / s))
})

const barWidth = computed(() => ratio.value * 100)

const fillColor = computed(() => (num.value !== null ? getScoreColor(ratio.value) : '#cbd5e1'))

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
  <div v-else class="data-grid__cell-score">
    <span class="data-grid__cell-score-num" :style="{ color: fillColor }">{{ display }}</span>
    <div class="data-grid__cell-score-bar">
      <div
        class="data-grid__cell-score-bar-fill"
        :style="{ width: barWidth + '%', background: fillColor }"
      />
    </div>
  </div>
</template>
