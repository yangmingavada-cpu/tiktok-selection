<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import PlanningProgressBubble from './planning-progress-bubble.vue'
import PlanDraftCard from './plan-draft-card.vue'
import type { ChatMessage } from './types'

const props = defineProps<{
  msg: ChatMessage
  creating: boolean
  confirmingDraft?: boolean
}>()

const emit = defineEmits<{
  rejectPlan: []
  confirmPlan: [plan: unknown[], sourceText?: string]
  confirmDraft: []
  rejectDraft: []
}>()

function renderMd(text: string): string {
  return marked.parse(text, { async: false }) as string
}

function isPreviewEmpty(preview: ChatMessage['preview']): boolean {
  return Boolean(preview && preview !== 'loading' && preview.status === 'empty')
}
</script>

<template>
  <PlanningProgressBubble
    v-if="msg.planningSnapshot"
    :status="msg.planningSnapshot.status"
    :steps="msg.planningSnapshot.steps"
    :thinking-text="msg.planningSnapshot.thinkingText"
    :trace-entries="msg.planningSnapshot.traceEntries"
    :session-id="msg.planningSnapshot.sessionId"
  />
  <div v-else :class="['message-row', msg.role]">
    <div class="avatar">{{ msg.role === 'ai' ? '🤖' : '👤' }}</div>
    <div class="bubble-wrap">
      <div class="bubble" :class="msg.role">
        <div v-if="msg.text && msg.role === 'ai'" class="msg-text msg-md" v-html="renderMd(msg.text)" />
        <pre v-else-if="msg.text" class="msg-text">{{ msg.text }}</pre>

        <!-- 方案卡片 -->
        <div v-if="msg.plan" class="plan-card">
          <div class="plan-topbar">
            <span class="plan-topbar-title">📋 AI 选品方案</span>
            <span class="preview-badge">
              <template v-if="msg.preview === 'loading'">
                <el-icon class="preview-spin"><Loading /></el-icon>
                <span class="preview-text checking">验证数据中...</span>
              </template>
              <template v-else-if="msg.preview?.status === 'ok'">
                <span class="preview-text ok">✓ {{ msg.preview.message }}</span>
              </template>
              <template v-else-if="msg.preview?.status === 'empty'">
                <span class="preview-text empty">⚠ {{ msg.preview.message }}</span>
              </template>
              <template v-else-if="msg.preview?.status === 'error'">
                <span class="preview-text error">✗ 数据验证失败</span>
              </template>
            </span>
          </div>

          <div class="plan-body">
            <div v-if="msg.interpretation === 'loading'" class="plan-interpret-loading">
              <el-icon class="preview-spin"><Loading /></el-icon>
              <span>正在生成方案解读报告...</span>
            </div>
            <div
              v-else-if="msg.interpretation"
              class="plan-interpretation"
              v-html="renderMd(msg.interpretation)"
            />
            <p v-else class="plan-fallback-summary">{{ msg.summary || '选品方案已生成，请确认是否创建。' }}</p>
          </div>

          <div class="plan-actions">
            <el-button
              size="small"
              :disabled="!msg.interpretationDone"
              @click="emit('rejectPlan')"
            >
              {{ isPreviewEmpty(msg.preview) ? '引导我调整' : '继续调整' }}
            </el-button>
            <el-button
              size="small"
              type="primary"
              :loading="props.creating"
              :disabled="!msg.interpretationDone"
              @click="emit('confirmPlan', msg.plan!, msg.sourceText)"
            >确认创建</el-button>
          </div>
        </div>

        <!-- 规划草稿确认卡片 -->
        <PlanDraftCard
          v-else-if="msg.planDraft"
          :plan="msg.planDraft"
          :confirming="props.confirmingDraft ?? false"
          @confirm="emit('confirmDraft')"
          @reject="emit('rejectDraft')"
        />

        <!-- 执行状态卡片 -->
        <div v-else-if="msg.execCard" class="exec-card">
          <div class="exec-card-header">
            <template v-if="msg.execCard.status === 'running'">
              <el-icon class="exec-spin"><Loading /></el-icon>
              <span>正在执行选品任务...</span>
            </template>
            <span v-else-if="msg.execCard.status === 'completed'" class="exec-status done">✅ 选品完成</span>
            <span v-else-if="msg.execCard.status === 'failed'" class="exec-status fail">❌ 执行失败</span>
            <span v-else class="exec-status paused">⏸ 已暂停</span>
          </div>

          <div v-if="msg.execCard.steps.length" class="exec-steps">
            <div
              v-for="(step, i) in msg.execCard.steps"
              :key="i"
              class="exec-step"
              :class="step.status"
            >
              <span class="exec-step-icon">{{ step.status === 'done' ? '✓' : step.status === 'fail' ? '✗' : '⟳' }}</span>
              {{ step.label }}
            </div>
          </div>

          <div v-if="msg.execCard.errorMsg" class="exec-error">{{ msg.execCard.errorMsg }}</div>

          <div v-if="msg.execCard.status === 'completed' || msg.execCard.status === 'paused'" class="exec-card-footer">
            <router-link :to="{ name: 'SessionDetail', params: { id: msg.execCard.id } }" class="exec-view-link">
              查看选品结果 →
            </router-link>
          </div>
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
}
.message-row.user { flex-direction: row-reverse; }

.avatar {
  font-size: 22px;
  flex-shrink: 0;
  margin-top: 2px;
}

.bubble-wrap { max-width: 72%; }

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
.bubble.user {
  background: #6366f1;
  color: #fff;
  border-top-right-radius: 2px;
}

.msg-text {
  margin: 0;
  font-family: inherit;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-md { white-space: normal; line-height: 1.7; }
.msg-md :deep(p) { margin: 4px 0; }
.msg-md :deep(p:first-child) { margin-top: 0; }
.msg-md :deep(p:last-child) { margin-bottom: 0; }
.msg-md :deep(ul), .msg-md :deep(ol) { padding-left: 18px; margin: 4px 0; }
.msg-md :deep(li) { margin: 2px 0; }
.msg-md :deep(strong) { font-weight: 600; }
.msg-md :deep(code) { background: rgba(0,0,0,0.06); padding: 1px 4px; border-radius: 3px; font-size: 12px; }
.msg-md :deep(h1), .msg-md :deep(h2), .msg-md :deep(h3) { font-weight: 600; margin: 8px 0 4px; }
.msg-md :deep(blockquote) { border-left: 3px solid #6366f1; padding: 4px 10px; margin: 6px 0; background: rgba(99,102,241,0.06); border-radius: 0 4px 4px 0; }

/* Plan card */
.plan-card {
  margin-top: 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.08);
}
.plan-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: linear-gradient(90deg, #eff6ff 0%, #f0fdf4 100%);
  border-bottom: 1px solid #dbeafe;
}
.plan-topbar-title { font-size: 13px; font-weight: 600; color: #1d4ed8; }

.plan-body { padding: 20px 20px 12px; background: #fff; }

.plan-interpret-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: #6b7280;
  font-size: 13px;
  justify-content: center;
}
.plan-fallback-summary { font-size: 14px; line-height: 1.7; color: #374151; margin: 0; }

.plan-interpretation { font-size: 14px; line-height: 1.75; color: #1f2937; }
.plan-interpretation :deep(h2) { font-size: 15px; font-weight: 700; color: #1d4ed8; margin: 16px 0 8px; padding-bottom: 4px; border-bottom: 1px solid #e0e7ff; }
.plan-interpretation :deep(h2:first-child) { margin-top: 0; }
.plan-interpretation :deep(h3) { font-size: 14px; font-weight: 600; color: #374151; margin: 14px 0 6px; }
.plan-interpretation :deep(p) { margin: 6px 0; color: #374151; }
.plan-interpretation :deep(ul), .plan-interpretation :deep(ol) { padding-left: 20px; margin: 6px 0; }
.plan-interpretation :deep(li) { margin: 4px 0; color: #374151; }
.plan-interpretation :deep(strong) { color: #111827; font-weight: 600; }
.plan-interpretation :deep(blockquote) { margin: 12px 0; padding: 8px 14px; border-left: 3px solid #6366f1; background: #f5f3ff; border-radius: 0 6px 6px 0; color: #4b5563; font-size: 13px; }
.plan-interpretation :deep(hr) { border: none; border-top: 1px solid #e5e7eb; margin: 14px 0; }
.plan-interpretation :deep(code) { background: #f3f4f6; padding: 1px 5px; border-radius: 3px; font-size: 12px; color: #4338ca; }

.plan-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 8px 12px;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
}

.preview-badge { display: flex; align-items: center; gap: 4px; margin-left: auto; font-size: 12px; }
.preview-spin { animation: spin 1s linear infinite; color: #6b7280; }
.preview-text { font-size: 12px; }
.preview-text.checking { color: #6b7280; }
.preview-text.ok { color: #16a34a; font-weight: 500; }
.preview-text.empty { color: #d97706; font-weight: 500; }
.preview-text.error { color: #dc2626; }

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Execution card */
.exec-card {
  margin-top: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
  font-size: 13px;
}
.exec-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: #f9fafb;
  border-bottom: 1px solid #f0f0f0;
  font-weight: 500;
  color: #374151;
}
.exec-spin { animation: spin 1s linear infinite; color: #6b7280; }
.exec-status.done { color: #16a34a; }
.exec-status.fail { color: #dc2626; }
.exec-status.paused { color: #d97706; }

.exec-steps {
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.exec-step {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #6b7280;
  font-size: 12px;
}
.exec-step.done { color: #16a34a; }
.exec-step.fail { color: #dc2626; }
.exec-step.running { color: #6366f1; }
.exec-step-icon { width: 14px; text-align: center; flex-shrink: 0; }

.exec-error {
  padding: 6px 14px 10px;
  color: #dc2626;
  font-size: 12px;
}
.exec-card-footer {
  padding: 10px 14px;
  border-top: 1px solid #f0f0f0;
  background: #f9fafb;
}
.exec-view-link {
  color: #6366f1;
  font-weight: 500;
  text-decoration: none;
  font-size: 13px;
}
.exec-view-link:hover { text-decoration: underline; }
</style>
