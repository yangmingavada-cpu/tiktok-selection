<script setup lang="ts">
import { computed } from 'vue'
import type { ExecSession } from './types'

const props = defineProps<{
  session: ExecSession
}>()

const emit = defineEmits<{
  viewResult: []
}>()

const STATUS_LABEL: Record<string, string> = {
  running: '执行中', paused: '已暂停', completed: '已完成', failed: '执行失败',
}

const statusTagType = computed(() => {
  const s = props.session.status
  if (s === 'completed') return 'success'
  if (s === 'failed') return 'danger'
  if (s === 'paused') return 'warning'
  return ''
})

const progressPct = computed(() => {
  const s = props.session
  if (s.status === 'completed' || s.status === 'paused' || s.status === 'failed') return 100
  const done = s.steps.filter(x => x.status === 'done' || x.status === 'fail').length
  const total = s.steps.length
  return total === 0 ? 10 : Math.min(95, Math.round(done / total * 100))
})
</script>

<template>
  <div class="results-panel">
    <div class="results-header">
      <span class="results-title">执行进度</span>
      <el-tag :type="statusTagType" size="small">{{ STATUS_LABEL[session.status] }}</el-tag>
      <el-button
        v-if="session.status === 'completed' || session.status === 'paused'"
        type="primary" size="small" style="margin-left:auto"
        @click="emit('viewResult')"
      >查看结果</el-button>
    </div>

    <div class="exec-progress-wrap">
      <el-progress
        :percentage="progressPct"
        :status="session.status === 'failed' ? 'exception' : session.status === 'completed' ? 'success' : undefined"
        :striped="session.status === 'running'"
        :striped-flow="session.status === 'running'"
        :duration="8"
      />
    </div>

    <el-scrollbar class="exec-steps-scroll">
      <div class="exec-steps">
        <div v-for="(s, si) in session.steps" :key="si" class="exec-step-item" :class="s.status">
          <span class="exec-step-icon" :class="{ spin: s.status === 'running' }">
            {{ s.status === 'done' ? '✓' : s.status === 'fail' ? '✗' : '⟳' }}
          </span>
          <span class="exec-step-label">{{ s.label }}</span>
        </div>
        <div v-if="session.status === 'running'" class="exec-step-item running">
          <span class="exec-step-icon spin">⟳</span>
          <span class="exec-step-label">正在执行...</span>
        </div>
      </div>
    </el-scrollbar>

    <div v-if="session.status === 'failed'" class="results-error">
      ✗ {{ session.errorMsg || '执行失败，请重试' }}
    </div>
  </div>
</template>

<style scoped>
.results-panel {
  width: 420px;
  flex-shrink: 0;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.results-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}
.results-title { color: #111827; }

.exec-progress-wrap { padding: 12px 16px 8px; flex-shrink: 0; }

.exec-steps-scroll { flex: 1; min-height: 0; }
.exec-steps { padding: 8px 16px 12px; display: flex; flex-direction: column; gap: 5px; }

.exec-step-item { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.exec-step-icon { width: 16px; text-align: center; flex-shrink: 0; font-size: 12px; }

.exec-step-item.done .exec-step-icon   { color: #22c55e; }
.exec-step-item.fail .exec-step-icon   { color: #ef4444; }
.exec-step-item.running .exec-step-icon { color: #6366f1; }
.exec-step-item.running .exec-step-label { color: #6b7280; }
.exec-step-label { color: #374151; }

.results-error { padding: 12px 16px; color: #ef4444; font-size: 13px; }

.spin { display: inline-block; animation: spin 1s linear infinite; }

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
