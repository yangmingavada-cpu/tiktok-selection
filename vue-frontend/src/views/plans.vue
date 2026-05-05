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
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ===== 我的方案 ===== -->
        <el-tab-pane name="my" label="我的方案">
          <el-table v-loading="loading" :data="plans" style="width: 100%">
            <el-table-column prop="name" label="方案名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '-' }}</template>
            </el-table-column>
            <el-table-column label="来源" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.sourcePresetId" type="primary" size="small">源自官方</el-tag>
                <span v-else class="muted">自建</span>
              </template>
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

          <el-empty v-if="!loading && plans.length === 0" description="暂无保存的方案，可以从「官方方案库」一键添加，或完成一次选品后保存为方案" />
        </el-tab-pane>

        <!-- ===== 官方方案库 ===== -->
        <el-tab-pane name="official">
          <template #label>
            官方方案库
            <el-tag type="success" size="small" effect="plain" style="margin-left: 6px;">{{ presets.length }}</el-tag>
          </template>

          <el-table v-loading="loadingPresets" :data="presets" style="width: 100%">
            <el-table-column prop="nameZh" label="方案名称" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <strong>{{ row.nameZh }}</strong>
                <el-tag type="primary" size="small" effect="plain" style="margin-left: 8px;">官方</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '-' }}</template>
            </el-table-column>
            <el-table-column label="积木块数" width="90" align="center">
              <template #default="{ row }">{{ row.blockChain?.length ?? 0 }}</template>
            </el-table-column>
            <el-table-column prop="useCount" label="使用次数" width="90" align="center">
              <template #default="{ row }">{{ row.useCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" :loading="importingId === row.id" @click="handleImport(row)">
                  添加到我的方案
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!loadingPresets && presets.length === 0" description="暂无官方方案" />
        </el-tab-pane>
      </el-tabs>
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
import { listPlans, updatePlan, deletePlan, createPlanFromPreset } from '@/api/plan'
import { listPresetPackages } from '@/api/preset'
import { PAGE_SIZE } from '@/constants'
import type { Plan, PresetPackage } from '@/types'
import PlanEditDialog from './plans/plan-edit-dialog.vue'

const router = useRouter()
const activeTab = shallowRef<'my' | 'official'>('my')

// ===== 我的方案 =====
const loading = shallowRef(false)
const plans = ref<Plan[]>([])
const pageNum = shallowRef(1)
const pageSize = shallowRef(PAGE_SIZE.DEFAULT)
const total = shallowRef(0)

// ===== 官方方案库 =====
const loadingPresets = shallowRef(false)
const presets = ref<PresetPackage[]>([])
const importingId = shallowRef<string | null>(null)
let presetsLoaded = false

const renameVisible = shallowRef(false)
const saving = shallowRef(false)
const renameForm = reactive({ id: '', name: '', description: '' })

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

async function fetchPresets() {
  loadingPresets.value = true
  try {
    const res = await listPresetPackages()
    presets.value = res.data || []
    presetsLoaded = true
  } catch {
    ElMessage.error('加载官方方案失败')
  } finally {
    loadingPresets.value = false
  }
}

function onTabChange(name: string | number) {
  // 切到「官方方案库」时按需加载（首次访问才请求）
  if (name === 'official' && !presetsLoaded) {
    fetchPresets()
  }
}

async function handleImport(row: PresetPackage) {
  try {
    await ElMessageBox.confirm(
      `把官方方案「${row.nameZh}」添加到「我的方案」？添加后可在「我的方案」里编辑或执行。`,
      '添加到我的方案',
      { type: 'info', confirmButtonText: '添加', cancelButtonText: '取消' },
    )
  } catch { return /* cancelled */ }

  importingId.value = row.id
  try {
    await createPlanFromPreset(row.id)
    ElMessage.success(`已添加：${row.nameZh}`)
    activeTab.value = 'my'
    pageNum.value = 1
    await fetchPlans()
  } catch {
    ElMessage.error('添加失败，请稍后重试')
  } finally {
    importingId.value = null
  }
}

function handleExecute(row: Plan) {
  editingPlan.value = row
  editDialogVisible.value = true
}

function onPlanExecuted(sessionId: string) {
  router.push({ name: 'SessionDetail', params: { id: sessionId } })
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

.muted {
  color: #94a3b8;
  font-size: 12px;
}
</style>
