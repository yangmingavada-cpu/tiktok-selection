<script setup lang="ts">
/**
 * 工具栏
 * 共 N 条 / 已选 K 行 / 搜索 / 列显隐 / +增列 / 行高三档 / 导出
 */
import {
  ElButton,
  ElCheckbox,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElIcon,
  ElInput,
  ElRadioButton,
  ElRadioGroup,
} from 'element-plus'
import { Download, Plus, Search, View } from '@element-plus/icons-vue'

import type { RowHeightMode } from './constants'
import type { DataGridColumn } from '@/types'

defineProps<{
  totalRows: number
  visibleRows: number
  selectedCount: number
  searchValue: string
  rowHeightMode: RowHeightMode
  /** 所有列（包括隐藏的） */
  allCols: readonly DataGridColumn[]
  /** 列显隐查询 */
  isVisible: (colId: string) => boolean
  editable: boolean
}>()

const emit = defineEmits<{
  'update:searchValue': [value: string]
  'update:rowHeightMode': [value: RowHeightMode]
  toggleCol: [colId: string, visible: boolean]
  addExtraCol: []
  export: []
}>()

function onColToggle(colId: string, currentVisible: boolean) {
  emit('toggleCol', colId, !currentVisible)
}
</script>

<template>
  <div class="data-grid__toolbar">
    <span class="data-grid__toolbar-stats">
      共 <strong>{{ visibleRows }}</strong>
      <template v-if="visibleRows !== totalRows">/ {{ totalRows }}</template>
      条
      <template v-if="selectedCount > 0">
        · 已选 <strong>{{ selectedCount }}</strong> 行
      </template>
    </span>

    <ElInput
      :model-value="searchValue"
      class="data-grid__toolbar-search"
      placeholder="全表搜索"
      size="small"
      clearable
      :prefix-icon="Search"
      @update:model-value="(v: string) => emit('update:searchValue', v)"
    />

    <div class="data-grid__toolbar-spacer" />

    <!-- 行高 -->
    <ElRadioGroup
      :model-value="rowHeightMode"
      size="small"
      @update:model-value="(v: RowHeightMode | string | number | boolean | undefined) => v && emit('update:rowHeightMode', v as RowHeightMode)"
    >
      <ElRadioButton value="compact">紧凑</ElRadioButton>
      <ElRadioButton value="default">标准</ElRadioButton>
      <ElRadioButton value="tall">宽松</ElRadioButton>
    </ElRadioGroup>

    <div class="data-grid__toolbar-divider" />

    <!-- 列显隐 -->
    <ElDropdown
      trigger="click"
      :hide-on-click="false"
      popper-class="data-grid__col-visibility-popper"
    >
      <ElButton size="small">
        <ElIcon><View /></ElIcon>
        <span style="margin-left: 4px">列显示</span>
      </ElButton>
      <template #dropdown>
        <ElDropdownMenu>
          <ElDropdownItem
            v-for="col in allCols"
            :key="col.id"
            @click.stop="onColToggle(col.id, isVisible(col.id))"
          >
            <ElCheckbox
              :model-value="isVisible(col.id)"
              @click.stop
              @change="onColToggle(col.id, isVisible(col.id))"
            />
            <span class="col-visibility-label">{{ col.label }}</span>
          </ElDropdownItem>
        </ElDropdownMenu>
      </template>
    </ElDropdown>

    <!-- 增列 -->
    <ElButton size="small" :disabled="!editable" @click="emit('addExtraCol')">
      <ElIcon><Plus /></ElIcon>
      <span style="margin-left: 4px">增列</span>
    </ElButton>

    <!-- 导出 -->
    <ElButton size="small" type="primary" @click="emit('export')">
      <ElIcon><Download /></ElIcon>
      <span style="margin-left: 4px">导出</span>
    </ElButton>
  </div>
</template>
