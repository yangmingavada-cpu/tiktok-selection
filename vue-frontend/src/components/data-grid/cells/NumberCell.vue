<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { formatNumberForDisplay, parseNumberFromInput } from '../helpers/cell-renderers'
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

const display = computed(() => formatNumberForDisplay(props.value))

watch(
  () => props.editing,
  async (editing) => {
    if (editing) {
      // 编辑前以原始数字（不带千分位）填入，方便修改
      draft.value =
        typeof props.value === 'number' && Number.isFinite(props.value)
          ? String(props.value)
          : props.value === null || props.value === undefined
            ? ''
            : String(props.value)
      await nextTick()
      inputRef.value?.focus()
      inputRef.value?.select()
    }
  },
  { immediate: true },
)

function commit() {
  const trimmed = draft.value.trim()
  // 允许清空（提交 null）
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
  <span v-else class="data-grid__cell-number" :title="display">{{ display }}</span>
</template>
