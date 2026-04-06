<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openCreate">新建等级</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="name" label="标识" width="120" />
      <el-table-column prop="displayName" label="显示名称" width="120" />
      <el-table-column prop="priceMonthly" label="月价格(USD)" width="120">
        <template #default="{ row }">{{ row.priceMonthly ?? '免费' }}</template>
      </el-table-column>
      <el-table-column prop="monthlyApiQuota" label="月API配额" width="110">
        <template #default="{ row }">{{ row.monthlyApiQuota ?? '∞' }}</template>
      </el-table-column>
      <el-table-column prop="maxConcurrentSessions" label="并发会话" width="90" />
      <el-table-column prop="maxProductsPerQuery" label="最大商品数" width="100" />
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.active ? 'success' : 'info'" size="small">
            {{ row.active ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该等级？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button text size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑等级' : '新建等级'" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="等级标识" prop="name">
          <el-input v-model="form.name" :disabled="isEdit" placeholder="如 free / pro / enterprise" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" placeholder="如 免费版 / 专业版" />
        </el-form-item>
        <el-form-item label="月价格(USD)">
          <el-input-number v-model="form.priceMonthly" :precision="2" :min="0" />
        </el-form-item>
        <el-form-item label="月API配额">
          <el-input-number v-model="form.monthlyApiQuota" :min="0" placeholder="留空=不限制" />
        </el-form-item>
        <el-form-item label="月Token配额">
          <el-input-number v-model="form.monthlyTokenQuota" :min="0" placeholder="留空=不限制" />
        </el-form-item>
        <el-form-item label="最大并发会话">
          <el-input-number v-model="form.maxConcurrentSessions" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="最大商品数/次">
          <el-input-number v-model="form.maxProductsPerQuery" :min="1" />
        </el-form-item>
        <el-form-item label="最大保存方案">
          <el-input-number v-model="form.maxSavedPlans" :min="0" />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.active" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { listTiers, createTier, updateTier, deleteTier } from '@/api/admin'
import { useCrudTable } from '@/composables/useCrudTable'
import type { Tier } from '@/types'

const DEFAULT_FORM = () => ({
  name: '',
  displayName: '',
  priceMonthly: 0,
  monthlyApiQuota: undefined as number | undefined,
  monthlyTokenQuota: undefined as number | undefined,
  maxConcurrentSessions: 3,
  maxApiPerSession: 50,
  maxTokenPerSession: 100000,
  maxProductsPerQuery: 200,
  maxSavedPlans: 10,
  sortOrder: 0,
  active: true,
})

const {
  loading, saving, list, dialogVisible, isEdit, formRef, form,
  loadData, openCreate, openEdit, handleSave, handleDelete,
} = useCrudTable<Tier, ReturnType<typeof DEFAULT_FORM>>({
  listApi: () => listTiers(),
  createApi: (data) => createTier(data),
  updateApi: (id, data) => updateTier(id, data),
  deleteApi: (id) => deleteTier(id),
  defaultForm: DEFAULT_FORM,
})

const rules = {
  name: [{ required: true, message: '请填写等级标识' }],
  displayName: [{ required: true, message: '请填写显示名称' }],
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
