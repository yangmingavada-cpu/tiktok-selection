<script setup lang="ts">
/**
 * 用户列对话框（双模式）
 * - create：新增 string / tag 列
 * - edit：编辑已存在 tag 列的 options（也可改 label），type 不可改
 */
import { computed, reactive, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus'

import type {
  ExtraColCreateRequest,
  ExtraColUpdateRequest,
  UserExtraCol,
  UserExtraColType,
} from '@/types'

const props = defineProps<{
  visible: boolean
  loading?: boolean
  /** create（默认）或 edit */
  mode?: 'create' | 'edit'
  /** edit 模式下的初始值 */
  initial?: UserExtraCol | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  confirm: [req: ExtraColCreateRequest]
  confirmEdit: [colId: string, req: ExtraColUpdateRequest]
}>()

interface FormState {
  label: string
  type: UserExtraColType
  options: string[]
}

const form = reactive<FormState>({
  label: '',
  type: 'string',
  options: [],
})

const isEdit = computed(() => props.mode === 'edit')

const title = computed(() => (isEdit.value ? '编辑列' : '新增列'))

watch(
  () => props.visible,
  (v) => {
    if (!v) return
    if (isEdit.value && props.initial) {
      form.label = props.initial.label
      form.type = props.initial.type
      form.options = props.initial.options ? [...props.initial.options] : []
    } else {
      form.label = ''
      form.type = 'string'
      form.options = []
    }
  },
)

function handleConfirm() {
  if (!form.label.trim()) {
    ElMessage.warning('请输入列名')
    return
  }
  if (form.type === 'tag' && form.options.length === 0) {
    ElMessage.warning('标签类型至少需要一个可选项')
    return
  }

  if (isEdit.value) {
    if (!props.initial) return
    const req: ExtraColUpdateRequest = {
      label: form.label.trim(),
    }
    // tag 类型才传 options
    if (form.type === 'tag') {
      req.options = [...form.options]
    }
    emit('confirmEdit', props.initial.id, req)
    return
  }

  const req: ExtraColCreateRequest = {
    label: form.label.trim(),
    type: form.type,
  }
  if (form.type === 'tag') {
    req.options = [...form.options]
  }
  emit('confirm', req)
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="title"
    width="440px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <ElForm :model="form" label-width="80px">
      <ElFormItem label="列名" required>
        <ElInput v-model="form.label" maxlength="50" show-word-limit placeholder="例如：我的备注" />
      </ElFormItem>

      <ElFormItem label="列类型" required>
        <ElRadioGroup v-model="form.type" :disabled="isEdit">
          <ElRadio value="string">文本</ElRadio>
          <ElRadio value="tag">标签（单选）</ElRadio>
        </ElRadioGroup>
      </ElFormItem>

      <ElFormItem v-if="form.type === 'tag'" label="可选项" required>
        <ElSelect
          v-model="form.options"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="输入选项后按回车添加"
          style="width: 100%"
        >
          <ElOption v-for="opt in form.options" :key="opt" :label="opt" :value="opt" />
        </ElSelect>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="handleConfirm">
        {{ isEdit ? '保存' : '确认' }}
      </ElButton>
    </template>
  </ElDialog>
</template>
