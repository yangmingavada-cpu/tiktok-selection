import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { STORAGE_KEY } from '@/constants'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
    title?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/layouts/main-layout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/dashboard.vue'),
      },
      {
        path: 'sessions',
        name: 'Sessions',
        component: () => import('@/views/selection-records.vue'),
      },
      {
        path: 'sessions/:id',
        name: 'SessionDetail',
        component: () => import('@/views/session-execution-detail.vue'),
        meta: { title: '选品详情' },
      },
      {
        path: 'plans',
        name: 'Plans',
        component: () => import('@/views/plans.vue'),
      },
      {
        path: 'new',
        name: 'NewSession',
        component: () => import('@/views/new-session.vue'),
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/admin-layout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/admin-dashboard.vue'),
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/admin-users.vue'),
      },
      {
        path: 'tiers',
        name: 'AdminTiers',
        component: () => import('@/views/admin/admin-tiers.vue'),
      },
      {
        path: 'api-keys',
        name: 'AdminApiKeys',
        component: () => import('@/views/admin/admin-api-keys.vue'),
      },
      {
        path: 'presets',
        name: 'AdminPresets',
        component: () => import('@/views/admin/admin-presets.vue'),
      },
      {
        path: 'llm-config',
        name: 'AdminLlmConfig',
        component: () => import('@/views/admin/admin-llm-config.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem(STORAGE_KEY.TOKEN)

  // Require auth by default unless explicitly set to false
  if (to.meta.requiresAuth !== false && !token) {
    return { name: 'Login' }
  }

  // Redirect authenticated users away from login/register
  if ((to.name === 'Login' || to.name === 'Register') && token) {
    return { path: '/' }
  }

  // Admin role check
  if (to.meta.requiresAdmin) {
    const role = localStorage.getItem(STORAGE_KEY.USER_ROLE)
    if (role !== 'admin') {
      return { path: '/' }
    }
  }
})

export default router
