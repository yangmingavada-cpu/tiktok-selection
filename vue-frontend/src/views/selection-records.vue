<template>
  <div class="sessions-page">
    <div class="page-header">
      <h2>选品记录</h2>
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
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Edit } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { hideSessionFromRecords, listSessions, updateSession } from '@/api/session'
import { getStatusLabel, getStatusTagType, PAGE_SIZE } from '@/constants'
import type { Session } from '@/types'

const router = useRouter()

const loading = shallowRef(false)
const sessions = ref<Session[]>([])
const pageNum = shallowRef(1)
const pageSize = shallowRef(PAGE_SIZE.DEFAULT)
const total = shallowRef(0)

async function fetchSessions() {
  loading.value = true
  try {
    // context=records 让后端按 hidden_from_records 过滤，
    // 跟对话历史侧栏的 hidden_from_chat 完全独立
    const res = await listSessions({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      context: 'records',
    })
    sessions.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleView(row: Session) {
  router.push({ name: 'SessionDetail', params: { id: row.id } })
}

/**
 * 从选品记录页隐藏一条会话
 * 只设 hidden_from_records=true，对话历史侧栏不受影响
 */
async function handleDelete(row: Session) {
  try {
    await ElMessageBox.confirm(
      '确定从选品记录中移除该条？对话历史侧栏不会受影响。',
      '移除选品记录',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' },
    )
  } catch {
    return // 用户取消
  }
  try {
    await hideSessionFromRecords(row.id)
    ElMessage.success('已从选品记录中移除')
    fetchSessions()
  } catch (e) {
    console.warn('[selection-records] hide failed:', e)
    ElMessage.error('移除失败')
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

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
