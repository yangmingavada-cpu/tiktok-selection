<template>
  <el-container class="main-layout">
    <el-aside
      :width="collapsed ? '64px' : '248px'"
      class="sidebar"
      :class="{ 'sidebar--collapsed': collapsed }"
    >
      <div class="logo">
        <div class="logo-mark">AI</div>
        <div v-show="!collapsed" class="logo-copy">
          <h3>选品系统</h3>
          <p>Selection Workspace</p>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <el-menu-item index="/new">
          <el-icon><MagicStick /></el-icon>
          <template #title>新建选品</template>
        </el-menu-item>
        <el-menu-item index="/sessions">
          <el-icon><Document /></el-icon>
          <template #title>选品记录</template>
        </el-menu-item>
        <el-menu-item index="/plans">
          <el-icon><FolderOpened /></el-icon>
          <template #title>方案库</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button
            text
            class="collapse-toggle"
            :title="collapsed ? '展开侧栏' : '收起侧栏'"
            @click="toggleCollapsed"
          >
            <el-icon :size="18">
              <Expand v-if="collapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
          <span class="header-title">AI 选品工作区</span>
        </div>
        <div class="header-right">
          <span class="user-info">{{ userStore.name || userStore.email }}</span>
          <el-tag size="small" type="info">{{ userStore.tierName }}</el-tag>
          <el-button text @click="userStore.logout()">退出</el-button>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  Document,
  Expand,
  Fold,
  FolderOpened,
  HomeFilled,
  MagicStick,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const SIDEBAR_COLLAPSED_KEY = 'sidebar-collapsed'
const collapsed = ref(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1')

function toggleCollapsed() {
  collapsed.value = !collapsed.value
}

watch(collapsed, (v) => {
  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, v ? '1' : '0')
})

onMounted(() => {
  if (!userStore.userId) {
    userStore.fetchProfile()
  }
})
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(255, 244, 214, 0.8) 0%, rgba(255, 255, 255, 0) 28%),
    linear-gradient(180deg, #fffdfa 0%, #f8fafc 100%);
}

.sidebar {
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.18), transparent 24%),
    linear-gradient(180deg, #182230 0%, #243447 100%);
  padding: 22px 14px 18px;
  overflow-x: hidden;
  overflow-y: auto;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.04);
  transition: width 0.25s ease;
}

.sidebar--collapsed {
  padding-inline: 8px;
}

.sidebar--collapsed .logo {
  justify-content: center;
  padding: 10px 0 24px;
}

/* 折叠态：el-menu 自带 collapse 宽度 64px，这里覆盖掉默认 min-width 让它贴着 aside */
.sidebar--collapsed :deep(.el-menu--collapse) {
  width: 100%;
  border-right: none;
}

.sidebar--collapsed :deep(.el-menu-item) {
  padding: 0 !important;
  justify-content: center;
}

.sidebar--collapsed :deep(.el-menu-item .el-icon) {
  margin-right: 0;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px 24px;
  color: #f8fafc;
}

.logo-mark {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.04em;
  box-shadow: 0 10px 24px rgba(249, 115, 22, 0.35);
}

.logo-copy h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.logo-copy p {
  margin: 4px 0 0;
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(226, 232, 240, 0.76);
}

.sidebar-menu {
  border-right: none;
  background: transparent;
}

.sidebar-menu :deep(.el-menu) {
  border-right: none;
  background: transparent;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 50px;
  margin-bottom: 8px;
  border-radius: 16px;
  color: rgba(226, 232, 240, 0.92);
  background: transparent;
  font-weight: 600;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff7ed;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(251, 191, 36, 0.18) 0%, rgba(249, 115, 22, 0.12) 100%);
  color: #fff7ed;
  box-shadow: inset 0 0 0 1px rgba(251, 191, 36, 0.14);
}

.sidebar-menu :deep(.el-menu-item .el-icon) {
  margin-right: 10px;
  font-size: 17px;
}

.header {
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(14px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-inline: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.collapse-toggle {
  padding: 6px 8px;
  color: #0f172a;
  border-radius: 8px;
}

.collapse-toggle:hover {
  color: #0f172a;
  background: rgba(15, 23, 42, 0.05);
}

.header-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  font-size: 14px;
  color: #334155;
  font-weight: 600;
}

.main-content {
  padding: 24px;
}

@media (max-width: 960px) {
  .header-title {
    display: none;
  }

  .main-content {
    padding: 14px;
  }
}
</style>
