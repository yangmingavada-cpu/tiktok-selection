/**
 * DataGrid 全局常量
 * 行高/冻结/虚拟滚动阈值/列宽默认值等。
 *
 * 设计参考 Baserow 的 GRID_VIEW_SIZE_TO_ROW_HEIGHT_MAPPING（紧凑/标准/宽松）。
 */
import type { ComputedRef, InjectionKey } from 'vue'

export type RowHeightMode = 'compact' | 'default' | 'tall'

/**
 * 列尺度注入 key
 * DataGrid 顶层扫描 tableData 得到每个 score 列的实际 max 值，通过 provide 下发；
 * ScoreCell 用 inject 拿到自己列的 scale，按 value/scale 算 bar 宽度与颜色比例。
 * 这样 0-10 数据和 0-100 数据都能自适应，不再硬编码 range。
 */
export const COL_SCALES_KEY: InjectionKey<ComputedRef<Record<string, number>>> =
  Symbol('dataGridColScales')

export const ROW_HEIGHT: Record<RowHeightMode, number> = {
  compact: 28,
  default: 36,
  tall: 56,
}

/** 行号列宽度（sticky 左侧） */
export const ROW_NUMBER_COL_WIDTH = 56

/** 选中复选框列宽度 */
export const CHECKBOX_COL_WIDTH = 40

/** 可滚动区最少需要保留的宽度，否则强制取消冻结（参照 Baserow canFitFrozenColumns） */
export const FROZEN_MIN_SCROLL_WIDTH = 300

/** 行数超过此阈值才启用虚拟滚动 */
export const VIRTUAL_THRESHOLD = 100

/** 各类型列的默认宽度 */
export const DEFAULT_COL_WIDTH: Record<string, number> = {
  string: 180,
  number: 120,
  percent: 140,
  score: 140,
  tag: 140,
}

/** localStorage key 前缀 */
export const LS_KEY = {
  COL_STATE: 'datagrid:colstate',     // datagrid:colstate:{sessionId} → { [colId]: { width, visible, rename } }
  COL_ORDER: 'datagrid:colorder',     // datagrid:colorder:{sessionId} → string[]
  ROW_HEIGHT: 'datagrid:rowheight',   // datagrid:rowheight → 'compact'|'default'|'tall'
} as const
