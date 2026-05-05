import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { TIMEOUT, STORAGE_KEY } from '@/constants'

const request = axios.create({
  baseURL: '/api',
  timeout: TIMEOUT.DEFAULT,
})

let authExpiredHandled = false
function handleAuthExpired() {
  if (authExpiredHandled) return
  authExpiredHandled = true
  ElMessage.warning('登录已失效，请重新登录')
  localStorage.removeItem(STORAGE_KEY.TOKEN)
  localStorage.removeItem(STORAGE_KEY.USER_ROLE)
  router.push('/login').finally(() => {
    setTimeout(() => { authExpiredHandled = false }, 1500)
  })
}

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(STORAGE_KEY.TOKEN)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    // blob 下载（如导出 Excel）直接返回二进制数据，不做业务 code 检查
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const res = response.data
    if (res.code !== '00000') {
      if (res.code === 'A0230') {
        handleAuthExpired()
      } else {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      handleAuthExpired()
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default request
