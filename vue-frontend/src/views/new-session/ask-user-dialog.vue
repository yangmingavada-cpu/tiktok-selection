<script setup lang="ts">
import { shallowRef } from 'vue'
import type { AskDialogState } from './types'

const props = defineProps<{
  dialog: AskDialogState
}>()

const emit = defineEmits<{
  selectOption: [option: string]
  customReply: [text: string]
  close: []
}>()

const customText = shallowRef('')

function handleCustom() {
  const text = customText.value.trim()
  if (!text) return
  customText.value = ''
  emit('customReply', text)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="props.dialog.visible" class="ask-overlay" @click.self="emit('close')">
      <div class="ask-dialog">
        <div class="ask-header">
          <span class="ask-question">{{ props.dialog.question }}</span>
          <button class="ask-close" @click="emit('close')">✕</button>
        </div>
        <div class="ask-options">
          <div
            v-for="(s, i) in props.dialog.suggestions"
            :key="i"
            class="ask-option"
            @click="emit('selectOption', s)"
          >
            <span class="ask-option-num">{{ i + 1 }}</span>
            <span class="ask-option-text">{{ s }}</span>
          </div>
        </div>
        <div class="ask-custom-row">
          <input
            v-model="customText"
            class="ask-custom-input"
            placeholder="或者自己描述..."
            @keydown.enter="handleCustom"
          />
          <button class="ask-skip-btn" @click="emit('close')">跳过</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ask-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.ask-dialog {
  background: #fff;
  border-radius: 16px;
  width: 480px;
  max-width: 92vw;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.18);
  overflow: hidden;
}

.ask-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 22px 22px 16px;
  gap: 12px;
}

.ask-question { font-size: 16px; font-weight: 600; color: #111827; line-height: 1.5; flex: 1; }
.ask-close { background: none; border: none; font-size: 16px; color: #9ca3af; cursor: pointer; padding: 0; line-height: 1; flex-shrink: 0; }
.ask-close:hover { color: #374151; }

.ask-options { padding: 0 12px 8px; display: flex; flex-direction: column; gap: 2px; }

.ask-option {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.12s;
}
.ask-option:hover { background: #f3f4f6; }

.ask-option-num {
  width: 26px; height: 26px; border-radius: 50%;
  background: #f3f4f6; color: #374151; font-size: 13px; font-weight: 500;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.ask-option-text { font-size: 14px; color: #1f2937; }

.ask-custom-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px 14px;
  border-top: 1px solid #f0f0f0;
  margin-top: 6px;
}

.ask-custom-input {
  flex: 1;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  color: #111827;
  outline: none;
}
.ask-custom-input:focus { border-color: #6366f1; }
.ask-custom-input::placeholder { color: #9ca3af; }

.ask-skip-btn {
  background: none;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
}
.ask-skip-btn:hover { background: #f9fafb; }
</style>
