<template>
  <div class="dashboard">
    <div class="page-header">
      <h2>工作台</h2>
      <span class="greeting">欢迎回来，{{ userStore.name || userStore.email }}</span>
    </div>

    <!-- Quick actions -->
    <el-row :gutter="20" class="quick-actions">
      <el-col :span="8">
        <el-card shadow="hover" class="action-card" @click="$router.push('/new')">
          <el-icon class="action-icon ai-icon"><MagicStick /></el-icon>
          <div class="action-title">AI智能新建</div>
          <div class="action-desc">用自然语言描述需求，AI自动构建选品管道</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="action-card" @click="$router.push('/sessions')">
          <el-icon class="action-icon record-icon"><Document /></el-icon>
          <div class="action-title">选品记录</div>
          <div class="action-desc">查看历史选品任务和结果数据</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="action-card" @click="$router.push('/plans')">
          <el-icon class="action-icon plan-icon"><FolderOpened /></el-icon>
          <div class="action-title">方案库</div>
          <div class="action-desc">管理和复用已保存的选品方案</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Recent sessions -->
    <el-card shadow="never" class="recent-card">
      <template #header>
        <div class="card-header-row">
          <span>最近选品记录</span>
          <el-button text size="small" @click="$router.push('/sessions')">查看全部</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="recentSessions" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="echotikApiCalls" label="API调用" width="90" align="center">
          <template #default="{ row }">{{ row.echotikApiCalls ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="$router.push(`/sessions/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && recentSessions.length === 0" description="暂无选品记录，点击「AI智能新建」开始第一次选品" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, onMounted } from 'vue'
import { MagicStick, Document, FolderOpened } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { listSessions } from '@/api/session'
import { getStatusLabel, getStatusTagType, PAGE_SIZE } from '@/constants'
import type { Session } from '@/types'

const userStore = useUserStore()
const loading = shallowRef(false)
const recentSessions = ref<Session[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await listSessions({ pageNum: 1, pageSize: PAGE_SIZE.DASHBOARD })
    recentSessions.value = res.data?.records || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.dashboard { padding: 0; }

.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; }
.greeting { font-size: 14px; color: #909399; }

.quick-actions { margin-bottom: 24px; }

.action-card {
  cursor: pointer;
  text-align: center;
  padding: 8px 0;
  transition: transform 0.2s;
}
.action-card:hover { transform: translateY(-2px); }

.action-icon {
  font-size: 36px;
  margin-bottom: 10px;
}
.ai-icon { color: #6366f1; }
.record-icon { color: #3b82f6; }
.plan-icon { color: #10b981; }

.action-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.action-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
</style>
