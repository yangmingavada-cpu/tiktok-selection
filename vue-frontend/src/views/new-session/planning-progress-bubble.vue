<script setup lang="ts">
import { computed, shallowRef } from 'vue'
import type { PlanningStatus, PlanningTraceEntry, StepItem } from './types'

const props = defineProps<{
  status: PlanningStatus
  steps: StepItem[]
  thinkingText: string
  traceEntries: PlanningTraceEntry[]
  sessionId: string
}>()

const expanded = shallowRef(false)

const statusLabel = computed(() => {
  if (props.status === 'running') return 'AI 正在规划中'
  if (props.status === 'completed') return '规划已完成'
  if (props.status === 'needs_input') return '等待补充信息'
  if (props.status === 'failed') return '规划已结束'
  return '规划轨迹'
})

const canExpand = computed(() => props.traceEntries.length > 0 || !!props.thinkingText)

function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}
</script>

<template>
  <div class="message-row ai">
    <div class="avatar">🤖</div>
    <div class="bubble-wrap">
      <div class="bubble ai progress-bubble">
        <div class="progress-header">
          <div class="progress-title">
            <span class="status-dot" :class="status" />
            <span>{{ statusLabel }}</span>
          </div>
          <button
            v-if="canExpand"
            type="button"
            class="trace-toggle"
            @click="expanded = !expanded"
          >
            {{ expanded ? '收起详细过程' : '查看详细过程' }}
          </button>
        </div>

        <div v-if="steps.length === 0" class="thinking-dots">
          <span />
          <span />
          <span />
        </div>

        <div v-else class="step-list">
          <div
            v-for="(step, i) in steps"
            :key="i"
            class="step-item"
            :class="step.success ? 'done' : 'fail'"
          >
            <span class="step-icon">{{ step.success ? '✓' : '✗' }}</span>
            <span class="step-label">{{ step.label }}</span>
          </div>
        </div>

        <div v-if="status === 'running'" class="step-item running">
          <span class="step-icon spin">◌</span>
          <span class="step-label">AI 正在推导下一步...</span>
        </div>

        <div v-if="thinkingText" class="thinking-preview">{{ thinkingText }}</div>

        <div v-if="expanded" class="trace-panel">
          <div class="trace-meta">
            <span class="trace-meta-item">状态：{{ statusLabel }}</span>
            <span v-if="sessionId" class="trace-meta-item">会话：{{ sessionId }}</span>
          </div>

          <div v-if="traceEntries.length" class="trace-timeline">
            <div
              v-for="entry in traceEntries"
              :key="entry.id"
              class="trace-entry"
              :class="entry.kind"
            >
              <div class="trace-entry-head">
                <span class="trace-kind">{{ entry.kind }}</span>
                <span class="trace-time">{{ formatTime(entry.createdAt) }}</span>
              </div>
              <pre class="trace-text">{{ entry.text }}</pre>
            </div>
          </div>

          <div v-else class="trace-empty">本轮还没有更多可展示的规划日志。</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
}

.avatar {
  font-size: 22px;
  flex-shrink: 0;
  margin-top: 2px;
}

.bubble-wrap {
  flex: 1;
  min-width: 0;
  max-width: none;
}

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}

.bubble.ai {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-top-left-radius: 2px;
}

.progress-bubble {
  padding: 12px 16px;
  width: 100%;
  min-width: 0;
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.progress-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-weight: 600;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #9ca3af;
}

.status-dot.running {
  background: #2563eb;
  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.12);
}

.status-dot.completed {
  background: #16a34a;
}

.status-dot.needs_input {
  background: #d97706;
}

.status-dot.failed {
  background: #dc2626;
}

.trace-toggle {
  border: none;
  background: transparent;
  color: #4f46e5;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  white-space: nowrap;
}

.trace-toggle:hover {
  color: #3730a3;
}

.thinking-dots {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 2px 0;
}

.thinking-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6b7280;
  animation: bounce 1.2s infinite;
}

.thinking-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.thinking-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.4;
}

.step-icon {
  flex-shrink: 0;
  width: 16px;
  text-align: center;
  font-size: 12px;
}

.step-item.done .step-icon {
  color: #22c55e;
}

.step-item.fail .step-icon {
  color: #ef4444;
}

.step-item.running {
  margin-top: 6px;
}

.step-item.running .step-icon {
  color: #6366f1;
}

.step-item.running .step-label {
  color: #6b7280;
}

.step-label {
  color: #374151;
  word-break: break-all;
}

.thinking-preview {
  font-size: 12px;
  color: #6b7280;
  margin-top: 8px;
  padding: 8px 10px;
  max-height: 140px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  border-left: 2px solid #e5e7eb;
  line-height: 1.5;
  background: #f9fafb;
  border-radius: 0 6px 6px 0;
}

.trace-panel {
  margin-top: 10px;
  border-top: 1px solid #e5e7eb;
  padding-top: 10px;
}

.trace-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.trace-meta-item {
  font-size: 12px;
  color: #4b5563;
  background: #f3f4f6;
  padding: 3px 8px;
  border-radius: 999px;
}

.trace-timeline {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow: auto;
  padding-right: 4px;
}

.trace-entry {
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fafafa;
}

.trace-entry.step {
  background: #f8fafc;
}

.trace-entry.thinking {
  background: #eef2ff;
  border-color: #c7d2fe;
}

.trace-entry.error {
  background: #fef2f2;
  border-color: #fecaca;
}

.trace-entry-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.trace-kind {
  font-size: 11px;
  text-transform: uppercase;
  color: #6b7280;
  letter-spacing: 0.04em;
}

.trace-time {
  font-size: 11px;
  color: #9ca3af;
}

.trace-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}

.trace-empty {
  font-size: 12px;
  color: #6b7280;
}

@media (max-width: 768px) {
  .progress-bubble {
    padding: 12px;
  }

  .progress-header {
    align-items: flex-start;
  }

  .trace-toggle {
    width: 100%;
    text-align: left;
  }
}

.spin {
  display: inline-block;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); }
  40% { transform: translateY(-6px); }
}
</style>
