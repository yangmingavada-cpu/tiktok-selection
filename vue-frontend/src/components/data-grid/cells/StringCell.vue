<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import { isHttpUrl, isImageUrl } from '../helpers/cell-renderers'
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
const imgFailed = ref(false)

const stringValue = computed(() => {
  if (props.value === null || props.value === undefined) return ''
  return String(props.value)
})

const renderedAsImage = computed(() => isImageUrl(props.value, props.col.id) && !imgFailed.value)
const renderedAsLink = computed(() => !renderedAsImage.value && isHttpUrl(props.value))

watch(
  () => props.editing,
  async (editing) => {
    if (editing) {
      draft.value = stringValue.value
      await nextTick()
      inputRef.value?.focus()
      inputRef.value?.select()
    }
  },
  { immediate: true },
)

watch(
  () => props.value,
  () => {
    imgFailed.value = false
  },
)

function commit() {
  if (draft.value === stringValue.value) {
    emit('cancel')
    return
  }
  emit('commit', draft.value)
}

function onImgError() {
  imgFailed.value = true
}
</script>

<template>
  <input
    v-if="editing"
    ref="inputRef"
    v-model="draft"
    class="data-grid__cell-input"
    type="text"
    @blur="commit"
    @keydown.enter.prevent="commit"
    @keydown.esc.prevent="emit('cancel')"
  />
  <template v-else>
    <img
      v-if="renderedAsImage"
      :src="stringValue"
      :alt="col.label"
      class="data-grid__cell-thumb"
      loading="lazy"
      @error="onImgError"
    />
    <a
      v-else-if="renderedAsLink"
      :href="stringValue"
      target="_blank"
      rel="noopener noreferrer"
      class="data-grid__cell-link"
      :title="stringValue"
      @click.stop
    >{{ stringValue }}</a>
    <span v-else class="data-grid__cell-text" :title="stringValue">{{ stringValue }}</span>
  </template>
</template>
