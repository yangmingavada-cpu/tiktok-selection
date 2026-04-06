<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import type { ColumnDim } from '@/types'
import { createUniver, LocaleType, mergeLocales } from '@univerjs/presets'
import { UniverSheetsCorePreset } from '@univerjs/preset-sheets-core'
import UniverPresetSheetsCoreZhCN from '@univerjs/preset-sheets-core/locales/zh-CN'
import '@univerjs/preset-sheets-core/lib/index.css'

const props = defineProps<{
  tableCols: readonly ColumnDim[]
  tableData: readonly Record<string, unknown>[]
}>()

const containerRef = ref<HTMLDivElement>()

// 重要：不能用 ref/reactive 包裹 Univer 实例，Vue 代理会破坏其内部状态
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let univerInst: any = null
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let univerAPI: any = null

// ─── 数据格式化 ───────────────────────────────────────────────
function formatValue(col: ColumnDim, val: unknown): string | number {
  if (val === null || val === undefined) return ''
  if (col.type === 'number'  && typeof val === 'number') return val
  if (col.type === 'percent' && typeof val === 'number') return val.toFixed(1) + '%'
  if (col.type === 'score'   && typeof val === 'number') return Number(val.toFixed(1))
  return String(val)
}

// 构建二维数组：第 0 行为表头，第 1+ 行为数据
function buildMatrix(
  cols: readonly ColumnDim[],
  rows: readonly Record<string, unknown>[]
): (string | number)[][] {
  const header: (string | number)[] = ['#', ...cols.map(c => c.label)]
  const data = rows.map((row, i) => [
    (i + 1) as string | number,
    ...cols.map(c => formatValue(c, row[c.id])),
  ])
  return [header, ...data]
}

// ─── 局部更新（SSE 推送时调用）───────────────────────────────
function updateSheet(cols: readonly ColumnDim[], rows: readonly Record<string, unknown>[]) {
  if (!univerAPI || cols.length === 0 || rows.length === 0) return
  try {
    const fSheet = univerAPI.getActiveWorkbook()?.getActiveSheet()
    if (!fSheet) return
    const matrix = buildMatrix(cols, rows)
    fSheet.getRange(0, 0, matrix.length, matrix[0].length).setValues(matrix)
  } catch (e) {
    console.warn('[UniverSheet] updateSheet error', e)
  }
}

// ─── 生命周期 ─────────────────────────────────────────────────
onMounted(() => {
  if (!containerRef.value) return

  const { univer, univerAPI: api } = createUniver({
    locale: LocaleType.ZH_CN,
    locales: {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      [LocaleType.ZH_CN]: mergeLocales(UniverPresetSheetsCoreZhCN as any),
    },
    presets: [
      UniverSheetsCorePreset({
        container: containerRef.value,
      }),
    ],
  })

  univerInst = univer
  univerAPI = api

  univerAPI.createWorkbook({
    name: '选品数据',
    sheetOrder: ['sheet1'],
    sheets: {
      sheet1: {
        id: 'sheet1',
        name: 'Sheet1',
        rowCount: 10000,
        columnCount: 100,
      },
    },
  })

  // 首次挂载时已有数据，延迟 100ms 等 Univer 完全初始化后写入
  if (props.tableCols.length > 0 && props.tableData.length > 0) {
    setTimeout(() => updateSheet(props.tableCols, props.tableData), 100)
  }
})

onUnmounted(() => {
  univerInst?.dispose()
  univerInst = null
  univerAPI = null
})

// ─── 响应 SSE 数据更新 ────────────────────────────────────────
// deep:false：SSE 事件替换的是整个 tableData 引用，浅监听即可
watch(
  () => [props.tableData, props.tableCols] as const,
  ([newData, newCols]) => {
    updateSheet(newCols, newData)
  },
  { deep: false }
)

// ─── 导出 Excel（SheetJS 动态导入）──────────────────────────
async function exportExcel() {
  const cols = props.tableCols
  const rows = props.tableData
  if (cols.length === 0 || rows.length === 0) return

  const { utils, writeFile } = await import('xlsx')

  const header = ['#', ...cols.map(c => c.label)]
  const data = rows.map((row, i) => [
    i + 1,
    ...cols.map(c => {
      const val = row[c.id]
      if (val === null || val === undefined) return ''
      if (c.type === 'percent' && typeof val === 'number') return val.toFixed(1) + '%'
      return val
    }),
  ])

  const ws = utils.aoa_to_sheet([header, ...data])
  const wb = utils.book_new()
  utils.book_append_sheet(wb, ws, '选品数据')
  writeFile(wb, '选品数据.xlsx')
}

defineExpose({ exportExcel })
</script>

<template>
  <div class="univer-wrapper">
    <!-- 始终保持在 DOM 中（不用 v-if），确保 onMounted 能拿到 containerRef -->
    <div ref="containerRef" class="univer-container" />
    <!-- 无数据时覆盖空状态遮罩 -->
    <div v-if="tableData.length === 0" class="empty-overlay">
      <el-empty description="暂无数据" :image-size="120" />
    </div>
  </div>
</template>

<style scoped>
.univer-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}

.univer-container {
  width: 100%;
  height: 100%;
}

.empty-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  z-index: 1;
}
</style>
