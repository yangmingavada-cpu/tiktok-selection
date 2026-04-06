<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Clock, Delete, Plus, RefreshRight } from '@element-plus/icons-vue'
import type { Session } from '@/types'

const props = defineProps<{
  sessions: readonly Session[]
  loading: boolean
  hydratedFromCache: boolean
  cacheUpdatedAt: number | null
}>()

const emit = defineEmits<{
  refresh: []
  deleteSession: [session: Session]
  restoreConversation: [session: Session]
}>()

const router = useRouter()

const cacheText = computed(() => {
  if (!props.cacheUpdatedAt) return ''
  return new Date(props.cacheUpdatedAt).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
})

function openSession(session: Session) {
  // 恢复对话（现在历史中只有对话，不包含已执行的任务）
  emit('restoreConversation', session)
}

function createNewSession() {
  router.push({ name: 'NewSession' })
}
</script>

<template>
  <aside class="history-rail">
    <div class="history-hero">
      <div class="history-copy">
        <span class="history-eyebrow">Conversation History</span>
        <h3 class="history-title">对话历史</h3>
        <p class="history-subtitle">点击历史记录可以继续之前的对话，调整选品方案。已执行的任务请到"选品记录"查看。</p>
      </div>
      <el-button class="history-refresh" circle text :icon="RefreshRight" :disabled="loading" @click="emit('refresh')" />
    </div>

    <div class="history-toolbar">
      <div class="history-status">
        <span v-if="hydratedFromCache && cacheText" class="history-cache">
          <el-icon><Clock /></el-icon>
          已从本地缓存恢复 {{ cacheText }}
        </span>
        <span v-else class="history-tip">会自动在后台同步最新记录</span>
      </div>

      <el-button class="history-create" type="primary" @click="createNewSession">
        <el-icon><Plus /></el-icon>
        新建对话
      </el-button>
    </div>

    <div v-loading="loading" class="history-body">
      <div v-if="!sessions.length" class="history-empty">
        <div class="history-empty-icon">💬</div>
        <div class="history-empty-title">还没有对话历史</div>
        <div class="history-empty-text">开始与 AI 对话，创建您的选品方案。对话记录会自动保存在这里。</div>
      </div>

      <div
        v-for="session in sessions"
        v-else
        :key="session.id"
        class="history-card"
      >
        <div class="history-card-top">
          <span class="history-card-title">{{ session.title || '未命名对话' }}</span>
          <div class="history-card-actions">
            <el-tag v-if="session.status === 'in_progress'" type="warning" effect="light" size="small">
              规划中
            </el-tag>
            <el-button
              class="history-delete"
              circle
              text
              :icon="Delete"
              @click.stop="emit('deleteSession', session)"
            />
          </div>
        </div>

        <button type="button" class="history-card-body" @click="openSession(session)">
          <div class="history-card-meta">
            <span>{{ session.sourceType || '自由规划' }}</span>
            <span>{{ session.createTime?.slice(0, 16).replace('T', ' ') || '-' }}</span>
          </div>
          <div class="history-card-footer">
            <span>Token {{ session.llmTotalTokens ?? 0 }}</span>
            <span>API {{ session.echotikApiCalls ?? 0 }}</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.history-rail {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.14), transparent 28%),
    linear-gradient(180deg, rgba(255, 252, 245, 0.98) 0%, rgba(255, 255, 255, 0.98) 22%, rgba(255, 255, 255, 0.98) 100%);
  box-shadow: 0 24px 50px rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.history-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 22px 20px 16px;
}

.history-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.history-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #b45309;
}

.history-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.1;
  font-weight: 800;
  color: #0f172a;
}

.history-subtitle {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.history-refresh {
  color: #475569;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.history-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.72);
  border-bottom: 1px solid rgba(15, 23, 42, 0.05);
  background: rgba(255, 248, 235, 0.74);
}

.history-status {
  min-width: 0;
}

.history-cache,
.history-tip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #92400e;
}

.history-tip {
  color: #64748b;
}

.history-create {
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  box-shadow: 0 12px 24px rgba(79, 70, 229, 0.22);
}

.history-body {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-empty {
  margin: auto 0;
  padding: 28px 18px;
  border: 1px dashed rgba(148, 163, 184, 0.5);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  text-align: center;
}

.history-empty-icon {
  font-size: 24px;
}

.history-empty-title {
  margin-top: 10px;
  font-size: 15px;
  font-weight: 700;
  color: #334155;
}

.history-empty-text {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.history-card {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(255, 251, 235, 0.56) 100%);
  padding: 14px;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.history-card:hover {
  transform: translateY(-2px);
  border-color: rgba(245, 158, 11, 0.34);
  box-shadow: 0 18px 28px rgba(15, 23, 42, 0.08);
}

.history-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.history-card-title {
  flex: 1;
  font-size: 14px;
  line-height: 1.6;
  font-weight: 700;
  color: #0f172a;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.history-card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.history-delete {
  color: #94a3b8;
}

.history-delete:hover {
  color: #dc2626;
  background: rgba(254, 242, 242, 0.92);
}

.history-card-body {
  width: 100%;
  margin-top: 12px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.history-card-meta,
.history-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
}

.history-card-footer {
  margin-top: 10px;
  color: #475569;
}

@media (max-width: 1280px) {
  .history-rail {
    width: 304px;
  }
}

@media (max-width: 960px) {
  .history-rail {
    width: 100%;
  }
}
</style>
