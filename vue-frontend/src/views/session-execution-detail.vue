<script setup lang="ts">
import { shallowRef, reactive, computed, ref } from 'vue'
import type { Component } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Refresh, Clock, Loading, CircleCheck, CircleClose, Remove, VideoPause } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { useSession } from '@/composables/useSession'
import { getStatusLabel, getStatusTagType } from '@/constants'
import type { ColumnDim } from '@/types'

const route = useRoute()
const sessionId = computed(() => route.params.id as string)

const {
  session, steps, tableData, tableCols, currentView,
  fetchSession, fetchSteps,
  handleExport, handleSavePlan,
} = useSession(sessionId)

const savePlanVisible = shallowRef(false)
const saving = shallowRef(false)
const planForm = reactive({ name: '', description: '' })

function formatCell(col: ColumnDim, val: unknown): string {
  if (val === null || val === undefined) return '-'
  if (col.type === 'number'  && typeof val === 'number') return val.toLocaleString()
  if (col.type === 'percent' && typeof val === 'number') return val.toFixed(1) + '%'
  if (col.type === 'score'   && typeof val === 'number') return val.toFixed(1)
  return String(val)
}

function cellClass(col: ColumnDim, val: unknown): string {
  if (col.type === 'score' && typeof val === 'number') {
    if (val >= 80) return 'score-high'
    if (val >= 60) return 'score-mid'
    return 'score-low'
  }
  return ''
}

async function confirmSavePlan() {
  saving.value = true
  const ok = await handleSavePlan(planForm.name, planForm.description)
  saving.value = false
  if (ok) {
    savePlanVisible.value = false
    planForm.name = ''
    planForm.description = ''
  }
}

async function refreshData() {
  await Promise.all([fetchSession(), fetchSteps()])
}

const planIntroExpanded = ref(false)
const interpretationActive = ref<string[]>([])

const planInterpretation = computed<string>(() => {
  const msgs = session.conversationSnapshot?.messages ?? []
  const msg = msgs.find(m => m.role === 'ai' && typeof m.interpretation === 'string' && m.interpretation)
  return (msg?.interpretation as string) ?? ''
})

function renderMd(text: string): string {
  return marked.parse(text, { async: false }) as string
}

const STEP_STATUS_ICON: Record<string, { icon: Component; cls: string }> = {
  pending:   { icon: Clock,        cls: 'step-pending' },
  running:   { icon: Loading,      cls: 'step-running' },
  completed: { icon: CircleCheck,  cls: 'step-done'    },
  failed:    { icon: CircleClose,  cls: 'step-failed'  },
  skipped:   { icon: Remove,       cls: 'step-skipped' },
  paused:    { icon: VideoPause,   cls: 'step-paused'  },
}

const mergedSteps = computed(() => {
  const chain = session.blockChain ?? []
  return chain.map((block, idx) => {
    const step = steps.value.find(s => s.seq === idx + 1)
    return { block, step }
  })
})

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

function formatBlockType(type: string): string {
  if (type.startsWith('SOURCE_'))    return '数据源'
  if (type.startsWith('SCORE_'))     return '评分'
  if (type.startsWith('ENRICH_'))    return '富化'
  if (type.startsWith('ANNOTATE_'))  return '标注'
  if (type.startsWith('COMPUTE_'))   return '计算'
  if (type.startsWith('FILTER_'))    return '过滤'
  if (type.startsWith('TRANSFORM_')) return '变换'
  if (type.startsWith('SORT_'))      return '排序'
  if (type.startsWith('TRAVERSE_'))  return '关联'
  if (type.startsWith('CONTROL_'))   return '控制'
  if (type.startsWith('OUTPUT_'))    return '输出'
  return '其他'
}


</script>

<template>
  <div class="session-detail">
    <!-- 顶部栏 -->
    <div class="top-bar">
      <el-button :icon="ArrowLeft" text @click="$router.push('/sessions')">返回记录</el-button>
      <span class="session-title-text">{{ session.title || '选品详情' }}</span>
      <el-tag :type="getStatusTagType(session.status ?? '')" size="small">{{ getStatusLabel(session.status ?? '') }}</el-tag>

      <div class="top-bar-stats">
        <span>API: {{ session.echotikApiCalls ?? 0 }}</span>
        <el-divider direction="vertical" />
        <span>Token: {{ session.llmTotalTokens ?? 0 }}</span>
      </div>

      <!-- 步骤进度点 -->
      <div v-if="steps.length > 0" class="top-bar-steps">
        <div
          v-for="(step, idx) in steps"
          :key="idx"
          class="step-dot-top"
          :class="{
            done:    step.status === 'completed',
            running: step.status === 'running',
            failed:  step.status === 'failed',
            paused:  step.status === 'paused',
          }"
          :title="`${step.blockId}: ${getStatusLabel(step.status)}`"
        />
      </div>

      <!-- 操作按钮 -->
      <div class="top-bar-actions">
        <el-button size="small" type="primary" plain @click="handleExport">导出 Excel</el-button>
        <el-button size="small" type="success" plain @click="savePlanVisible = true">保存方案</el-button>
      </div>
    </div>

    <!-- 方案介绍卡片 -->
    <el-card v-if="session.blockChain?.length" class="plan-intro-card">
      <template #header>
        <div class="card-header">
          <span>方案介绍</span>
          <el-button text @click="planIntroExpanded = !planIntroExpanded">
            {{ planIntroExpanded ? '收起' : '展开' }}
          </el-button>
        </div>
      </template>

      <el-collapse-transition>
        <div v-show="planIntroExpanded" class="plan-intro-content">
          <!-- 方案解读（折叠，默认收起） -->
          <div v-if="planInterpretation" class="intro-section">
            <el-collapse v-model="interpretationActive">
              <el-collapse-item title="方案解读" name="interp">
                <div class="plan-interpretation" v-html="renderMd(planInterpretation)" />
              </el-collapse-item>
            </el-collapse>
          </div>

          <!-- 执行步骤 -->
          <div class="intro-section">
            <h4>执行步骤（{{ mergedSteps.length }} 个）</h4>
            <div class="step-timeline">
              <div
                v-for="({ block, step }, idx) in mergedSteps"
                :key="idx"
                class="step-row"
              >
                <!-- 左侧：状态图标 + 竖线 -->
                <div class="step-icon-col">
                  <el-icon class="step-status-icon" :class="STEP_STATUS_ICON[step?.status ?? 'pending'].cls">
                    <component :is="STEP_STATUS_ICON[step?.status ?? 'pending'].icon" />
                  </el-icon>
                  <div v-if="idx < mergedSteps.length - 1" class="step-connector" />
                </div>
                <!-- 右侧：内容 -->
                <div class="step-content">
                  <div class="step-header">
                    <span class="step-num">{{ idx + 1 }}</span>
                    <span class="step-label">{{ block.label || block.type }}</span>
                    <el-tag size="small" type="info" effect="plain" class="step-type-tag">
                      {{ formatBlockType(block.type || '') }}
                    </el-tag>
                  </div>
                  <div v-if="step?.status === 'completed'" class="step-meta">
                    <span>输出 {{ step.outputCount.toLocaleString() }} 条</span>
                    <el-divider direction="vertical" />
                    <span>{{ formatDuration(step.durationMs) }}</span>
                  </div>
                  <div v-if="step?.status === 'failed' && step.errorMessage" class="step-error">
                    {{ step.errorMessage }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-collapse-transition>
    </el-card>

    <!-- 数据表格（全宽） -->
    <div class="data-container">
      <div class="data-toolbar">
        <span v-if="tableData.length > 0" class="data-count">
          共 <strong>{{ currentView?.totalCount ?? tableData.length }}</strong> 条结果
        </span>
        <span v-else class="data-count" />
        <el-button :icon="Refresh" size="small" circle @click="refreshData" />
      </div>

      <div class="table-wrap">
        <el-table
          v-if="tableData.length > 0"
          :data="tableData"
          stripe
          border
          height="100%"
          style="width: 100%"
        >
          <el-table-column type="index" width="50" fixed="left" />
          <el-table-column
            v-for="col in tableCols"
            :key="col.id"
            :prop="col.id"
            :label="col.label"
            :min-width="col.type === 'number' || col.type === 'score' ? 100 : 140"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span :class="cellClass(col, row[col.id])">
                {{ formatCell(col, row[col.id]) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无数据" :image-size="120" />
      </div>
    </div>

    <!-- 保存方案弹窗 -->
    <el-dialog v-model="savePlanVisible" title="保存为方案" width="480px">
      <el-form :model="planForm" label-width="80px">
        <el-form-item label="方案名称" required>
          <el-input v-model="planForm.name" placeholder="给方案起个名字" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="planForm.description" type="textarea" :rows="3" placeholder="可选" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="savePlanVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!planForm.name.trim()"
          @click="confirmSavePlan"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.plan-intro-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.plan-intro-content {
  padding-top: 12px;
}

.intro-section {
  margin-bottom: 24px;
}

.intro-section:last-child {
  margin-bottom: 0;
}

.intro-section h4 {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.source-text {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  line-height: 1.6;
  color: #606266;
  margin: 0;
}

.source-link {
  margin-left: 12px;
  color: #606266;
  font-size: 13px;
}

.step-timeline { display: flex; flex-direction: column; }

.step-row { display: flex; gap: 12px; }
.step-row:last-child .step-content { padding-bottom: 0; }

.step-icon-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 24px;
  flex-shrink: 0;
}
.step-status-icon { font-size: 18px; }
.step-connector { width: 2px; flex: 1; min-height: 10px; background: #e4e7ed; margin: 2px 0; }

.step-content { flex: 1; padding-bottom: 12px; }
.step-header { display: flex; align-items: center; gap: 8px; min-height: 24px; }
.step-num { font-size: 12px; color: #c0c4cc; min-width: 16px; }
.step-label { font-size: 13px; font-weight: 500; color: #303133; }
.step-type-tag { flex-shrink: 0; }

.step-meta { font-size: 12px; color: #909399; margin-top: 3px; display: flex; align-items: center; gap: 4px; }
.step-error { font-size: 12px; color: #f56c6c; margin-top: 3px; line-height: 1.5; }

.step-pending { color: #c0c4cc; }
.step-running { color: #409eff; animation: pulse 1s ease-in-out infinite; }
.step-done    { color: #67c23a; }
.step-failed  { color: #f56c6c; }
.step-skipped { color: #c0c4cc; }
.step-paused  { color: #e6a23c; }

.session-detail {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 0 12px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.session-title-text {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-bar-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.top-bar-steps {
  display: flex;
  align-items: center;
  gap: 3px;
}

.top-bar-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.step-dot-top {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #dcdfe6;
  transition: background 0.3s;
}
.step-dot-top.done    { background: #67c23a; }
.step-dot-top.running { background: #409eff; animation: pulse 1s ease-in-out infinite; }
.step-dot-top.failed  { background: #f56c6c; }
.step-dot-top.paused  { background: #e6a23c; }

.data-container {
  flex: 1;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.data-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.data-count {
  font-size: 13px;
  color: #606266;
}

.table-wrap {
  flex: 1;
  overflow: hidden;
}

.score-high { color: #67c23a; font-weight: 600; }
.score-mid  { color: #e6a23c; font-weight: 600; }
.score-low  { color: #f56c6c; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.plan-interpretation { font-size: 14px; line-height: 1.75; color: #1f2937; }
.plan-interpretation :deep(h2) { font-size: 15px; font-weight: 700; color: #1d4ed8; margin: 16px 0 8px; padding-bottom: 4px; border-bottom: 1px solid #e0e7ff; }
.plan-interpretation :deep(h2:first-child) { margin-top: 0; }
.plan-interpretation :deep(h3) { font-size: 14px; font-weight: 600; color: #374151; margin: 14px 0 6px; }
.plan-interpretation :deep(p) { margin: 6px 0; color: #374151; }
.plan-interpretation :deep(ul), .plan-interpretation :deep(ol) { padding-left: 20px; margin: 6px 0; }
.plan-interpretation :deep(li) { margin: 4px 0; color: #374151; }
.plan-interpretation :deep(strong) { color: #111827; font-weight: 600; }
.plan-interpretation :deep(blockquote) { margin: 12px 0; padding: 8px 14px; border-left: 3px solid #6366f1; background: #f5f3ff; border-radius: 0 6px 6px 0; color: #4b5563; font-size: 13px; }
.plan-interpretation :deep(code) { background: #f3f4f6; padding: 1px 5px; border-radius: 3px; font-size: 12px; color: #4338ca; }
</style>
