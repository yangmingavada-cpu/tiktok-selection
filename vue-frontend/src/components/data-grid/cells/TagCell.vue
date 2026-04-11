<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElSelect, ElOption } from 'element-plus'

import { getTagColor, getTagTextColor } from '../helpers/cell-renderers'
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

const draft = ref<string>('')
const selectRef = ref<InstanceType<typeof ElSelect>>()
/** 防止 visible-change 和 change 重复触发 cancel/commit */
const committing = ref(false)

const display = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return ''
  return String(props.value)
})

const bgColor = computed(() => getTagColor(props.value))
const textColor = computed(() => getTagTextColor(props.value))

const options = computed(() => props.col.options ?? [])

watch(
  () => props.editing,
  async (editing) => {
    if (!editing) {
      committing.value = false
      return
    }
    draft.value = display.value
    committing.value = false

    // ElSelect.focus() 只让内部 input 获得焦点，不会自动展开下拉框
    // 需要手动调 toggleMenu() 或设置 visible 来展开
    await nextTick()
    await nextTick()  // 多一帧等 DOM + popper 初始化完成
    const sel = selectRef.value as unknown as {
      focus?: () => void
      toggleMenu?: () => void
      visible?: boolean
    } | undefined
    if (!sel) return
    sel.focus?.()
    // 如果 focus 之后下拉没开（automatic-dropdown 没生效时的兜底），手动 toggle
    if (!sel.visible) {
      sel.toggleMenu?.()
    }
  },
  { immediate: true },
)

function onChange(val: string) {
  committing.value = true
  if (val === display.value) {
    emit('cancel')
    return
  }
  emit('commit', val || null)
}

function onVisibleChange(visible: boolean) {
  // 下拉关闭时：如果是 change 触发的已经走 commit/cancel 了，跳过
  if (visible || committing.value) return
  // 纯关闭（没有 change）→ 取消编辑，不提交
  emit('cancel')
}
</script>

<template>
  <ElSelect
    v-if="editing"
    ref="selectRef"
    v-model="draft"
    class="data-grid__cell-select"
    automatic-dropdown
    teleported
    clearable
    placeholder="请选择"
    @change="onChange"
    @visible-change="onVisibleChange"
  >
    <ElOption v-for="opt in options" :key="opt" :label="opt" :value="opt" />
  </ElSelect>
  <span
    v-else-if="display"
    class="data-grid__tag"
    :style="{ background: bgColor, color: textColor }"
    :title="display"
  >{{ display }}</span>
  <span v-else class="data-grid__tag data-grid__tag--empty">—</span>
</template>
