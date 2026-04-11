/**
 * 行多选 composable
 *
 * 用 Set<number>（原数组下标）记录选中的行。
 * 仅内存态，不持久化（每次进入页面重置）。
 */
import { computed, ref } from 'vue'

export function useRowSelection() {
  const selected = ref<Set<number>>(new Set())

  const selectedCount = computed(() => selected.value.size)

  function toggle(originalIndex: number) {
    const next = new Set(selected.value)
    if (next.has(originalIndex)) {
      next.delete(originalIndex)
    } else {
      next.add(originalIndex)
    }
    selected.value = next
  }

  function setSelected(originalIndex: number, value: boolean) {
    const next = new Set(selected.value)
    if (value) next.add(originalIndex)
    else next.delete(originalIndex)
    selected.value = next
  }

  function clear() {
    selected.value = new Set()
  }

  function selectAll(indices: number[]) {
    selected.value = new Set(indices)
  }

  function isSelected(originalIndex: number): boolean {
    return selected.value.has(originalIndex)
  }

  /** 用于"全选当前可视行"checkbox 的三态：none / partial / all */
  function checkboxState(visibleIndices: number[]): 'none' | 'partial' | 'all' {
    if (visibleIndices.length === 0) return 'none'
    let hit = 0
    for (const i of visibleIndices) {
      if (selected.value.has(i)) hit++
    }
    if (hit === 0) return 'none'
    if (hit === visibleIndices.length) return 'all'
    return 'partial'
  }

  return {
    selected,
    selectedCount,
    toggle,
    setSelected,
    clear,
    selectAll,
    isSelected,
    checkboxState,
  }
}
