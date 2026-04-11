/**
 * 单元格渲染辅助函数（纯函数，无 Vue 依赖）
 *
 * - 启发式识别 image / link
 * - 数字与百分比格式化
 * - 评分颜色映射
 * - CSV 字段转义
 *
 * 参考 Baserow GridViewFieldNumber 的 formatNumberValueForEdit / parseNumberValue 拆分思路。
 */

const IMAGE_EXT_RE = /\.(png|jpe?g|webp|gif|avif|svg)(\?|$)/i
const IMAGE_FIELD_HINT_RE = /(image|img|cover|avatar|thumb|photo|picture)/i
const HTTP_RE = /^https?:\/\//i

/**
 * 判断单元格的值是否应被渲染为图片缩略图
 * 双重规则：
 * 1) 强：值是 http(s) URL 且后缀为图片扩展名（含 query 参数）
 * 2) 弱：字段 id 含 image/img/cover/avatar/thumb/photo/picture 且值是 http(s) URL（兼容无后缀的 CDN URL）
 */
export function isImageUrl(value: unknown, fieldId: string): boolean {
  if (typeof value !== 'string') return false
  if (!HTTP_RE.test(value)) return false
  if (IMAGE_EXT_RE.test(value)) return true
  if (IMAGE_FIELD_HINT_RE.test(fieldId)) return true
  return false
}

/**
 * 判断字符串是否应渲染为可点击链接。
 * 必须排除 javascript: 与 data: 协议防 XSS。
 */
export function isHttpUrl(value: unknown): boolean {
  if (typeof value !== 'string') return false
  if (!HTTP_RE.test(value)) return false
  // 双重保险：HTTP_RE 已限制 http(s)，但仍显式排除危险前缀
  const lower = value.toLowerCase()
  return !lower.startsWith('javascript:') && !lower.startsWith('data:')
}

/** 数字显示格式化（千分位） */
export function formatNumberForDisplay(v: unknown): string {
  if (v === null || v === undefined || v === '') return ''
  if (typeof v === 'number') {
    if (!Number.isFinite(v)) return ''
    return v.toLocaleString()
  }
  const n = Number(v)
  if (Number.isFinite(n)) return n.toLocaleString()
  return String(v)
}

/** 从输入框文本解析为数字（容忍千分位逗号与空白） */
export function parseNumberFromInput(s: string): number | null {
  if (s === null || s === undefined) return null
  const cleaned = String(s).replace(/[,\s]/g, '')
  if (cleaned === '') return null
  const n = Number(cleaned)
  return Number.isFinite(n) ? n : null
}

/** 百分比显示格式化（输入是 0-100 范围的数字） */
export function formatPercentForDisplay(v: unknown): string {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (!Number.isFinite(n)) return String(v)
  return n.toFixed(1) + '%'
}

/**
 * 评分颜色映射（红 → 黄 → 绿 渐变）
 * 输入 0-1 的 ratio（= value / scale），由调用方根据列实际 max 算出；
 * 不再硬编码 0-10 范围，这样 0-10 / 0-100 / 0-1000 数据都能自适应。
 *
 * ratio 含义：
 *   0   → 红 (hue=0)
 *   0.5 → 黄 (hue=60)
 *   1   → 绿 (hue=120)
 * 超出 [0,1] 会被 clamp。
 */
export function getScoreColor(ratio: unknown): string {
  const n = Number(ratio)
  if (!Number.isFinite(n)) return '#cbd5e1'
  const clamped = Math.max(0, Math.min(1, n))
  const hue = clamped * 120
  return `hsl(${hue}, 65%, 48%)`
}

/** Tag 颜色映射：根据字符串 hash 给同一个值固定颜色 */
export function getTagColor(value: unknown): string {
  if (value === null || value === undefined || value === '') return 'transparent'
  const s = String(value)
  let hash = 0
  for (let i = 0; i < s.length; i++) {
    hash = (hash * 31 + s.charCodeAt(i)) & 0xffff
  }
  const hue = hash % 360
  return `hsl(${hue}, 70%, 90%)`
}

/** 给文本染色用的深色（搭配 getTagColor 的浅色背景） */
export function getTagTextColor(value: unknown): string {
  if (value === null || value === undefined || value === '') return '#9ca3af'
  const s = String(value)
  let hash = 0
  for (let i = 0; i < s.length; i++) {
    hash = (hash * 31 + s.charCodeAt(i)) & 0xffff
  }
  const hue = hash % 360
  return `hsl(${hue}, 70%, 30%)`
}

/** 用于全表搜索的小写化字符串拼接 */
export function rowMatchesSearch(row: Record<string, unknown>, query: string): boolean {
  if (!query) return true
  const q = query.toLowerCase()
  for (const key in row) {
    if (key === '__originalIndex') continue
    const v = row[key]
    if (v === null || v === undefined) continue
    if (String(v).toLowerCase().includes(q)) return true
  }
  return false
}
