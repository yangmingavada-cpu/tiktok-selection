<script setup lang="ts">
/**
 * 方案库执行前可视化编辑对话框
 *
 * 用户在方案库点"执行" → 弹这个对话框 → 左侧显示步骤列表 / 右侧动态字段表单 →
 * "审核并执行"按钮触发后端 audit_chain → 通过则 createSession + 跳转 SessionDetail
 *
 * 不展示 JSON：所有字段按后端 @McpParam schema 元数据动态渲染：
 *   - enum → ElSelect
 *   - boolean → ElSwitch
 *   - integer/number → ElInputNumber
 *   - array → multiple ElSelect (allow-create)
 *   - 普通 string → ElInput
 */
import { computed, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'

import { getBlockSchemas, type BlockSchema, type BlockSchemaField } from '@/api/blockSchema'
import { auditBlockChain, type AuditResult } from '@/api/intent'
import { createSession } from '@/api/session'
import type { Block, Plan } from '@/types'

const props = defineProps<{
  visible: boolean
  plan: Plan | null
}>()

const emit = defineEmits<{
  'update:visible': [v: boolean]
  executed: [sessionId: string]
}>()

// ── 本地状态 ─────────────────────────────────
const localChain = ref<Block[]>([])
const schemaMap = ref<Record<string, BlockSchema>>({})
const activeIdx = ref(0)
const loading = ref(false)
const auditing = ref(false)
const executing = ref(false)
const auditResult = ref<AuditResult | null>(null)

// ── 计算 ────────────────────────────────────
const activeBlock = computed(() => localChain.value[activeIdx.value])
const activeSchema = computed<BlockSchema | null>(() => {
  if (!activeBlock.value) return null
  return schemaMap.value[activeBlock.value.blockId] || null
})

const activeProperties = computed<Record<string, BlockSchemaField>>(() =>
  activeSchema.value?.schema.properties || {},
)

const activeRequired = computed<Set<string>>(() =>
  new Set(activeSchema.value?.schema.required || []),
)

// ── 进入对话框时拉 schema + 深拷贝 plan.blockChain ──
watch(
  () => props.visible,
  async (v) => {
    if (!v || !props.plan) return
    // 深拷贝避免直接污染 plan
    localChain.value = JSON.parse(JSON.stringify(props.plan.blockChain || [])) as Block[]
    activeIdx.value = 0
    auditResult.value = null

    if (localChain.value.length === 0) {
      schemaMap.value = {}
      return
    }

    const ids = [...new Set(localChain.value.map((b) => b.blockId))]
    loading.value = true
    try {
      const res = await getBlockSchemas(ids)
      schemaMap.value = res.data || {}
    } catch (e) {
      console.warn('[plan-edit] fetch schemas failed:', e)
      ElMessage.warning('加载字段元数据失败，请重试')
    } finally {
      loading.value = false
    }
  },
)

// ── 字段读写助手 ─────────────────────────────
function getFieldValue(name: string): unknown {
  const block = activeBlock.value
  if (!block) return undefined
  const config = (block.config || {}) as Record<string, unknown>
  return config[name]
}

function setFieldValue(name: string, val: unknown) {
  const block = activeBlock.value
  if (!block) return
  const config = { ...((block.config || {}) as Record<string, unknown>) }
  if (val === '' || val === null || val === undefined) {
    delete config[name]
  } else {
    config[name] = val
  }
  // 替换整个 block 触发响应式
  const next = [...localChain.value]
  next[activeIdx.value] = { ...block, config: config as Block['config'] }
  localChain.value = next
}

/** enum value 按 type 还原（schema 里 enum 总是 string[]，但实际可能是 integer/number/boolean） */
function castEnumValue(v: string, type: BlockSchemaField['type']): unknown {
  if (type === 'integer') return parseInt(v, 10)
  if (type === 'number') return parseFloat(v)
  if (type === 'boolean') return v === 'true'
  return v
}

// ── 步骤摘要 ─────────────────────────────────
function blockLabel(b: Block): string {
  return schemaMap.value[b.blockId]?.label || b.label || b.blockId
}

function blockSummary(b: Block): string {
  const cfg = (b.config || {}) as Record<string, unknown>
  const parts: string[] = []
  if (cfg.region) parts.push(String(cfg.region))
  if (cfg.category_id) parts.push(`类目 ${cfg.category_id}`)
  if (cfg.sort_by) parts.push(`按 ${cfg.sort_by} ${cfg.order || 'desc'}`)
  if (cfg.top_n) parts.push(`top ${cfg.top_n}`)
  if (cfg.field && cfg.operator) parts.push(`${cfg.field} ${cfg.operator} ${cfg.value ?? ''}`)
  if (cfg.score_type) parts.push(`${cfg.score_type} 评分`)
  return parts.join(' · ')
}

// ── 审核 / 执行 ─────────────────────────────
async function handleAudit() {
  auditing.value = true
  try {
    const res = await auditBlockChain(localChain.value)
    auditResult.value = res.data || null
    if (auditResult.value?.pass) {
      ElMessage.success(`审核通过，评分 ${auditResult.value.score}/100`)
    }
  } catch (e) {
    console.warn('[plan-edit] audit failed:', e)
    ElMessage.error('审核服务暂不可用')
  } finally {
    auditing.value = false
  }
}

async function handleAuditAndExecute() {
  await handleAudit()
  const result = auditResult.value
  if (!result) return
  if (!result.pass || result.score < 60) {
    ElMessage.warning('当前方案审核未通过，请按提示调整后再试')
    return
  }

  executing.value = true
  try {
    const res = await createSession({
      blockChain: localChain.value,
      sourceType: 'user_plan',
      sourcePlanId: props.plan?.id,
      title: props.plan?.name,
    })
    const sessionId = res.data?.id
    if (sessionId) {
      ElMessage.success('任务已创建')
      emit('executed', sessionId)
      emit('update:visible', false)
    }
  } catch (e) {
    console.warn('[plan-edit] create session failed:', e)
    ElMessage.error('执行失败，请重试')
  } finally {
    executing.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="编辑方案参数"
    width="960px"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:visible', v)"
  >
    <div v-loading="loading" class="plan-edit">
      <!-- 左：步骤列表 -->
      <aside class="plan-edit__steps">
        <div class="steps-header">执行步骤 ({{ localChain.length }})</div>
        <div
          v-for="(b, idx) in localChain"
          :key="idx"
          class="step-item"
          :class="{ active: activeIdx === idx }"
          @click="activeIdx = idx"
        >
          <span class="step-seq">{{ idx + 1 }}</span>
          <div class="step-info">
            <div class="step-label">{{ blockLabel(b) }}</div>
            <div v-if="blockSummary(b)" class="step-summary">{{ blockSummary(b) }}</div>
          </div>
        </div>
      </aside>

      <!-- 右：当前步骤的字段表单 -->
      <section class="plan-edit__form">
        <div v-if="!activeBlock" class="form-empty">请在左侧选择一个步骤</div>
        <template v-else>
          <div class="form-header">
            <h3>{{ blockLabel(activeBlock) }}</h3>
            <p v-if="activeSchema?.endpoint" class="form-hint">{{ activeSchema.endpoint }}</p>
            <p v-if="!activeSchema" class="form-hint warning">
              ⚠ 该块没有可编辑的字段元数据（{{ activeBlock.blockId }}）
            </p>
          </div>

          <ElForm v-if="activeSchema" label-width="160px" label-position="right">
            <ElFormItem
              v-for="(field, fieldName) in activeProperties"
              :key="fieldName"
              :required="activeRequired.has(fieldName)"
            >
              <template #label>
                <span class="field-label" :title="field.description">
                  {{ fieldName }}
                  <ElIcon v-if="field.description" class="field-info"><InfoFilled /></ElIcon>
                </span>
              </template>

              <!-- 枚举 → Select -->
              <ElSelect
                v-if="field.enum && field.enum.length > 0"
                :model-value="getFieldValue(fieldName) as string | number | boolean | undefined"
                :placeholder="field.default || '请选择'"
                clearable
                @update:model-value="(v) => setFieldValue(fieldName, v)"
              >
                <ElOption
                  v-for="e in field.enum"
                  :key="e"
                  :label="e"
                  :value="castEnumValue(e, field.type) as string | number | boolean"
                />
              </ElSelect>

              <!-- boolean → Switch -->
              <ElSwitch
                v-else-if="field.type === 'boolean'"
                :model-value="Boolean(getFieldValue(fieldName))"
                @update:model-value="(v) => setFieldValue(fieldName, v)"
              />

              <!-- integer/number → InputNumber -->
              <ElInputNumber
                v-else-if="field.type === 'integer' || field.type === 'number'"
                :model-value="getFieldValue(fieldName) as number | undefined"
                :precision="field.type === 'integer' ? 0 : undefined"
                :placeholder="field.default || ''"
                @update:model-value="(v) => setFieldValue(fieldName, v)"
              />

              <!-- array → multi-select with allow-create -->
              <ElSelect
                v-else-if="field.type === 'array'"
                :model-value="(getFieldValue(fieldName) as unknown[]) || []"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="回车添加"
                @update:model-value="(v) => setFieldValue(fieldName, v)"
              />

              <!-- string default → Input -->
              <ElInput
                v-else
                :model-value="getFieldValue(fieldName) as string | undefined"
                :placeholder="field.example || field.default || ''"
                @update:model-value="(v) => setFieldValue(fieldName, v)"
              />

              <div v-if="field.description" class="field-desc">{{ field.description }}</div>
            </ElFormItem>
          </ElForm>
        </template>
      </section>
    </div>

    <!-- 审核结果 banner -->
    <ElAlert
      v-if="auditResult"
      :type="auditResult.pass ? 'success' : 'warning'"
      :closable="false"
      class="audit-banner"
    >
      <template #title>
        审核评分：{{ auditResult.score }}/100 ·
        {{ auditResult.pass ? '通过，可以执行' : '存在风险，建议调整' }}
      </template>
      <ul v-if="auditResult.issues.length || auditResult.suggestions.length" class="audit-list">
        <li v-for="(issue, i) in auditResult.issues" :key="'i' + i">⚠ {{ issue }}</li>
        <li v-for="(s, i) in auditResult.suggestions" :key="'s' + i">💡 {{ s }}</li>
      </ul>
    </ElAlert>

    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton :loading="auditing" @click="handleAudit">仅审核</ElButton>
      <ElButton type="primary" :loading="executing || auditing" @click="handleAuditAndExecute">
        审核并执行
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.plan-edit {
  display: flex;
  gap: 16px;
  min-height: 480px;
  max-height: 60vh;
}

.plan-edit__steps {
  width: 240px;
  flex-shrink: 0;
  border-right: 1px solid #e4e7ed;
  padding-right: 12px;
  overflow-y: auto;
}

.steps-header {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  padding: 0 8px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.step-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s ease;
}

.step-item:hover {
  background: #f5f7fa;
}

.step-item.active {
  background: #ecf5ff;
  border: 1px solid #d9ecff;
}

.step-seq {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #c0c4cc;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-item.active .step-seq {
  background: #409eff;
}

.step-info {
  flex: 1;
  min-width: 0;
}

.step-label {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.step-summary {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.plan-edit__form {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 0 4px;
}

.form-header {
  margin-bottom: 16px;
}

.form-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.form-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}

.form-hint.warning {
  color: #e6a23c;
}

.form-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #c0c4cc;
}

.field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: help;
}

.field-info {
  font-size: 12px;
  color: #c0c4cc;
}

.field-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

.audit-banner {
  margin-top: 16px;
}

.audit-list {
  margin: 8px 0 0;
  padding-left: 20px;
  font-size: 13px;
}

.audit-list li {
  margin-bottom: 4px;
}
</style>
