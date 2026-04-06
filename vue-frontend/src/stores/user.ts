import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, register as registerApi, getUserProfile } from '@/api/auth'
import type { LoginRequest, RegisterRequest } from '@/api/auth'
import router from '@/router'
import { STORAGE_KEY } from '@/constants'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(STORAGE_KEY.TOKEN) || '')
  const userId = ref('')
  const email = ref('')
  const name = ref('')
  const role = ref(localStorage.getItem(STORAGE_KEY.USER_ROLE) || '')
  const tierName = ref('')

  function setAuth(t: string, r: string) {
    token.value = t
    role.value = r
    localStorage.setItem(STORAGE_KEY.TOKEN, t)
    localStorage.setItem(STORAGE_KEY.USER_ROLE, r)
  }

  function clearAuth() {
    token.value = ''
    userId.value = ''
    email.value = ''
    name.value = ''
    role.value = ''
    tierName.value = ''
    localStorage.removeItem(STORAGE_KEY.TOKEN)
    localStorage.removeItem(STORAGE_KEY.USER_ROLE)
  }

  async function login(data: LoginRequest) {
    const res = await loginApi(data)
    const d = res.data
    setAuth(d.token, d.role)
    userId.value = d.userId
    email.value = d.email
    name.value = d.name
    tierName.value = d.tierName
    router.push('/')
  }

  async function register(data: RegisterRequest) {
    const res = await registerApi(data)
    const d = res.data
    setAuth(d.token, d.role)
    userId.value = d.userId
    email.value = d.email
    name.value = d.name
    tierName.value = d.tierName
    router.push('/')
  }

  async function fetchProfile() {
    const res = await getUserProfile()
    const d = res.data
    userId.value = d.id
    email.value = d.email
    name.value = d.name
    role.value = d.role
    tierName.value = d.tierDisplayName
    localStorage.setItem(STORAGE_KEY.USER_ROLE, d.role)
  }

  function logout() {
    clearAuth()
    router.push('/login')
  }

  return { token, userId, email, name, role, tierName, login, register, fetchProfile, logout }
})
