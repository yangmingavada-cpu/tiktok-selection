/**
 * 虚拟滚动 composable
 *
 * 输入：行数组（响应式）、行高、容器 ref
 * 输出：可见行下标范围、上下 padding 高度
 *
 * 简化策略：
 * - 行数 ≤ VIRTUAL_THRESHOLD 时不启用，全量渲染
 * - 否则按 scrollTop / rowHeight 计算 visibleStart / visibleEnd，前后 buffer 5 行
 */
import { computed, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

import { VIRTUAL_THRESHOLD } from '../constants'

const BUFFER_ROWS = 5

interface UseVirtualScrollOptions {
  containerRef: Ref<HTMLElement | null>
  rowCount: Ref<number>
  rowHeight: Ref<number>
}

export function useVirtualScroll(opts: UseVirtualScrollOptions) {
  const scrollTop = ref(0)
  const viewportHeight = ref(0)

  const enabled = computed(() => opts.rowCount.value > VIRTUAL_THRESHOLD)

  const visibleStart = computed(() => {
    if (!enabled.value) return 0
    const raw = Math.floor(scrollTop.value / opts.rowHeight.value) - BUFFER_ROWS
    return Math.max(0, raw)
  })

  const visibleEnd = computed(() => {
    if (!enabled.value) return opts.rowCount.value
    const visibleRows = Math.ceil(viewportHeight.value / opts.rowHeight.value) + BUFFER_ROWS * 2
    return Math.min(opts.rowCount.value, visibleStart.value + visibleRows)
  })

  const topPadding = computed(() => visibleStart.value * opts.rowHeight.value)
  const bottomPadding = computed(
    () => Math.max(0, opts.rowCount.value - visibleEnd.value) * opts.rowHeight.value,
  )
  const totalHeight = computed(() => opts.rowCount.value * opts.rowHeight.value)

  let scrollTimer: number | null = null

  function onScroll() {
    const el = opts.containerRef.value
    if (!el) return
    if (scrollTimer !== null) return
    scrollTimer = window.requestAnimationFrame(() => {
      scrollTop.value = el.scrollTop
      viewportHeight.value = el.clientHeight
      scrollTimer = null
    })
  }

  function onResize() {
    const el = opts.containerRef.value
    if (!el) return
    viewportHeight.value = el.clientHeight
  }

  onMounted(() => {
    const el = opts.containerRef.value
    if (el) {
      viewportHeight.value = el.clientHeight
      el.addEventListener('scroll', onScroll, { passive: true })
    }
    window.addEventListener('resize', onResize)
  })

  onBeforeUnmount(() => {
    const el = opts.containerRef.value
    if (el) el.removeEventListener('scroll', onScroll)
    window.removeEventListener('resize', onResize)
    if (scrollTimer !== null) {
      cancelAnimationFrame(scrollTimer)
      scrollTimer = null
    }
  })

  // 行数变化时重置滚动到顶（避免编辑场景下虚拟窗口错位）
  watch(opts.rowCount, () => {
    scrollTop.value = 0
    const el = opts.containerRef.value
    if (el) el.scrollTop = 0
  })

  return {
    enabled,
    visibleStart,
    visibleEnd,
    topPadding,
    bottomPadding,
    totalHeight,
    scrollTop,
  }
}
