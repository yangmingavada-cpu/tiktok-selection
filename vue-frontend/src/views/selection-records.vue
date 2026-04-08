<template>
  <div class="sessions-page">
    <div class="page-header">
      <h2>选品记录</h2>
      <div class="header-actions">
        <el-button type="primary" plain @click="intentDialogVisible = true">
          <el-icon><MagicStick /></el-icon>
          AI新建
        </el-button>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          新建选品
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="sessions"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="title" label="标题" min-width="200">
          <template #default="{ row }">
            <div class="title-cell">
              <el-input
                v-if="editingId === row.id"
                v-model="editingTitle"
                size="small"
                @blur="handleTitleBlur(row)"
                @keyup.enter="handleTitleSave(row)"
                @keyup.esc="editingId = null"
              />
              <div v-else @dblclick="handleTitleEdit(row)" class="title-display">
                {{ row.title }}
                <el-icon class="edit-icon"><Edit /></el-icon>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="getSourceTagType(row.sourceType)" size="small">
              {{ formatSourceType(row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="echotikApiCalls" label="API调用" width="100" align="center">
          <template #default="{ row }">
            {{ row.echotikApiCalls ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            {{ row.createTime?.slice(0, 16).replace('T', ' ') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">
              查看
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchSessions"
          @current-change="fetchSessions"
        />
      </div>
    </el-card>

    <!-- AI意图解析对话框 -->
    <el-dialog
      v-model="intentDialogVisible"
      title="AI智能新建选品"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetIntentDialog"
    >
      <!-- Step 1: 输入意图 -->
      <template v-if="intentStep === 'input'">
        <p class="intent-hint">用自然语言描述你的选品需求，AI将自动生成积木链。</p>
        <el-input
          v-model="intentText"
          type="textarea"
          :rows="4"
          placeholder="例如：帮我找美区销量增长最快的美妆产品，按增长率排序取前50个，并标记利润率高的商品"
          maxlength="2000"
          show-word-limit
        />
      </template>

      <!-- Step 2: 解析中 -->
      <template v-else-if="intentStep === 'parsing'">
        <div class="parsing-state">
          <el-icon class="is-loading parsing-icon"><Loading /></el-icon>
          <p>AI正在解析意图，构建积木链…</p>
          <p class="parsing-sub">已迭代 {{ intentIterations }} 次</p>
        </div>
      </template>

      <!-- Step 3: 预览积木链 -->
      <template v-else-if="intentStep === 'preview'">
        <div class="preview-header">
          <el-tag type="success" size="small">解析成功</el-tag>
          <span class="preview-meta">共 {{ intentBlockChain.length }} 个积木块 · 消耗 {{ intentTokens }} tokens</span>
        </div>
        <p v-if="intentSummary" class="intent-summary">{{ intentSummary }}</p>
        <el-scrollbar max-height="300px">
          <div class="block-chain-preview">
            <div
              v-for="(block, index) in intentBlockChain"
              :key="index"
              class="block-item"
            >
              <span class="block-seq">{{ index + 1 }}</span>
              <span class="block-id">{{ block.blockId }}</span>
              <span class="block-desc">{{ blockDesc(block) }}</span>
            </div>
          </div>
        </el-scrollbar>
      </template>

      <!-- Step 4: 失败 -->
      <template v-else-if="intentStep === 'error'">
        <el-result
          icon="error"
          title="解析失败"
          :sub-title="intentError"
        />
      </template>

      <template #footer>
        <el-button @click="intentDialogVisible = false">取消</el-button>
        <template v-if="intentStep === 'input'">
          <el-button
            type="primary"
            :disabled="!intentText.trim()"
            @click="handleParseIntent"
          >
            开始解析
          </el-button>
        </template>
        <template v-else-if="intentStep === 'preview'">
          <el-button @click="intentStep = 'input'">重新输入</el-button>
          <el-button type="primary" @click="handleConfirmIntent">
            确认创建
          </el-button>
        </template>
        <template v-else-if="intentStep === 'error'">
          <el-button type="primary" @click="intentStep = 'input'">重新输入</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, MagicStick, Loading, Edit } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { listSessions, createSession, removeSession, updateSession } from '@/api/session'
import { parseIntent } from '@/api/intent'
import { getStatusLabel, getStatusTagType, PAGE_SIZE } from '@/constants'
import type { Session, Block } from '@/types'

const router = useRouter()

const loading = shallowRef(false)
const sessions = ref<Session[]>([])
const pageNum = shallowRef(1)
const pageSize = shallowRef(PAGE_SIZE.DEFAULT)
const total = shallowRef(0)

// AI意图对话框状态
const intentDialogVisible = shallowRef(false)
const intentStep = shallowRef<'input' | 'parsing' | 'preview' | 'error'>('input')
const intentText = shallowRef('')
const intentBlockChain = ref<Block[]>([])
const intentSummary = shallowRef('')
const intentTokens = shallowRef(0)
const intentIterations = shallowRef(0)
const intentError = shallowRef('')

function blockDesc(block: Block): string {
  const cfg = block.config || {}
  if (cfg.source_type) return `来源: ${cfg.source_type}`
  if (cfg.field && cfg.operator) return `${cfg.field} ${cfg.operator} ${cfg.value ?? ''}`
  if (cfg.sort_by) return `按 ${cfg.sort_by} 排序, top ${cfg.top_n ?? ''}`
  if (cfg.score_type) return `${cfg.score_type} 评分`
  if (cfg.traverse_type) return `关联: ${cfg.traverse_type}`
  return JSON.stringify(cfg).slice(0, 60)
}

async function fetchSessions() {
  loading.value = true
  try {
    const res = await listSessions({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    sessions.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  router.push({ name: 'SessionDetail', params: { id: 'new' } })
}

function handleView(row: Session) {
  router.push({ name: 'SessionDetail', params: { id: row.id } })
}

async function handleDelete(row: Session) {
  try {
    await ElMessageBox.confirm('确定删除该选品记录吗？', '提示', { type: 'warning' })
    await removeSession(row.id)
    ElMessage.success('删除成功')
    fetchSessions()
  } catch {
    // cancelled
  }
}

const editingId = ref<string | null>(null)
const editingTitle = ref('')

function handleTitleEdit(row: Session) {
  editingId.value = row.id
  editingTitle.value = row.title
}

async function handleTitleSave(row: Session) {
  if (editingTitle.value.trim() === row.title) {
    editingId.value = null
    return
  }

  try {
    await updateSession(row.id, { title: editingTitle.value.trim() })
    row.title = editingTitle.value.trim()
    ElMessage.success('修改成功')
  } catch (error) {
    ElMessage.error('修改失败')
  } finally {
    editingId.value = null
  }
}

function handleTitleBlur(row: Session) {
  // 延迟执行，让 Enter 事件先触发
  setTimeout(() => {
    if (editingId.value === row.id) {
      handleTitleSave(row)
    }
  }, 200)
}

function getSourceTagType(sourceType: string): string {
  const typeMap: Record<string, string> = {
    'user_plan': 'success',
    'preset': 'warning',
    'ai_conversation': 'primary',
    '': 'info'
  }
  return typeMap[sourceType] || 'info'
}

function formatSourceType(sourceType: string): string {
  if (!sourceType) return 'AI对话'
  const typeMap: Record<string, string> = {
    'user_plan': '方案库',
    'preset': '预设模板',
    'ai_conversation': 'AI对话',
  }
  return typeMap[sourceType] || sourceType
}

async function handleParseIntent() {
  intentStep.value = 'parsing'
  intentIterations.value = 0
  try {
    const res = await parseIntent({ userText: intentText.value })
    const data = res.data
    intentTokens.value = data?.llmTokensUsed ?? 0
    intentIterations.value = data?.iterations ?? 0
    if (data?.success && data?.blockChain?.length) {
      intentBlockChain.value = data.blockChain
      intentSummary.value = data.summary || ''
      intentStep.value = 'preview'
    } else {
      intentError.value = data?.message || '积木链构建失败，请重新描述需求'
      intentStep.value = 'error'
    }
  } catch (e: unknown) {
    intentError.value = e instanceof Error ? e.message : '请求失败，请检查网络'
    intentStep.value = 'error'
  }
}

async function handleConfirmIntent() {
  try {
    const res = await createSession({
      blockChain: intentBlockChain.value,
      sourceText: intentText.value,
    })
    const sessionId = res.data?.id
    intentDialogVisible.value = false
    ElMessage.success('选品任务已创建')
    if (sessionId) {
      router.push({ name: 'SessionDetail', params: { id: sessionId } })
    } else {
      fetchSessions()
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  }
}

function resetIntentDialog() {
  intentStep.value = 'input'
  intentText.value = ''
  intentBlockChain.value = []
  intentSummary.value = ''
  intentTokens.value = 0
  intentIterations.value = 0
  intentError.value = ''
}

onMounted(fetchSessions)
</script>

<style scoped>
.title-cell {
  cursor: pointer;
}

.title-display {
  display: flex;
  align-items: center;
  gap: 6px;
}

.title-display:hover .edit-icon {
  opacity: 1;
}

.edit-icon {
  opacity: 0;
  transition: opacity 0.2s;
  color: #909399;
  font-size: 14px;
}

.sessions-page {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.intent-hint {
  margin: 0 0 12px;
  color: #606266;
  font-size: 13px;
}

.parsing-state {
  text-align: center;
  padding: 32px 0;
  color: #606266;
}

.parsing-icon {
  font-size: 40px;
  color: #409eff;
  margin-bottom: 12px;
}

.parsing-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.preview-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.preview-meta {
  font-size: 12px;
  color: #909399;
}

.intent-summary {
  font-size: 13px;
  color: #606266;
  margin: 0 0 12px;
  padding: 8px 12px;
  background: #f4f4f5;
  border-radius: 4px;
}

.block-chain-preview {
  padding: 4px 0;
}

.block-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 13px;
}

.block-item:hover {
  background: #f5f7fa;
}

.block-seq {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #e1effe;
  color: #1e40af;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.block-id {
  font-family: monospace;
  font-weight: 600;
  color: #303133;
  min-width: 60px;
}

.block-desc {
  color: #606266;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
