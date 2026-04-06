<template>
  <div class="register-container">
    <el-card class="register-card" shadow="hover">
      <template #header>
        <h2 class="register-title">TikTok AI 选品系统</h2>
        <p class="register-subtitle">注册新账号</p>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="name">
          <el-input v-model="form.name" placeholder="昵称（可选）" prefix-icon="User" />
        </el-form-item>

        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="邮箱" prefix-icon="Message" />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码 (6-64位)"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleRegister"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" class="register-btn" @click="handleRegister">
            注 册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-footer">
        已有账号？
        <router-link to="/login">立即登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, reactive } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = shallowRef(false)

const form = reactive({
  name: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度为6-64位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: Function) => {
        if (value !== form.password) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.register({
      email: form.email,
      password: form.password,
      name: form.name || undefined,
    })
    ElMessage.success('注册成功')
  } catch (e: any) {
    // Error already handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.register-card {
  width: 420px;
}

.register-title {
  margin: 0;
  text-align: center;
  font-size: 24px;
  color: #303133;
}

.register-subtitle {
  margin: 8px 0 0;
  text-align: center;
  color: #909399;
  font-size: 14px;
}

.register-btn {
  width: 100%;
}

.register-footer {
  text-align: center;
  color: #909399;
  font-size: 14px;
}

.register-footer a {
  color: #409eff;
  text-decoration: none;
}
</style>
