<template>
  <div class="admin-dashboard">
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-label">总用户数</div>
            <div class="stat-value">{{ overview?.totalUsers ?? '-' }}</div>
            <div class="stat-sub">今日新增 {{ overview?.todayNewUsers ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-label">总会话数</div>
            <div class="stat-value">{{ overview?.totalSessions ?? '-' }}</div>
            <div class="stat-sub">今日 {{ overview?.todaySessions ?? 0 }} | 运行中 {{ overview?.runningSessions ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-label">累计 API 调用</div>
            <div class="stat-value">{{ formatNum(overview?.totalEchotikCalls) }}</div>
            <div class="stat-sub">Echotik API</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-label">累计 Token 消耗</div>
            <div class="stat-value">{{ formatNum(overview?.totalLlmTokens) }}</div>
            <div class="stat-sub">LLM Tokens</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never" header="密钥池状态">
          <div class="key-pool-stats" v-if="overview?.apiKeyPoolStatus">
            <span>总数：<strong>{{ overview.apiKeyPoolStatus.total }}</strong></span>
            <el-divider direction="vertical" />
            <span>可用：<strong>{{ overview.apiKeyPoolStatus.active }}</strong></span>
            <el-divider direction="vertical" />
            <span>
              低余量预警：
              <el-tag :type="overview.apiKeyPoolStatus.lowQuotaCount > 0 ? 'danger' : 'success'" size="small">
                {{ overview.apiKeyPoolStatus.lowQuotaCount }}
              </el-tag>
            </span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" header="密钥余量明细">
          <el-table :data="apiKeyStats" size="small" max-height="240">
            <el-table-column prop="name" label="名称" />
            <el-table-column label="剩余/总量">
              <template #default="{ row }">
                {{ row.remainingCalls ?? '∞' }} / {{ row.totalCalls ?? '∞' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.belowThreshold" type="danger" size="small">低余量</el-tag>
                <el-tag v-else-if="row.active" type="success" size="small">正常</el-tag>
                <el-tag v-else type="info" size="small">禁用</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboardOverview, getDashboardApiKeys } from '@/api/admin'
import type { DashboardOverview, EchotikApiKey } from '@/types'

const overview = ref<DashboardOverview | null>(null)
const apiKeyStats = ref<EchotikApiKey[]>([])

function formatNum(n: number | undefined): string {
  if (n == null) return '-'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

async function loadData() {
  const [ov, ak] = await Promise.all([getDashboardOverview(), getDashboardApiKeys()])
  overview.value = ov.data
  apiKeyStats.value = ak.data
}

onMounted(loadData)
</script>

<style scoped>
.admin-dashboard {
  padding: 4px;
}

.stat-item {
  text-align: center;
  padding: 8px 0;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
}

.stat-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.key-pool-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
</style>
