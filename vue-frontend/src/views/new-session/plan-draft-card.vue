<template>
  <div class="plan-draft-card">
    <div class="plan-topbar">
      <span class="plan-topbar-title">📝 选品规划草稿</span>
      <el-tag size="small" type="warning">等待确认</el-tag>
    </div>
    <div class="plan-body">
      <table class="plan-fields">
        <tbody>
          <tr><td class="field-label">目标市场</td><td class="field-value">{{ plan.market }}</td></tr>
          <tr><td class="field-label">商品品类</td><td class="field-value">{{ plan.category }}</td></tr>
          <tr><td class="field-label">价格区间</td><td class="field-value">{{ plan.price_range }}</td></tr>
          <tr v-if="plan.filters"><td class="field-label">筛选条件</td><td class="field-value">{{ plan.filters }}</td></tr>
          <tr v-if="plan.scoring_dimensions"><td class="field-label">评分维度</td><td class="field-value">{{ plan.scoring_dimensions }}</td></tr>
          <tr><td class="field-label">推荐数量</td><td class="field-value">{{ plan.output_count ?? 20 }} 个</td></tr>
          <tr v-if="plan.strategy_notes"><td class="field-label">策略备注</td><td class="field-value">{{ plan.strategy_notes }}</td></tr>
        </tbody>
      </table>
    </div>
    <div class="plan-actions">
      <el-button size="small" @click="emit('reject')">重新描述</el-button>
      <el-button size="small" type="primary" :loading="confirming" @click="emit('confirm')">
        确认规划，开始构建
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PlanDraft } from '@/types'

const props = defineProps<{
  plan: PlanDraft
  confirming: boolean
}>()
void props

const emit = defineEmits<{
  confirm: []
  reject: []
}>()
</script>

<style scoped>
.plan-draft-card {
  margin-top: 12px;
  border: 1px solid #fde68a;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(234, 179, 8, 0.1);
}

.plan-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: linear-gradient(90deg, #fffbeb 0%, #fefce8 100%);
  border-bottom: 1px solid #fde68a;
}

.plan-topbar-title {
  font-size: 13px;
  font-weight: 600;
  color: #92400e;
}

.plan-body {
  padding: 16px 20px;
  background: #fff;
}

.plan-fields {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.plan-fields tr + tr td {
  border-top: 1px solid #f3f4f6;
}

.field-label {
  padding: 7px 12px 7px 0;
  color: #6b7280;
  white-space: nowrap;
  width: 80px;
  vertical-align: top;
}

.field-value {
  padding: 7px 0;
  color: #111827;
  line-height: 1.5;
}

.plan-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 8px 12px;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
}
</style>
