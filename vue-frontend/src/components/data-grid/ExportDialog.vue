<script setup lang="ts">
/**
 * 导出对话框
 * 选择文件格式 + 导出范围（当前视图 / 仅选中行 / 全部行）
 *
 * 调用方传入当前视图状态（搜索/排序/可见列/选中行），点击确认时拼装 query params 调用导出 api。
 */
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
} from 'element-plus'

import type { DataGridColumn } from '@/types'
import type { SortState } from './helpers/use-grid-filter-sort'

export interface ExportConfirmPayload {
  format: 'xlsx' | 'csv'
  fields?: string
  order?: string
  search?: string
  rowIndices?: string
  renames?: string
}

const props = defineProps<{
  visible: boolean
  totalRows: number
  visibleRows: number
  selectedRowIndices: number[]
  visibleCols: readonly DataGridColumn[]
  /** 所有列（含隐藏） */
  allCols: readonly DataGridColumn[]
  searchValue: string
  sortState: SortState | null
  /** 列重命名映射（前端 localStorage 持久化的） */
  colRenames: Record<string, string>
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  confirm: [payload: ExportConfirmPayload]
}>()

type ExportScope = 'view' | 'selected' | 'all'

const format = ref<'xlsx' | 'csv'>('xlsx')
const scope = ref<ExportScope>('view')

const selectedCount = computed(() => props.selectedRowIndices.length)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      format.value = 'xlsx'
      scope.value = selectedCount.value > 0 ? 'selected' : 'view'
    }
  },
)

function handleConfirm() {
  const payload: ExportConfirmPayload = { format: format.value }

  if (scope.value === 'view') {
    // 当前视图：应用 search + sort + visible cols
    if (props.searchValue.trim()) payload.search = props.searchValue.trim()
    if (props.sortState && props.sortState.dir) {
      payload.order = `${props.sortState.colId}:${props.sortState.dir}`
    }
    payload.fields = props.visibleCols.map(c => c.id).join(',')
  } else if (scope.value === 'selected') {
    // 仅选中行：传 rowIndices + 当前 visible cols
    payload.rowIndices = props.selectedRowIndices.join(',')
    payload.fields = props.visibleCols.map(c => c.id).join(',')
  }
  // scope === 'all' → 不传任何过滤参数，后端全量

  // 列重命名（仅对前端本地改名的原始列生效）
  const renameEntries: string[] = []
  for (const [colId, newLabel] of Object.entries(props.colRenames)) {
    if (newLabel) renameEntries.push(`${colId}:${newLabel}`)
  }
  if (renameEntries.length > 0) {
    payload.renames = renameEntries.join(',')
  }

  emit('confirm', payload)
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="导出选品结果"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <div class="export-section">
      <div class="export-label">文件格式</div>
      <ElRadioGroup v-model="format">
        <ElRadioButton value="xlsx">Excel (.xlsx)</ElRadioButton>
        <ElRadioButton value="csv">CSV (.csv)</ElRadioButton>
      </ElRadioGroup>
    </div>

    <div class="export-section">
      <div class="export-label">导出范围</div>
      <ElRadioGroup v-model="scope" class="export-scope">
        <ElRadio value="view">
          当前视图 <span class="export-count">({{ visibleRows }} 行 · {{ visibleCols.length }} 列)</span>
        </ElRadio>
        <ElRadio value="selected" :disabled="selectedCount === 0">
          仅选中行 <span class="export-count">({{ selectedCount }} 行)</span>
        </ElRadio>
        <ElRadio value="all">
          全部行 <span class="export-count">({{ totalRows }} 行 · 全部列)</span>
        </ElRadio>
      </ElRadioGroup>
    </div>

    <p class="export-hint">
      ⓘ "当前视图"会按你当前的搜索 / 排序 / 列显隐与列重命名导出；"全部行"会忽略所有视图过滤直接导出原始数据。
    </p>

    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">确认导出</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.export-section {
  margin-bottom: 18px;
}
.export-label {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 8px;
}
.export-scope :deep(.el-radio) {
  display: flex;
  align-items: center;
  margin-right: 0;
  margin-bottom: 8px;
}
.export-count {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
.export-hint {
  margin: 8px 0 0;
  padding: 8px 12px;
  background: #f4f4f5;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
}
</style>
