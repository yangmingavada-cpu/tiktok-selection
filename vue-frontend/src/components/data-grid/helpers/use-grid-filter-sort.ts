/**
 * 全表搜索 + 列排序 状态 composable
 *
 * 数据流：mergedRows → applySearch → applySort → 结果传给虚拟滚动
 * 排序点击三态切换：null → asc → desc → null
 */
import { computed, ref } from 'vue'

import { rowMatchesSearch } from './cell-renderers'

export type SortDir = 'asc' | 'desc' | null

export interface SortState {
  colId: string
  dir: SortDir
}

interface UseGridFilterSortOptions {
  rowsRef: { value: Record<string, unknown>[] }
}

export function useGridFilterSort(opts: UseGridFilterSortOptions) {
  const search = ref('')
  const sortState = ref<SortState | null>(null)

  /** 切换某列的排序：null → asc → desc → null */
  function cycleSort(colId: string) {
    if (!sortState.value || sortState.value.colId !== colId) {
      sortState.value = { colId, dir: 'asc' }
      return
    }
    if (sortState.value.dir === 'asc') {
      sortState.value.dir = 'desc'
      return
    }
    sortState.value = null
  }

  function getSortDir(colId: string): SortDir {
    if (!sortState.value || sortState.value.colId !== colId) return null
    return sortState.value.dir
  }

  function clearSort() {
    sortState.value = null
  }

  function clearSearch() {
    search.value = ''
  }

  /** 应用搜索 + 排序后的行（每行已带 __originalIndex） */
  const filteredSortedRows = computed(() => {
    let result = opts.rowsRef.value

    // 1. search
    if (search.value.trim()) {
      const q = search.value.trim()
      result = result.filter(row => rowMatchesSearch(row, q))
    }

    // 2. sort（不可变副本，避免污染原数组）
    if (sortState.value && sortState.value.dir) {
      const { colId, dir } = sortState.value
      const desc = dir === 'desc'
      result = [...result].sort((a, b) => {
        const va = a[colId]
        const vb = b[colId]
        let cmp: number
        if (va === null || va === undefined) cmp = vb === null || vb === undefined ? 0 : -1
        else if (vb === null || vb === undefined) cmp = 1
        else if (typeof va === 'number' && typeof vb === 'number') cmp = va - vb
        else {
          const sa = String(va)
          const sb = String(vb)
          cmp = sa < sb ? -1 : sa > sb ? 1 : 0
        }
        return desc ? -cmp : cmp
      })
    }

    return result
  })

  return {
    search,
    sortState,
    cycleSort,
    getSortDir,
    clearSort,
    clearSearch,
    filteredSortedRows,
  }
}
