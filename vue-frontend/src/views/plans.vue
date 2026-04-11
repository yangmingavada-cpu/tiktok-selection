<template>
  <div class="plans-page">
    <div class="page-header">
      <h2>方案库</h2>
      <el-button type="primary" @click="$router.push('/new')">
        <el-icon><Plus /></el-icon>
        新建选品
      </el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="plans" style="width: 100%">
        <el-table-column prop="name" label="方案名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="积木块数" width="90" align="center">
          <template #default="{ row }">{{ row.blockChain?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="useCount" label="执行次数" width="90" align="center">
          <template #default="{ row }">{{ row.useCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleExecute(row)">执行</el-button>
            <el-button type="warning" link size="small" @click="handleRename(row)">重命名</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchPlans"
        />
      </div>

      <el-empty v-if="!loading && plans.length === 0" description="暂无保存的方案，完成一次选品后可保存为方案" />
    </el-card>

    <!-- 执行前可视化编辑对话框 -->
    <PlanEditDialog
      v-model:visible="editDialogVisible"
      :plan="editingPlan"
      @executed="onPlanExecuted"
    />

    <!-- 重命名弹窗 -->
    <el-dialog v-model="renameVisible" title="修改方案信息" width="440px">
      <el-form :model="renameForm" label-width="80px">
        <el-form-item label="方案名称" required>
          <el-input v-model="renameForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="renameForm.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!renameForm.name.trim()" @click="handleRenameSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listPlans, updatePlan, deletePlan } from '@/api/plan'
import { PAGE_SIZE } from '@/constants'
import type { Plan } from '@/types'
import PlanEditDialog from './plans/plan-edit-dialog.vue'

const router = useRouter()
const loading = shallowRef(false)
const plans = ref<Plan[]>([])
const pageNum = shallowRef(1)
const pageSize = shallowRef(PAGE_SIZE.DEFAULT)
const total = shallowRef(0)

const renameVisible = shallowRef(false)
const saving = shallowRef(false)
const renameForm = reactive({ id: '', name: '', description: '' })

// 执行前可视化编辑对话框
const editDialogVisible = ref(false)
const editingPlan = ref<Plan | null>(null)

async function fetchPlans() {
  loading.value = true
  try {
    const res = await listPlans({ pageNum: pageNum.value, pageSize: pageSize.value })
    plans.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

/**
 * 点"执行"不再直接创建 session，先弹可视化编辑对话框，
 * 让用户调参 + 审核通过后再 createSession
 */
function handleExecute(row: Plan) {
  editingPlan.value = row
  editDialogVisible.value = true
}

function onPlanExecuted(sessionId: string) {
  router.push({ name: 'SessionDetail', params: { id: sessionId } })
  // 后端 createSession 已经 +1 useCount，这里主动刷新一下列表展示最新次数
  fetchPlans()
}

function handleRename(row: Plan) {
  renameForm.id = row.id
  renameForm.name = row.name
  renameForm.description = row.description || ''
  renameVisible.value = true
}

async function handleRenameSave() {
  saving.value = true
  try {
    await updatePlan(renameForm.id, { name: renameForm.name, description: renameForm.description })
    ElMessage.success('已更新')
    renameVisible.value = false
    fetchPlans()
  } catch {
    ElMessage.error('更新失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Plan) {
  try {
    await ElMessageBox.confirm(`确定删除方案「${row.name}」？`, '提示', { type: 'warning' })
    await deletePlan(row.id)
    ElMessage.success('已删除')
    fetchPlans()
  } catch { /* cancelled */ }
}

onMounted(fetchPlans)
</script>

<style scoped>
.plans-page { padding: 0; }

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
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
