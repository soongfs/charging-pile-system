<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { Car, LogIn, Shield, PlugZap, Zap } from '@lucide/vue'
import { login } from '../api'

const router = useRouter()
const message = useMessage()
const carId = ref(localStorage.getItem('carId') || '')
const password = ref('')
const loading = ref(false)

async function doLogin() {
  if (!carId.value || !password.value) {
    message.warning('请输入车辆 ID 和密码')
    return
  }

  loading.value = true
  try {
    await login({ carId: carId.value.trim(), password: password.value })
    localStorage.setItem('carId', carId.value.trim())
    router.push('/user')
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div>
        <div class="brand">
          <span class="brand-mark"><PlugZap :size="20" /></span>
          <div>
            <h1 class="brand-title">智能充电桩调度计费系统</h1>
            <p class="brand-subtitle">车辆排队、充电过程、账单结算一体化控制台</p>
          </div>
        </div>
      </div>

      <div>
        <p class="auth-eyebrow"><Zap :size="15" /> Charging Operations</p>
        <h2 class="auth-title">从排队到结算，所有充电动作都在同一个工作台完成。</h2>
        <p class="auth-copy">
          用户端聚焦车辆申请、队列状态、实时充电和账单查询；管理员端聚合充电桩运行、队列监控和电价配置。
        </p>
        <div class="auth-metrics">
          <div class="metric-box">
            <span class="metric-value">5</span>
            <span class="metric-label">默认充电桩</span>
          </div>
          <div class="metric-box">
            <span class="metric-value">17</span>
            <span class="metric-label">REST 端点</span>
          </div>
          <div class="metric-box">
            <span class="metric-value">120kW</span>
            <span class="metric-label">快充功率</span>
          </div>
        </div>
      </div>
    </section>

    <section class="auth-form-panel">
      <div class="panel-heading">
        <div>
          <p class="section-eyebrow muted"><Car :size="15" /> User Access</p>
          <h2 class="panel-title">用户登录</h2>
          <p class="panel-note">使用车辆 ID 进入用户端工作台。</p>
        </div>
      </div>

      <n-form @submit.prevent="doLogin">
        <n-form-item label="车辆 ID">
          <n-input v-model:value.trim="carId" placeholder="例如 car001" clearable />
        </n-form-item>
        <n-form-item label="登录密码">
          <n-input
            v-model:value="password"
            type="password"
            placeholder="输入密码"
            show-password-on="click"
            @keyup.enter="doLogin"
          />
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="doLogin">
          <template #icon><LogIn :size="16" /></template>
          登录
        </n-button>
      </n-form>

      <n-divider />

      <n-space vertical :size="10">
        <n-button secondary block @click="router.push('/register')">
          <template #icon><Car :size="16" /></template>
          注册新车辆
        </n-button>
        <n-button tertiary block @click="router.push('/admin')">
          <template #icon><Shield :size="16" /></template>
          管理员工作台
        </n-button>
      </n-space>
    </section>
  </main>
</template>
