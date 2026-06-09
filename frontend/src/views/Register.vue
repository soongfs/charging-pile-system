<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { ArrowLeft, Car, CheckCircle2, PlugZap, UserPlus } from '@lucide/vue'
import { register, setPassword } from '../api'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const form = reactive({
  carId: '',
  userName: '',
  carCapacity: 60,
  password: '',
})

async function doRegister() {
  if (!form.carId || !form.userName || !form.password) {
    message.warning('请填写车辆 ID、用户名和密码')
    return
  }
  if (!form.carCapacity || form.carCapacity <= 0) {
    message.warning('电池容量必须大于 0')
    return
  }

  loading.value = true
  try {
    const carId = form.carId.trim()
    await register({ carId, userName: form.userName.trim(), carCapacity: Number(form.carCapacity) })
    await setPassword({ carId, password: form.password })
    localStorage.setItem('carId', carId)
    message.success('账号已创建')
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
      <div class="brand">
        <span class="brand-mark"><PlugZap :size="20" /></span>
        <div>
          <h1 class="brand-title">智能充电桩调度计费系统</h1>
          <p class="brand-subtitle">新车辆账号激活后可直接进入用户端。</p>
        </div>
      </div>

      <div>
        <p class="auth-eyebrow"><CheckCircle2 :size="15" /> Account Setup</p>
        <h2 class="auth-title">创建车辆账号，录入容量，并完成密码激活。</h2>
        <p class="auth-copy">
          后端会校验车辆 ID 唯一性和电池容量，注册成功后自动设置密码并进入用户工作台。
        </p>
      </div>
    </section>

    <section class="auth-form-panel">
      <div class="panel-heading">
        <div>
          <p class="section-eyebrow muted"><Car :size="15" /> Vehicle Profile</p>
          <h2 class="panel-title">注册车辆</h2>
          <p class="panel-note">车辆 ID 是登录和账单查询的唯一标识。</p>
        </div>
      </div>

      <n-form @submit.prevent="doRegister">
        <n-form-item label="车辆 ID">
          <n-input v-model:value.trim="form.carId" placeholder="例如 京A001 或 car001" clearable />
        </n-form-item>
        <n-form-item label="用户名">
          <n-input v-model:value.trim="form.userName" placeholder="车主或使用人名称" clearable />
        </n-form-item>
        <n-form-item label="电池容量 kWh">
          <n-input-number v-model:value="form.carCapacity" :min="1" :precision="1" style="width: 100%" />
        </n-form-item>
        <n-form-item label="登录密码">
          <n-input v-model:value="form.password" type="password" placeholder="设置密码" show-password-on="click" />
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="doRegister">
          <template #icon><UserPlus :size="16" /></template>
          注册并进入
        </n-button>
      </n-form>

      <n-divider />

      <n-button tertiary block @click="router.push('/')">
        <template #icon><ArrowLeft :size="16" /></template>
        返回登录
      </n-button>
    </section>
  </main>
</template>
