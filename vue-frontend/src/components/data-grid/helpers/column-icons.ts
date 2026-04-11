/**
 * 字段类型 → Element icon 映射
 * 参考 Baserow GridViewFieldType 用 field._.type.iconClass 渲染字段类型 icon 的设计。
 */
import type { Component } from 'vue'
import {
  Document,
  Discount,
  Histogram,
  Star,
  PriceTag,
} from '@element-plus/icons-vue'

import type { DataGridColumn } from '@/types'

/**
 * 5 种列类型 → 字段类型 icon
 *  string  → Aa（文本，用 Document icon 替代）
 *  number  → 123（数字，用 Histogram icon 替代）
 *  percent → %（百分比，用 Discount icon）
 *  score   → ★（评分，用 Star icon）
 *  tag     → ◉（标签，用 PriceTag icon）
 */
export const COL_TYPE_ICON: Record<DataGridColumn['type'], Component> = {
  string: Document,
  number: Histogram,
  percent: Discount,
  score: Star,
  tag: PriceTag,
}

export const COL_TYPE_LABEL: Record<DataGridColumn['type'], string> = {
  string: '文本',
  number: '数字',
  percent: '百分比',
  score: '评分',
  tag: '标签',
}
