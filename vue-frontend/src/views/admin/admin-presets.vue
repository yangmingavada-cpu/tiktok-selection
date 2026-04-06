<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openCreate">新建套餐</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="pkgCode" label="代码" width="160" />
      <el-table-column prop="nameZh" label="中文名" width="160" />
      <el-table-column prop="nameEn" label="英文名" width="160" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="积木块数" width="90">
        <template #default="{ row }">{{ row.blockChain?.length ?? 0 }}</template>
      </el-table-column>
      <el-table-column prop="useCount" label="使用次数" width="90" />
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
          <el-button text size="small" @click="handleOpenEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该套餐？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button text size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑套餐' : '新建套餐'" width="680px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="套餐代码" prop="pkgCode">
          <el-input v-model="form.pkgCode" :disabled="isEdit" placeholder="如 HOT_PRODUCT_TH" />
        </el-form-item>
        <el-form-item label="中文名称" prop="nameZh">
          <el-input v-model="form.nameZh" />
        </el-form-item>
        <el-form-item label="英文名称" prop="nameEn">
          <el-input v-model="form.nameEn" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="积木链(JSON)" prop="blockChain">
          <el-input
            v-model="blockChainText"
            type="textarea"
            :rows="8"
            placeholder='[{"blockId":"DS01","seq":1,"config":{...}}]'
            @blur="parseBlockChain"
          />
          <div v-if="blockChainError" class="json-error">{{ blockChainError }}</div>
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
        <el-button type="primary" :loading="saving" @click="handleSavePreset">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { listPresets, createPreset, updatePreset, deletePreset } from '@/api/admin'
import { useCrudTable } from '@/composables/useCrudTable'
import type { PresetPackage, PresetPackageForm } from '@/types'

const DEFAULT_FORM = (): PresetPackageForm => ({
  pkgCode: '', nameZh: '', nameEn: '', description: '',
  blockChain: [], tags: [], sortOrder: 0, active: true,
})

const blockChainText = ref('[]')
const blockChainError = ref('')

const {
  loading, saving, list, dialogVisible, isEdit, formRef, form,
  loadData, openCreate: _openCreate, openEdit, handleSave, handleDelete,
} = useCrudTable<PresetPackage, PresetPackageForm>({
  listApi: () => listPresets(),
  createApi: (data) => createPreset(data),
  updateApi: (id, data) => updatePreset(id, data),
  deleteApi: (id) => deletePreset(id),
  defaultForm: DEFAULT_FORM,
})

function openCreate() {
  _openCreate()
  blockChainText.value = '[]'
  blockChainError.value = ''
}

function handleOpenEdit(row: PresetPackage) {
  openEdit(row)
  blockChainText.value = JSON.stringify(row.blockChain ?? [], null, 2)
  blockChainError.value = ''
}

function parseBlockChain() {
  try {
    form.blockChain = JSON.parse(blockChainText.value)
    blockChainError.value = ''
  } catch {
    blockChainError.value = 'JSON 格式错误，请检查'
  }
}

async function handleSavePreset() {
  parseBlockChain()
  if (blockChainError.value) return
  await handleSave()
}

const rules = {
  pkgCode: [{ required: true, message: '请填写套餐代码' }],
  nameZh: [{ required: true, message: '请填写中文名称' }],
  nameEn: [{ required: true, message: '请填写英文名称' }],
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.json-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}
</style>
