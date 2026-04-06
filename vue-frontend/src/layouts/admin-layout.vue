<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <h3>管理后台</h3>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#1a1a2e"
        text-color="#a0aec0"
        active-text-color="#63b3ed"
      >
        <el-menu-item index="/admin">
          <el-icon><DataAnalysis /></el-icon>
          <span>运营看板</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/tiers">
          <el-icon><Medal /></el-icon>
          <span>等级配置</span>
        </el-menu-item>
        <el-menu-item index="/admin/api-keys">
          <el-icon><Key /></el-icon>
          <span>API 密钥池</span>
        </el-menu-item>
        <el-menu-item index="/admin/presets">
          <el-icon><Tickets /></el-icon>
          <span>预设套餐</span>
        </el-menu-item>
        <el-menu-item index="/admin/llm-config">
          <el-icon><Setting /></el-icon>
          <span>LLM 配置</span>
        </el-menu-item>
        <el-divider />
        <el-menu-item index="/">
          <el-icon><Back /></el-icon>
          <span>返回主站</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ pageTitle }}</span>
        <div class="header-right">
          <el-tag type="danger" size="small">管理员</el-tag>
          <span class="user-info">{{ userStore.name || userStore.email }}</span>
          <el-button text @click="userStore.logout()">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, User, Medal, Key, Tickets, Setting, Back } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const PAGE_TITLES: Record<string, string> = {
  '/admin': '运营看板',
  '/admin/users': '用户管理',
  '/admin/tiers': '等级配置',
  '/admin/api-keys': 'API 密钥池',
  '/admin/presets': '预设套餐',
  '/admin/llm-config': 'LLM 配置',
}

const pageTitle = computed(() => PAGE_TITLES[route.path] ?? '管理后台')
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.sidebar {
  background-color: #1a1a2e;
  overflow-y: auto;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  border-bottom: 1px solid #2d3748;
}

.logo h3 {
  margin: 0;
  font-size: 16px;
  letter-spacing: 1px;
}

.header {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  font-size: 14px;
  color: #606266;
}
</style>
