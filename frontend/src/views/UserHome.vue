<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
  BatteryCharging,
  CalendarDays,
  Car,
  CircleDollarSign,
  Clock,
  Gauge,
  List,
  LogOut,
  Play,
  PlugZap,
  Receipt,
  RefreshCcw,
  Square,
  Zap,
} from '@lucide/vue'
import {
  endCharging,
  modifyAmount,
  modifyMode,
  queryCarState,
  queryChargingState,
  requestBill,
  requestDetailedList,
  startCharging,
  submitRequest,
} from '../api'

const router = useRouter()
const message = useMessage()
const carId = ref(localStorage.getItem('carId') || '')
const activeTab = ref('request')
const requestState = ref(null)
const progress = ref(null)
const bills = ref([])
const billDetail = ref(null)
const detailRecords = ref([])
const billDate = ref(new Date().toISOString().slice(0, 10))
const loading = reactive({
  request: false,
  state: false,
  start: false,
  end: false,
  bill: false,
})
const requestForm = reactive({
  requestMode: 0,
  requestAmount: 40,
})
const editForm = reactive({
  amount: 40,
  mode: 0,
})

let timer = null

const modeOptions = [
  { label: '慢充 60kW', value: 0 },
  { label: '快充 120kW', value: 1 },
]

const navItems = [
  { key: 'request', label: '充电申请', icon: Zap },
  { key: 'queue', label: '排队状态', icon: List },
  { key: 'charging', label: '充电过程', icon: BatteryCharging },
  { key: 'bill', label: '账单查询', icon: Receipt },
]

const stateLabel = {
  waiting: '等待中',
  dispatched: '已分配',
  charging: '充电中',
  done: '已完成',
  cancelled: '已取消',
}

const stateText = computed(() => stateLabel[requestState.value?.carState] || '暂无申请')
const assignedPile = computed(() => requestState.value?.pileId || progress.value?.pileId || '-')
const chargedAmount = computed(() => progress.value?.chargedAmount || 0)
const totalFee = computed(() => progress.value?.totalFee || 0)
const progressPercent = computed(() => Math.min(100, Math.max(0, progress.value?.progressPercent || 0)))
const canModifyMode = computed(() => requestState.value?.carState === 'waiting')
const canStart = computed(() => Boolean(requestState.value?.canStart || requestState.value?.carState === 'dispatched'))
const canEnd = computed(() => Boolean(progress.value))

function statusClass(state) {
  return `status-pill status-${state || 'off'}`
}

function fmtNumber(value, digits = 2) {
  return Number(value || 0).toFixed(digits)
}

function fmtMoney(value) {
  return `¥${Number(value || 0).toFixed(3)}`
}

function fmtSeconds(seconds) {
  const total = Math.max(0, Number(seconds || 0))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = Math.floor(total % 60)
  return h > 0 ? `${h}h ${m}m ${s}s` : `${m}m ${s}s`
}

function fmtTime(value) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function ensureCarId() {
  if (!carId.value) {
    router.push('/')
    return false
  }
  return true
}

function logout() {
  localStorage.removeItem('carId')
  router.push('/')
}

async function refreshState(silent = false) {
  if (!ensureCarId()) return
  loading.state = true
  try {
    requestState.value = await queryCarState(carId.value)
    if (requestState.value?.requestAmount) editForm.amount = requestState.value.requestAmount
    if (requestState.value?.requestMode !== undefined) editForm.mode = requestState.value.requestMode
  } catch (error) {
    requestState.value = null
    if (!silent) message.info(error.message)
  } finally {
    loading.state = false
  }
}

async function refreshProgress(silent = true) {
  if (!ensureCarId()) return
  try {
    progress.value = await queryChargingState(carId.value)
  } catch (error) {
    progress.value = null
    if (!silent) message.info(error.message)
  }
}

async function submit() {
  if (!ensureCarId()) return
  loading.request = true
  try {
    requestState.value = await submitRequest({
      carId: carId.value,
      requestAmount: Number(requestForm.requestAmount),
      requestMode: requestForm.requestMode,
    })
    editForm.amount = requestForm.requestAmount
    editForm.mode = requestForm.requestMode
    activeTab.value = requestState.value?.canStart ? 'charging' : 'queue'
    message.success('申请已提交')
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.request = false
  }
}

async function updateAmount() {
  try {
    await modifyAmount({ carId: carId.value, amount: Number(editForm.amount) })
    message.success('电量已更新')
    await refreshState(true)
  } catch (error) {
    message.error(error.message)
  }
}

async function updateMode() {
  try {
    await modifyMode({ carId: carId.value, mode: editForm.mode })
    message.success('模式已更新')
    await refreshState(true)
  } catch (error) {
    message.error(error.message)
  }
}

async function start() {
  loading.start = true
  try {
    await startCharging({ carId: carId.value })
    message.success('已开始充电')
    await refreshState(true)
    await refreshProgress(false)
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.start = false
  }
}

async function end() {
  loading.end = true
  try {
    await endCharging({ carId: carId.value })
    message.success('充电已结束')
    progress.value = null
    await refreshState(true)
    await loadBills()
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.end = false
  }
}

async function loadBills() {
  if (!ensureCarId()) return
  loading.bill = true
  try {
    bills.value = await requestBill(carId.value, billDate.value)
  } catch (error) {
    bills.value = []
    message.error(error.message)
  } finally {
    loading.bill = false
  }
}

async function showDetail(bill) {
  try {
    const detail = await requestDetailedList(bill.id)
    billDetail.value = detail.bill
    detailRecords.value = detail.records || []
  } catch (error) {
    message.error(error.message)
  }
}

onMounted(async () => {
  if (!ensureCarId()) return
  await refreshState(true)
  await refreshProgress(true)
  await loadBills()
  timer = window.setInterval(() => {
    refreshState(true)
    refreshProgress(true)
  }, 4000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark"><PlugZap :size="20" /></span>
        <div>
          <h1 class="brand-title">用户工作台</h1>
          <p class="brand-subtitle">{{ carId }}</p>
        </div>
      </div>
      <n-space>
        <n-button tertiary @click="router.push('/admin')">管理员端</n-button>
        <n-button quaternary @click="logout">
          <template #icon><LogOut :size="16" /></template>
          退出
        </n-button>
      </n-space>
    </header>

    <main class="page">
      <div class="workspace">
        <aside class="side-panel">
          <div class="side-header">
            <h2 class="side-title">车辆控制</h2>
            <p class="side-meta">状态：{{ stateText }}</p>
          </div>
          <nav class="side-nav">
            <button
              v-for="item in navItems"
              :key="item.key"
              class="nav-button"
              :class="{ active: activeTab === item.key }"
              @click="activeTab = item.key"
            >
              <component :is="item.icon" :size="17" />
              <span>{{ item.label }}</span>
            </button>
          </nav>
        </aside>

        <section class="content-stack">
          <div class="summary-grid">
            <article class="summary-card">
              <span class="summary-label"><Car :size="15" /> 当前车辆</span>
              <strong class="summary-value">{{ carId }}</strong>
              <span class="summary-sub">车辆 ID</span>
            </article>
            <article class="summary-card">
              <span class="summary-label"><List :size="15" /> 申请状态</span>
              <strong class="summary-value">{{ stateText }}</strong>
              <span class="summary-sub">前方 {{ requestState?.carNumberBeforePosition || 0 }} 辆</span>
            </article>
            <article class="summary-card">
              <span class="summary-label"><Gauge :size="15" /> 分配桩号</span>
              <strong class="summary-value">{{ assignedPile }}</strong>
              <span class="summary-sub">{{ requestState?.modeLabel || progress?.modeLabel || '-' }}</span>
            </article>
            <article class="summary-card">
              <span class="summary-label"><CircleDollarSign :size="15" /> 当前费用</span>
              <strong class="summary-value">{{ fmtMoney(totalFee) }}</strong>
              <span class="summary-sub">{{ fmtNumber(chargedAmount, 3) }} kWh</span>
            </article>
          </div>

          <section v-if="activeTab === 'request'" class="tool-panel">
            <div class="tool-header">
              <h2 class="tool-title"><Zap :size="18" /> 充电申请</h2>
              <n-button tertiary :loading="loading.state" @click="refreshState(false)">
                <template #icon><RefreshCcw :size="16" /></template>
                刷新
              </n-button>
            </div>
            <div class="tool-body">
              <div class="tool-grid">
                <div class="field-stack">
                  <span class="field-label">充电模式</span>
                  <n-select v-model:value="requestForm.requestMode" :options="modeOptions" />
                </div>
                <div class="field-stack">
                  <span class="field-label">申请电量 kWh</span>
                  <n-input-number v-model:value="requestForm.requestAmount" :min="1" :precision="1" style="width: 100%" />
                </div>
                <div class="field-stack">
                  <span class="field-label">操作</span>
                  <n-button type="primary" :loading="loading.request" @click="submit">
                    <template #icon><Zap :size="16" /></template>
                    提交申请
                  </n-button>
                </div>
              </div>

              <div v-if="requestState" class="info-table">
                <div class="info-row">
                  <span class="info-label">申请编号</span>
                  <span class="info-value">#{{ requestState.requestId }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">状态</span>
                  <span class="info-value">
                    <span :class="statusClass(requestState.carState)">{{ stateLabel[requestState.carState] || requestState.carState }}</span>
                  </span>
                </div>
                <div class="info-row">
                  <span class="info-label">申请时间</span>
                  <span class="info-value">{{ fmtTime(requestState.requestTime) }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">分配桩号</span>
                  <span class="info-value">{{ requestState.pileId || '-' }}</span>
                </div>
              </div>
              <n-empty v-else description="暂无充电申请" />
            </div>
          </section>

          <section v-if="activeTab === 'queue'" class="tool-panel">
            <div class="tool-header">
              <h2 class="tool-title"><List :size="18" /> 排队状态</h2>
              <n-button tertiary :loading="loading.state" @click="refreshState(false)">
                <template #icon><RefreshCcw :size="16" /></template>
                刷新
              </n-button>
            </div>
            <div class="tool-body">
              <div class="data-grid">
                <div class="info-table">
                  <div class="info-row">
                    <span class="info-label">车辆状态</span>
                    <span class="info-value">
                      <span :class="statusClass(requestState?.carState)">{{ stateText }}</span>
                    </span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">队列类型</span>
                    <span class="info-value">{{ requestState?.modeLabel || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">前方等待</span>
                    <span class="info-value">{{ requestState?.carNumberBeforePosition || 0 }} 辆</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">排队序号</span>
                    <span class="info-value">{{ requestState?.carPosition ?? '-' }}</span>
                  </div>
                </div>

                <div class="inline-controls">
                  <div class="tool-grid two">
                    <div class="field-stack">
                      <span class="field-label">电量 kWh</span>
                      <n-input-number v-model:value="editForm.amount" :min="1" :precision="1" style="width: 100%" />
                    </div>
                    <div class="field-stack">
                      <span class="field-label">模式</span>
                      <n-select v-model:value="editForm.mode" :options="modeOptions" :disabled="!canModifyMode" />
                    </div>
                  </div>
                  <div class="actions">
                    <n-button secondary @click="updateAmount">修改电量</n-button>
                    <n-button secondary :disabled="!canModifyMode" @click="updateMode">修改模式</n-button>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section v-if="activeTab === 'charging'" class="tool-panel">
            <div class="tool-header">
              <h2 class="tool-title"><BatteryCharging :size="18" /> 充电过程</h2>
              <n-button tertiary @click="refreshProgress(false)">
                <template #icon><RefreshCcw :size="16" /></template>
                刷新
              </n-button>
            </div>
            <div class="tool-body">
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: `${progressPercent}%` }" />
                <div class="progress-caption">{{ fmtNumber(progressPercent, 1) }}%</div>
              </div>

              <div class="data-grid">
                <div class="info-table">
                  <div class="info-row">
                    <span class="info-label">充电桩</span>
                    <span class="info-value">{{ progress?.pileId || requestState?.pileId || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">功率</span>
                    <span class="info-value">{{ progress?.powerKw ? `${progress.powerKw} kW` : '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">目标电量</span>
                    <span class="info-value">{{ progress?.requestAmount || requestState?.requestAmount || '-' }} kWh</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">已充电量</span>
                    <span class="info-value">{{ fmtNumber(progress?.chargedAmount, 3) }} kWh</span>
                  </div>
                </div>

                <div class="info-table">
                  <div class="info-row">
                    <span class="info-label">开始时间</span>
                    <span class="info-value">{{ fmtTime(progress?.startTime) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">已用时</span>
                    <span class="info-value">{{ fmtSeconds(progress?.elapsedSeconds) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">充电费</span>
                    <span class="info-value amount">{{ fmtMoney(progress?.chargeFee) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">服务费</span>
                    <span class="info-value amount">{{ fmtMoney(progress?.serviceFee) }}</span>
                  </div>
                </div>
              </div>

              <div class="actions">
                <n-button type="primary" :loading="loading.start" :disabled="!canStart || canEnd" @click="start">
                  <template #icon><Play :size="16" /></template>
                  开始充电
                </n-button>
                <n-button type="error" secondary :loading="loading.end" :disabled="!canEnd" @click="end">
                  <template #icon><Square :size="16" /></template>
                  结束充电
                </n-button>
              </div>
            </div>
          </section>

          <section v-if="activeTab === 'bill'" class="tool-panel">
            <div class="tool-header">
              <h2 class="tool-title"><Receipt :size="18" /> 账单查询</h2>
              <div class="actions">
                <n-input v-model:value="billDate" style="width: 160px" placeholder="YYYY-MM-DD">
                  <template #prefix><CalendarDays :size="15" /></template>
                </n-input>
                <n-button type="primary" :loading="loading.bill" @click="loadBills">
                  <template #icon><RefreshCcw :size="16" /></template>
                  查询
                </n-button>
              </div>
            </div>
            <div class="tool-body">
              <div v-if="bills.length" class="pile-list">
                <article v-for="bill in bills" :key="bill.id" class="pile-item">
                  <div class="pile-id">{{ bill.pileId }}</div>
                  <div class="pile-main">
                    <h3 class="pile-title">账单 #{{ bill.id }}</h3>
                    <div class="pile-meta">
                      <span class="status-pill status-idle">{{ fmtNumber(bill.chargeAmount, 3) }} kWh</span>
                      <span class="status-pill status-running">{{ fmtSeconds(bill.chargeDuration) }}</span>
                      <span class="status-pill status-dispatched amount">{{ fmtMoney(bill.totalFee) }}</span>
                    </div>
                  </div>
                  <div class="pile-actions">
                    <n-button tertiary @click="showDetail(bill)">详单</n-button>
                  </div>
                </article>
              </div>
              <n-empty v-else description="暂无账单" />

              <div v-if="billDetail" class="info-table">
                <div class="info-row">
                  <span class="info-label">账单日期</span>
                  <span class="info-value">{{ billDetail.date }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">充电费</span>
                  <span class="info-value amount">{{ fmtMoney(billDetail.totalChargeFee) }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">服务费</span>
                  <span class="info-value amount">{{ fmtMoney(billDetail.totalServiceFee) }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">合计</span>
                  <span class="info-value amount">{{ fmtMoney(billDetail.totalFee) }}</span>
                </div>
              </div>

              <div v-if="detailRecords.length" class="table-wrap">
                <n-data-table
                  size="small"
                  :bordered="false"
                  :columns="[
                    { title: '记录', key: 'id' },
                    { title: '开始时间', key: 'startTime' },
                    { title: '结束时间', key: 'endTime' },
                    { title: '电量', key: 'chargeAmount' },
                    { title: '费用', key: 'total' },
                  ]"
                  :data="detailRecords.map((r) => ({
                    id: `#${r.id}`,
                    startTime: fmtTime(r.startTime),
                    endTime: fmtTime(r.endTime),
                    chargeAmount: `${fmtNumber(r.chargeAmount, 3)} kWh`,
                    total: fmtMoney((r.chargeFee || 0) + (r.serviceFee || 0)),
                  }))"
                />
              </div>
            </div>
          </section>
        </section>
      </div>
    </main>
  </div>
</template>
