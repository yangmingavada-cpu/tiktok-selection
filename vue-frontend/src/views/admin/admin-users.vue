<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索邮箱 / 用户名"
        clearable
        style="width: 240px"
        @keyup.enter="loadData"
      />
      <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px; margin-left: 8px">
        <el-option label="正常" value="active" />
        <el-option label="禁用" value="disabled" />
      </el-select>
      <el-select v-model="filterRole" placeholder="角色" clearable style="width: 120px; margin-left: 8px">
        <el-option label="普通用户" value="user" />
        <el-option label="管理员" value="admin" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px" @click="loadData">查询</el-button>
    </div>

    <el-table :data="users" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="220" show-overflow-tooltip />
      <el-table-column prop="name" label="用户名" width="140" />
      <el-table-column prop="email" label="邮箱" show-overflow-tooltip />
      <el-table-column prop="role" label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tierDisplayName" label="等级" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'warning'" size="small">
            {{ row.status === 'active' ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="160">
        <template #default="{ row }">{{ row.createdAt?.slice(0, 16) ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="编辑用户" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名">
          <el-input :value="form.name" disabled />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input :value="form.email" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role">
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="正常" value="active" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="form.tierId" placeholder="选择等级">
            <el-option v-for="t in tiers" :key="t.id" :label="t.displayName" :value="t.id" />
          </el-select>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listAdminUsers, updateAdminUser, listTiers } from '@/api/admin'
import type { Tier, UserProfile } from '@/types'
import { PAGE_SIZE } from '@/constants'

const loading = ref(false)
const saving = ref(false)
const users = ref<UserProfile[]>([])
const tiers = ref<Tier[]>([])
const dialogVisible = ref(false)
const editId = ref('')
const pageNum = ref(1)
const pageSize = ref(PAGE_SIZE.DEFAULT)
const total = ref(0)
const searchKeyword = ref('')
const filterStatus = ref('')
const filterRole = ref('')

const form = reactive({
  name: '', email: '', role: 'user', status: 'active', tierId: '',
})

async function loadData() {
  loading.value = true
  try {
    const res = await listAdminUsers({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: filterStatus.value || undefined,
      role: filterRole.value || undefined,
      keyword: searchKeyword.value || undefined,
    })
    const page = res.data
    users.value = page.records ?? []
    total.value = page.total ?? users.value.length
  } finally {
    loading.value = false
  }
}

function openEdit(row: UserProfile & { tierId?: string }) {
  editId.value = row.id
  Object.assign(form, {
    name: row.name, email: row.email, role: row.role,
    status: row.status, tierId: row.tierId ?? '',
  })
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    await updateAdminUser(editId.value, { status: form.status, tierId: form.tierId, role: form.role })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const res = await listTiers()
  tiers.value = res.data
  await loadData()
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
