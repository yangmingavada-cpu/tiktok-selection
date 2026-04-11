<script setup lang="ts">
/**
 * 单列表头
 * 三段式：左 type icon + 中 label（可双击重命名）+ 右 sort/menu/resize handle
 * 参考 Baserow GridViewFieldType 的设计。
 */
import { computed, nextTick, ref, watch } from 'vue'
import {
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElIcon,
  ElMessageBox,
} from 'element-plus'
import {
  ArrowDown,
  ArrowUp,
  Edit,
  Hide,
  More,
  Setting,
  Sort,
} from '@element-plus/icons-vue'

import { COL_TYPE_ICON, COL_TYPE_LABEL } from './helpers/column-icons'
import type { SortDir } from './helpers/use-grid-filter-sort'
import type { DataGridColumn } from '@/types'

const props = defineProps<{
  col: DataGridColumn
  width: number
  sortDir: SortDir
  /** 是否允许重命名（终态才可改增列；原始列在任何状态都可本地重命名） */
  editable: boolean
}>()

const emit = defineEmits<{
  cycleSort: [colId: string]
  startResize: [colId: string, startX: number, startWidth: number]
  rename: [colId: string, newLabel: string]
  hide: [colId: string]
  remove: [colId: string]
  editOptions: [colId: string]
  moveColumn: [fromColId: string, toColId: string]
}>()

const editing = ref(false)
const draft = ref('')
const inputRef = ref<HTMLInputElement>()

// ── 列拖拽排序 ───────────────────────────────────
const isDragOver = ref(false)
const DRAG_MIME = 'text/x-datagrid-col-id'

function onDragStart(e: DragEvent) {
  if (!e.dataTransfer) return
  e.dataTransfer.setData(DRAG_MIME, props.col.id)
  e.dataTransfer.effectAllowed = 'move'
}

function onDragOver(e: DragEvent) {
  if (!e.dataTransfer) return
  e.dataTransfer.dropEffect = 'move'
  isDragOver.value = true
}

function onDragLeave() {
  isDragOver.value = false
}

function onDrop(e: DragEvent) {
  isDragOver.value = false
  const fromId = e.dataTransfer?.getData(DRAG_MIME)
  if (fromId && fromId !== props.col.id) {
    emit('moveColumn', fromId, props.col.id)
  }
}

const typeIcon = computed(() => COL_TYPE_ICON[props.col.type])
const typeLabel = computed(() => COL_TYPE_LABEL[props.col.type])

watch(editing, async (val) => {
  if (val) {
    draft.value = props.col.label
    await nextTick()
    inputRef.value?.focus()
    inputRef.value?.select()
  }
})

function onLabelDoubleClick() {
  // 用户增列在终态可改名（走后端）；原始列任何状态都可本地改名
  if (props.col.isExtra && !props.editable) return
  editing.value = true
}

function commitRename() {
  const trimmed = draft.value.trim()
  editing.value = false
  if (trimmed && trimmed !== props.col.label) {
    emit('rename', props.col.id, trimmed)
  }
}

function cancelRename() {
  editing.value = false
}

function onSortClick(e: MouseEvent) {
  e.stopPropagation()
  emit('cycleSort', props.col.id)
}

function onResizeMouseDown(e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  emit('startResize', props.col.id, e.clientX, props.width)
}

async function onMenuCommand(cmd: string) {
  if (cmd === 'hide') {
    emit('hide', props.col.id)
  } else if (cmd === 'rename') {
    editing.value = true
  } else if (cmd === 'edit-options') {
    emit('editOptions', props.col.id)
  } else if (cmd === 'remove') {
    try {
      await ElMessageBox.confirm(`确定删除列「${props.col.label}」？该列下所有值会被一并清除。`, '删除列', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      })
      emit('remove', props.col.id)
    } catch {
      /* cancelled */
    }
  }
}
</script>

<template>
  <div
    class="grid-view__column"
    :class="{ 'grid-view__column--drop-target': isDragOver }"
    :style="{ width: width + 'px' }"
    draggable="true"
    @dragstart="onDragStart"
    @dragover.prevent="onDragOver"
    @dragleave="onDragLeave"
    @drop.prevent="onDrop"
  >
    <div class="grid-view__description">
      <ElIcon class="grid-view__description-icon" :title="typeLabel">
        <component :is="typeIcon" />
      </ElIcon>

      <input
        v-if="editing"
        ref="inputRef"
        v-model="draft"
        class="grid-view__description-name-input"
        draggable="false"
        @blur="commitRename"
        @keydown.enter.prevent="commitRename"
        @keydown.esc.prevent="cancelRename"
      />
      <span
        v-else
        class="grid-view__description-name"
        :title="col.label"
        @dblclick.stop="onLabelDoubleClick"
      >{{ col.label }}</span>

      <div class="grid-view__description-options" draggable="false" @click.stop @mousedown.stop>
        <span
          class="grid-view__description-icon-trigger"
          :class="{ active: sortDir !== null }"
          :title="sortDir === 'asc' ? '升序（再点切换）' : sortDir === 'desc' ? '降序（再点取消）' : '点击排序'"
          @click="onSortClick"
        >
          <ElIcon>
            <ArrowUp v-if="sortDir === 'asc'" />
            <ArrowDown v-else-if="sortDir === 'desc'" />
            <Sort v-else />
          </ElIcon>
        </span>

        <ElDropdown trigger="click" @command="onMenuCommand">
          <span class="grid-view__description-icon-trigger" title="更多">
            <ElIcon><More /></ElIcon>
          </span>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem command="rename" :icon="Edit">
                重命名{{ col.isExtra ? '' : '（仅本地）' }}
              </ElDropdownItem>
              <ElDropdownItem
                v-if="col.isExtra && col.type === 'tag'"
                command="edit-options"
                :icon="Setting"
              >编辑选项</ElDropdownItem>
              <ElDropdownItem command="hide" :icon="Hide">隐藏列</ElDropdownItem>
              <ElDropdownItem
                v-if="col.isExtra"
                command="remove"
                divided
                style="color: #f56c6c"
              >删除列</ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
      </div>

      <div
        class="grid-view__description-width"
        title="拖动调整列宽"
        draggable="false"
        @mousedown.stop="onResizeMouseDown"
      />
    </div>
  </div>
</template>
