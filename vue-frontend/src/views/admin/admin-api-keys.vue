<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openCreate">添加密钥</el-button>
      <el-alert
        v-if="lowQuotaCount > 0"
        :title="`${lowQuotaCount} 个密钥余量低于告警阈值，请及时补充`"
        type="warning"
        show-icon
        :closable="false"
        style="display: inline-flex; margin-left: 16px; padding: 4px 12px;"
      />
    </div>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="apiKeyMasked" label="API Key" width="160" />
      <el-table-column prop="apiSecretMasked" label="API Secret" width="160" />
      <el-table-column label="剩余/总量" width="130">
        <template #default="{ row }">
          {{ row.remainingCalls ?? '∞' }} / {{ row.totalCalls ?? '∞' }}
        </template>
      </el-table-column>
      <el-table-column label="余量状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.belowThreshold" type="danger" size="small">低余量</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.active" @change="handleToggle(row.id)" />
        </template>
      </el-table-column>
      <el-table-column prop="lastUsedTime" label="最后使用" width="160">
        <template #default="{ row }">{{ row.lastUsedTime?.slice(0, 16) ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该密钥？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button text size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑密钥' : '添加密钥'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 主账号-Key1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" :placeholder="isEdit ? '留空则保留原值' : ''" show-password />
        </el-form-item>
        <el-form-item label="API Secret">
          <el-input v-model="form.apiSecret" :placeholder="isEdit ? '留空则保留原值' : ''" show-password />
        </el-form-item>
        <el-form-item label="总调用配额">
          <el-input-number v-model="form.totalCalls" :min="0" placeholder="留空=不限制" />
        </el-form-item>
        <el-form-item label="剩余次数">
          <el-input-number v-model="form.remainingCalls" :min="0" />
        </el-form-item>
        <el-form-item label="告警阈值">
          <el-input-number v-model="form.alertThreshold" :min="0" />
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
import { computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { listApiKeys, createApiKey, updateApiKey, deleteApiKey, toggleApiKey } from '@/api/admin'
import { useCrudTable } from '@/composables/useCrudTable'
import type { EchotikApiKey, EchotikApiKeyForm } from '@/types'

const DEFAULT_FORM = (): EchotikApiKeyForm => ({
  name: '', apiKey: '', apiSecret: '',
  totalCalls: undefined, remainingCalls: undefined,
  alertThreshold: 100, active: true,
})

const {
  loading, saving, list, dialogVisible, isEdit, formRef, form,
  loadData, openCreate, openEdit: _openEdit, handleSave, handleDelete,
} = useCrudTable<EchotikApiKey, EchotikApiKeyForm>({
  listApi: () => listApiKeys(),
  createApi: (data) => createApiKey(data),
  updateApi: (id, data) => updateApiKey(id, data),
  deleteApi: (id) => deleteApiKey(id),
  defaultForm: DEFAULT_FORM,
  rowToForm: (row) => ({ ...DEFAULT_FORM(), ...row, apiKey: '', apiSecret: '' }),
})

function openEdit(row: EchotikApiKey) {
  _openEdit(row)
}

const rules = {
  name: [{ required: true, message: '请填写名称' }],
}

const lowQuotaCount = computed(() => list.value.filter(k => k.belowThreshold).length)

async function handleToggle(id: string) {
  await toggleApiKey(id)
  await loadData()
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}
</style>
