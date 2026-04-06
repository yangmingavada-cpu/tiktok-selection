<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增配置</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="provider" label="厂商" width="100" />
      <el-table-column prop="model" label="模型" width="180" />
      <el-table-column prop="apiKeyMasked" label="API Key" width="160" />
      <el-table-column prop="baseUrl" label="Base URL" show-overflow-tooltip />
      <el-table-column prop="maxTokens" label="最大Token" width="100" />
      <el-table-column prop="priority" label="优先级" width="80" />
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
          <el-popconfirm title="确认删除该LLM配置？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button text size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑LLM配置' : '新增LLM配置'" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 GPT-4o-主" />
        </el-form-item>
        <el-form-item label="厂商" prop="provider">
          <el-select v-model="form.provider" placeholder="选择厂商">
            <el-option label="OpenAI" value="openai" />
            <el-option label="Anthropic" value="anthropic" />
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="OpenRouter" value="openrouter" />
            <el-option label="硅基流动" value="siliconflow" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" :placeholder="isEdit ? '留空则保留原值' : ''" show-password />
        </el-form-item>
        <el-form-item label="模型" prop="model">
          <el-input v-model="form.model" placeholder="如 gpt-4o" />
        </el-form-item>
        <el-form-item label="最大Token">
          <el-input-number v-model="form.maxTokens" :min="1" :max="128000" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" />
          <span class="form-tip">数值越小优先级越高</span>
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
import { listLlmConfigs, createLlmConfig, updateLlmConfig, deleteLlmConfig } from '@/api/admin'
import { useCrudTable } from '@/composables/useCrudTable'
import type { LlmConfig, LlmConfigForm } from '@/types'

const DEFAULT_FORM = (): LlmConfigForm => ({
  name: '', provider: 'openai', baseUrl: '', apiKey: '',
  model: '', maxTokens: 4096, priority: 1, active: true,
})

const {
  loading, saving, list, dialogVisible, isEdit, formRef, form,
  loadData, openCreate, openEdit, handleSave, handleDelete,
} = useCrudTable<LlmConfig, LlmConfigForm>({
  listApi: () => listLlmConfigs(),
  createApi: (data) => createLlmConfig(data),
  updateApi: (id, data) => updateLlmConfig(id, data),
  deleteApi: (id) => deleteLlmConfig(id),
  defaultForm: DEFAULT_FORM,
  rowToForm: (row) => ({ ...DEFAULT_FORM(), ...row, apiKey: '' }),
})

function handleOpenEdit(row: LlmConfig) {
  openEdit(row)
}

const rules = {
  name: [{ required: true, message: '请填写名称' }],
  provider: [{ required: true, message: '请选择厂商' }],
  baseUrl: [{ required: true, message: '请填写 Base URL' }],
  model: [{ required: true, message: '请填写模型名称' }],
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
