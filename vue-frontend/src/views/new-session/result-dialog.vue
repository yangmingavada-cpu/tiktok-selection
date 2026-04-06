<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { ExecSession } from './types'

const props = defineProps<{
  visible: boolean
  session: ExecSession | null
}>()

const emit = defineEmits<{
  close: []
  exportExcel: []
  savePlan: []
  continueTuning: []
}>()

const resultState = computed(() => {
  if (!props.session) return 'pending'
  if (props.session.dataState === 'pending') return 'pending'
  if (props.session.rows.length > 0) return 'ready'
  return 'empty'
})

const doneSteps = computed(() => props.session?.steps.filter(step => step.status === 'done').length ?? 0)
const totalSteps = computed(() => props.session?.steps.length ?? 0)
const latestStep = computed(() => props.session?.steps.at(-1)?.label || '后台正在准备结果数据')
const syncLabel = computed(() => {
  if (!props.session?.lastSyncedAt) return '等待首次同步'
  const delta = Math.max(0, Math.floor((Date.now() - props.session.lastSyncedAt) / 1000))
  if (delta < 2) return '刚刚同步'
  if (delta < 60) return `${delta} 秒前同步`
  return `${Math.floor(delta / 60)} 分钟前同步`
})
const statusLabel = computed(() => {
  switch (props.session?.status) {
    case 'completed':
      return '执行完成'
    case 'paused':
      return '执行暂停'
    case 'failed':
      return '执行失败'
    default:
      return '执行中'
  }
})

function formatCell(value: unknown, type: string): string {
  if (value == null || value === '') return '-'
  if (type === 'percent') return `${(Number(value) * 100).toFixed(1)}%`
  if (type === 'score') return Number(value).toFixed(1)
  if (type === 'number') return Number(value).toLocaleString()
  return String(value).slice(0, 60)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="result-overlay" @click.self="emit('close')">
      <div class="result-dialog">
        <div class="result-dialog-header">
          <div class="result-heading">
            <span class="result-eyebrow">Result Center</span>
            <h3 class="result-title">选品结果</h3>
            <p class="result-subtitle">
              执行结果会自动同步到这里。同步中不代表为空，只有明确判空后才建议你继续调整方案。
            </p>
          </div>

          <div class="result-actions">
            <el-button plain size="small" class="action-btn secondary" @click="emit('savePlan')">
              保存方案
            </el-button>
            <el-button size="small" class="action-btn success" @click="emit('exportExcel')">
              导出 Excel
            </el-button>
            <button class="result-close-btn" @click="emit('close')">×</button>
          </div>
        </div>

        <div v-if="resultState === 'pending'" class="result-state pending">
          <div class="pending-hero">
            <div class="pending-spinner-wrap">
              <div class="spinner-orb" />
              <el-icon class="spinner-icon is-loading"><Loading /></el-icon>
            </div>

            <div class="pending-copy">
              <span class="state-badge">结果同步中</span>
              <h4>后台已经在执行，结果正在回传</h4>
              <p>
                当前状态更像“正在装载结果”，不是“结果为空”。系统会继续自动拉取
                <code>currentView</code>，拿到数据后这里会立即切换成表格。
              </p>
            </div>
          </div>

          <div class="pending-grid">
            <div class="info-card">
              <span class="info-label">执行状态</span>
              <strong>{{ statusLabel }}</strong>
            </div>
            <div class="info-card">
              <span class="info-label">步骤进度</span>
              <strong>{{ doneSteps }}/{{ totalSteps || 1 }}</strong>
            </div>
            <div class="info-card">
              <span class="info-label">最近一步</span>
              <strong class="truncate">{{ latestStep }}</strong>
            </div>
            <div class="info-card">
              <span class="info-label">同步状态</span>
              <strong>{{ session?.syncState === 'syncing' ? '正在刷新' : syncLabel }}</strong>
            </div>
          </div>
        </div>

        <template v-else-if="resultState === 'ready' && session">
          <div class="result-summary">
            <div class="summary-card accent">
              <span class="summary-label">命中结果</span>
              <strong>{{ session.totalRows }}</strong>
              <small>条可用数据</small>
            </div>
            <div class="summary-card">
              <span class="summary-label">展示字段</span>
              <strong>{{ session.dims.length }}</strong>
              <small>列</small>
            </div>
            <div class="summary-card">
              <span class="summary-label">执行状态</span>
              <strong>{{ statusLabel }}</strong>
              <small>{{ syncLabel }}</small>
            </div>
            <div class="summary-card">
              <span class="summary-label">最近一步</span>
              <strong class="truncate">{{ latestStep }}</strong>
              <small>已完成 {{ doneSteps }} 步</small>
            </div>
          </div>

          <div class="table-shell">
            <el-scrollbar class="result-table-scroll">
              <table class="result-full-table">
                <thead>
                  <tr>
                    <th v-for="dim in session.dims" :key="dim.id">{{ dim.label }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rowIndex) in session.rows" :key="rowIndex">
                    <td v-for="dim in session.dims" :key="dim.id">{{ formatCell(row[dim.id], dim.type) }}</td>
                  </tr>
                </tbody>
              </table>
            </el-scrollbar>
          </div>
        </template>

        <div v-else class="result-state empty">
          <div class="empty-hero">
            <span class="state-badge warning">当前没有命中结果</span>
            <h4>这次已经明确筛不到数据了</h4>
            <p>
              现在再去调整方案才是合理动作。你可以放宽价格区间、降低销量门槛、放松评分限制，
              或者扩大类目范围后重新执行。
            </p>
            <el-button type="primary" class="tuning-btn" @click="emit('continueTuning')">
              引导我继续调整
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.result-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.36) 0%, rgba(15, 23, 42, 0.58) 100%);
  backdrop-filter: blur(10px);
}

.result-dialog {
  width: min(1180px, 94vw);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 30px;
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.12), transparent 24%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.99) 0%, rgba(248, 250, 252, 0.98) 100%);
  box-shadow: 0 32px 90px rgba(15, 23, 42, 0.28);
}

.result-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px 18px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.result-heading {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #c2410c;
}

.result-title {
  margin: 0;
  font-size: 30px;
  line-height: 1.05;
  font-weight: 800;
  color: #0f172a;
}

.result-subtitle {
  max-width: 720px;
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #64748b;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-btn {
  border-radius: 12px;
  font-weight: 600;
}

.action-btn.secondary {
  border-color: rgba(148, 163, 184, 0.24);
  color: #334155;
  background: rgba(255, 255, 255, 0.9);
}

.action-btn.success {
  border: none;
  color: #fff;
  background: linear-gradient(135deg, #16a34a 0%, #65a30d 100%);
  box-shadow: 0 12px 24px rgba(22, 163, 74, 0.22);
}

.result-close-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: #94a3b8;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.result-close-btn:hover {
  background: #fff;
  color: #334155;
  transform: rotate(90deg);
}

.result-state {
  display: flex;
  flex: 1;
  min-height: 0;
}

.pending {
  flex-direction: column;
  justify-content: center;
  gap: 26px;
  padding: 34px 28px 30px;
}

.pending-hero {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 28px;
  align-items: center;
  padding: 28px 30px;
  border-radius: 26px;
  background:
    radial-gradient(circle at 18% 20%, rgba(59, 130, 246, 0.14), transparent 28%),
    linear-gradient(180deg, rgba(239, 246, 255, 0.78) 0%, rgba(248, 250, 252, 0.94) 100%);
  border: 1px solid rgba(96, 165, 250, 0.18);
}

.pending-spinner-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 150px;
  height: 150px;
  margin: 0 auto;
}

.spinner-orb {
  position: absolute;
  inset: 10px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 30% 30%, rgba(96, 165, 250, 0.34), rgba(37, 99, 235, 0.1) 62%, transparent 64%);
  filter: blur(2px);
}

.spinner-icon {
  position: relative;
  z-index: 1;
  font-size: 56px;
  color: #2563eb;
}

.pending-copy {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pending-copy h4,
.empty-hero h4 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  color: #0f172a;
}

.pending-copy p,
.empty-hero p {
  max-width: 720px;
  margin: 0;
  font-size: 15px;
  line-height: 1.8;
  color: #64748b;
}

.state-badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.state-badge.warning {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.pending-grid,
.result-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.info-card,
.summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 104px;
  padding: 18px 18px 16px;
  border-radius: 22px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.summary-card.accent {
  background:
    radial-gradient(circle at top left, rgba(37, 99, 235, 0.14), transparent 42%),
    linear-gradient(180deg, rgba(239, 246, 255, 0.9) 0%, rgba(255, 255, 255, 0.98) 100%);
}

.info-label,
.summary-label {
  font-size: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #64748b;
}

.info-card strong,
.summary-card strong {
  font-size: 22px;
  line-height: 1.25;
  color: #0f172a;
}

.info-card .truncate,
.summary-card .truncate {
  font-size: 16px;
}

.summary-card small {
  color: #64748b;
  font-size: 13px;
}

.table-shell {
  flex: 1;
  min-height: 0;
  padding: 16px 18px 18px;
}

.result-table-scroll {
  height: 100%;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.88);
}

.result-full-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.result-full-table th,
.result-full-table td {
  padding: 13px 16px;
  text-align: left;
  border-bottom: 1px solid rgba(15, 23, 42, 0.05);
  white-space: nowrap;
}

.result-full-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  font-weight: 700;
  color: #334155;
  background: rgba(248, 250, 252, 0.96);
  backdrop-filter: blur(8px);
}

.result-full-table tbody tr:hover td {
  background: rgba(239, 246, 255, 0.6);
}

.empty {
  align-items: center;
  justify-content: center;
  padding: 32px 28px 34px;
}

.empty-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  width: min(760px, 100%);
  padding: 34px 30px;
  text-align: center;
  border-radius: 28px;
  background:
    radial-gradient(circle at top, rgba(251, 191, 36, 0.16), transparent 30%),
    linear-gradient(180deg, rgba(255, 251, 235, 0.84) 0%, rgba(255, 255, 255, 0.98) 100%);
  border: 1px solid rgba(245, 158, 11, 0.16);
}

.tuning-btn {
  margin-top: 4px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  box-shadow: 0 14px 28px rgba(79, 70, 229, 0.22);
}

code {
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.06);
  color: #0f172a;
  font-family: Consolas, 'SFMono-Regular', monospace;
}

@media (max-width: 960px) {
  .result-overlay {
    padding: 16px;
  }

  .result-dialog {
    width: 100%;
    border-radius: 24px;
  }

  .result-dialog-header {
    flex-direction: column;
    align-items: stretch;
  }

  .result-actions {
    justify-content: flex-end;
    flex-wrap: wrap;
  }

  .pending-hero {
    grid-template-columns: 1fr;
    justify-items: center;
    text-align: center;
  }

  .pending-grid,
  .result-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .result-title {
    font-size: 26px;
  }

  .pending,
  .empty {
    padding: 24px 18px;
  }

  .pending-grid,
  .result-summary {
    grid-template-columns: 1fr;
  }

  .table-shell {
    padding: 12px;
  }
}
</style>
