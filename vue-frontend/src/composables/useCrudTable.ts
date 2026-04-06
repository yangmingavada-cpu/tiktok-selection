import { ref, reactive, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'

interface CrudOptions<T, F> {
  /** Fetch list data from API */
  listApi: () => Promise<{ data: T[] }>
  /** Create item API */
  createApi: (data: F) => Promise<unknown>
  /** Update item API */
  updateApi: (id: string, data: F) => Promise<unknown>
  /** Delete item API */
  deleteApi: (id: string) => Promise<unknown>
  /** Default form values for creating new items */
  defaultForm: () => F
  /** Extract id from a row */
  getId?: (row: T) => string
  /** Transform row to form data when editing */
  rowToForm?: (row: T) => F
}

export function useCrudTable<T extends { id: string }, F extends object>(
  options: CrudOptions<T, F>,
) {
  const {
    listApi,
    createApi,
    updateApi,
    deleteApi,
    defaultForm,
    getId = (row) => row.id,
    rowToForm = (row) => ({ ...row }) as unknown as F,
  } = options

  const loading = ref(false)
  const saving = ref(false)
  const list = ref<T[]>([]) as Ref<T[]>
  const dialogVisible = ref(false)
  const isEdit = ref(false)
  const editId = ref('')
  const formRef = ref<FormInstance>()
  const form = reactive<F>(defaultForm()) as F

  async function loadData() {
    loading.value = true
    try {
      const res = await listApi()
      list.value = res.data
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    isEdit.value = false
    editId.value = ''
    Object.assign(form, defaultForm())
    dialogVisible.value = true
  }

  function openEdit(row: T) {
    isEdit.value = true
    editId.value = getId(row)
    Object.assign(form, rowToForm(row))
    dialogVisible.value = true
  }

  async function handleSave() {
    await formRef.value?.validate()
    saving.value = true
    try {
      if (isEdit.value) {
        await updateApi(editId.value, form)
      } else {
        await createApi(form)
      }
      ElMessage.success('保存成功')
      dialogVisible.value = false
      await loadData()
    } finally {
      saving.value = false
    }
  }

  async function handleDelete(id: string) {
    await deleteApi(id)
    ElMessage.success('删除成功')
    await loadData()
  }

  return {
    loading,
    saving,
    list,
    dialogVisible,
    isEdit,
    editId,
    formRef,
    form,
    loadData,
    openCreate,
    openEdit,
    handleSave,
    handleDelete,
  }
}
