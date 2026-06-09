<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
  Activity,
  CircleDollarSign,
  Gauge,
  List,
  LogOut,
  Play,
  PlugZap,
  Power,
  PowerOff,
  RefreshCcw,
  Settings,
  Shield,
  Zap,
} from '@lucide/vue'
import {
  powerOff,
  powerOn,
  queryPileState,
  queryQueueState,
  setParameters,
  startChargingPile,
} from '../api'

const router = useRouter()
const message = useMessage()
const activeTab = ref('piles')
const piles = ref([])
const fastQueue = ref([])
const slowQueue = ref([])
const loading = reactive({
  piles: false,
  queue: false,
  pricing: false,
})
const pricing = reactive({
  peakPrice: 1.2,
  normalPrice: 0.8,
  valleyPrice: 0.6,
  serviceFeeRate: 0.2,
})

let timer = null

const navItems = [
  { key: 'piles', label: '充电桩状态', icon: Gauge },
  { key: 'queues', label: '等待队列', icon: List },
  { key: 'pricing', label: '计费参数', icon: Settings },
]

const workingLabel = {
  idle: '空闲',
  running: '待服务',
  charging: '充电中',
  fault: '故障',
  off: '关机',
}

const powerLabel = {
  on: '开机',
  off: '关机',
}

const totalCapacity = computed(() => piles.value.reduce((sum, pile) => sum + Number(pile.totalCapacity || 0), 0))
const totalTimes = computed(() => piles.value.reduce((sum, pile) => sum + Number(pile.totalChargeNum || 0), 0))
const activePileCount = computed(() => piles.value.filter((pile) => pile.powerState === 'on').length)
const queueCount = computed(() => fastQueue.value.length + slowQueue.value.length)

function statusClass(state) {
  return `status-pill status-${state || 'off'}`
}

function fmtNumber(value, digits = 2) {
  return Number(value || 0).toFixed(digits)
}

function fmtDuration(seconds) {
  const total = Math.max(0, Number(seconds || 0))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  return h > 0 ? `${h}h ${m}m` : `${m}m`
}

function fmtTime(value) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

async function loadPiles(silent = false) {
  loading.piles = true
  try {
    const result = await queryPileState()
    piles.value = result || []
  } catch (error) {
    if (!silent) message.error(error.message)
  } finally {
    loading.piles = false
  }
}

async function loadQueues(silent = false) {
  loading.queue = true
  try {
    const [fast, slow] = await Promise.all([queryQueueState('fast'), queryQueueState('slow')])
    fastQueue.value = fast || []
    slowQueue.value = slow || []
  } catch (error) {
    if (!silent) message.error(error.message)
  } finally {
    loading.queue = false
  }
}

async function act(action, pileId) {
  try {
    if (action === 'powerOn') await powerOn({ pileId })
    if (action === 'start') await startChargingPile({ pileId })
    if (action === 'powerOff') await powerOff({ pileId })
    message.success('操作已执行')
    await loadPiles(true)
  } catch (error) {
    message.error(error.message)
  }
}

async function savePricing() {
  loading.pricing = true
  try {
    await setParameters({
      peakPrice: Number(pricing.peakPrice),
      normalPrice: Number(pricing.normalPrice),
      valleyPrice: Number(pricing.valleyPrice),
      serviceFeeRate: Number(pricing.serviceFeeRate),
    })
    message.success('计费参数已保存')
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.pricing = false
  }
}

function queueRows(rows, modeLabel) {
  return rows.map((row, index) => ({
    idx: index + 1,
    carId: row.carId,
    requestAmount: `${fmtNumber(row.requestAmount, 1)} kWh`,
    requestTime: fmtTime(row.requestTime),
    state: workingLabel[row.carState] || row.carState,
    pileId: row.pileId || '-',
    modeLabel,
  }))
}

onMounted(async () => {
  await Promise.all([loadPiles(true), loadQueues(true)])
  timer = window.setInterval(() => {
    loadPiles(true)
    loadQueues(true)
  }, 5000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark"><Shield :size="20" /></span>
        <div>
          <h1 class="brand-title">管理员工作台</h1>
          <p class="brand-subtitle">充电桩运行、等待队列和计费参数</p>
        </div>
      </div>
      <n-space>
        <n-button tertiary @click="router.push('/user')">用户端</n-button>
        <n-button quaternary @click="router.push('/')">
          <template #icon><LogOut :size="16" /></template>
          返回入口
        </n-button>
      </n-space>
    </header>

    <main class="page">
      <div class="workspace">
        <aside class="side-panel">
          <div class="side-header">
            <h2 class="side-title">运行管理</h2>
            <p class="side-meta">{{ activePileCount }}/{{ piles.length || 5 }} 台开机，{{ queueCount }} 辆等待</p>
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
              <span class="summary-label"><Power :size="15" /> 开机数量</span>
              <strong class="summary-value">{{ activePileCount }}</strong>
              <span class="summary-sub">当前可管理桩</span>
            </article>
            <article class="summary-card">
              <span class="summary-label"><Activity :size="15" /> 充电次数</span>
              <strong class="summary-value">{{ totalTimes }}</strong>
              <span class="summary-sub">累计服务</span>
            </article>
            <article class="summary-card">
              <span class="summary-label"><Zap :size="15" /> 累计电量</span>
              <strong class="summary-value">{{ fmtNumber(totalCapacity, 2) }}</strong>
              <span class="summary-sub">kWh</span>
            </article>
            <article class="summary-card">
              <span class="summary-label"><List :size="15" /> 队列车辆</span>
              <strong class="summary-value">{{ queueCount }}</strong>
              <span class="summary-sub">快充 {{ fastQueue.length }}，慢充 {{ slowQueue.length }}</span>
            </article>
          </div>

          <section v-if="activeTab === 'piles'" class="tool-panel">
            <div class="tool-header">
              <h2 class="tool-title"><PlugZap :size="18" /> 充电桩状态</h2>
              <n-button tertiary :loading="loading.piles" @click="loadPiles(false)">
                <template #icon><RefreshCcw :size="16" /></template>
                刷新
              </n-button>
            </div>
            <div class="tool-body">
              <div class="pile-list">
                <article v-for="pile in piles" :key="pile.id" class="pile-item">
                  <div class="pile-id">{{ pile.id }}</div>
                  <div class="pile-main">
                    <h3 class="pile-title">{{ pile.type === 'fast' ? '快充桩' : '慢充桩' }} {{ pile.type === 'fast' ? '120kW' : '60kW' }}</h3>
                    <div class="pile-meta">
                      <span :class="statusClass(pile.powerState)">{{ powerLabel[pile.powerState] || pile.powerState }}</span>
                      <span :class="statusClass(pile.workingState)">{{ workingLabel[pile.workingState] || pile.workingState }}</span>
                      <span class="status-pill status-idle">{{ pile.totalChargeNum }} 次</span>
                      <span class="status-pill status-dispatched">{{ fmtNumber(pile.totalCapacity, 2) }} kWh</span>
                      <span class="status-pill status-off">{{ fmtDuration(pile.totalChargeTime) }}</span>
                    </div>
                  </div>
                  <div class="pile-actions">
                    <n-tooltip trigger="hover">
                      <template #trigger>
                        <n-button circle secondary :disabled="pile.powerState === 'on'" @click="act('powerOn', pile.id)">
                          <template #icon><Power :size="16" /></template>
                        </n-button>
                      </template>
                      开机
                    </n-tooltip>
                    <n-tooltip trigger="hover">
                      <template #trigger>
                        <n-button circle secondary :disabled="pile.powerState !== 'on' || pile.workingState === 'charging'" @click="act('start', pile.id)">
                          <template #icon><Play :size="16" /></template>
                        </n-button>
                      </template>
                      置为待服务
                    </n-tooltip>
                    <n-tooltip trigger="hover">
                      <template #trigger>
                        <n-button circle secondary type="error" :disabled="pile.workingState === 'charging'" @click="act('powerOff', pile.id)">
                          <template #icon><PowerOff :size="16" /></template>
                        </n-button>
                      </template>
                      关机
                    </n-tooltip>
                  </div>
                </article>
              </div>
            </div>
          </section>

          <section v-if="activeTab === 'queues'" class="tool-panel">
            <div class="tool-header">
              <h2 class="tool-title"><List :size="18" /> 等待队列</h2>
              <n-button tertiary :loading="loading.queue" @click="loadQueues(false)">
                <template #icon><RefreshCcw :size="16" /></template>
                刷新
              </n-button>
            </div>
            <div class="tool-body">
              <div class="queue-columns">
                <div class="data-panel">
                  <div class="tool-header">
                    <h3 class="tool-title"><Zap :size="17" /> 快充队列</h3>
                    <span class="status-pill status-dispatched">{{ fastQueue.length }} 辆</span>
                  </div>
                  <div class="tool-body">
                    <n-data-table
                      size="small"
                      :bordered="false"
                      :columns="[
                        { title: '#', key: 'idx', width: 48 },
                        { title: '车辆', key: 'carId' },
                        { title: '电量', key: 'requestAmount' },
                        { title: '状态', key: 'state' },
                        { title: '桩号', key: 'pileId' },
                      ]"
                      :data="queueRows(fastQueue, '快充')"
                    />
                  </div>
                </div>

                <div class="data-panel">
                  <div class="tool-header">
                    <h3 class="tool-title"><Gauge :size="17" /> 慢充队列</h3>
                    <span class="status-pill status-idle">{{ slowQueue.length }} 辆</span>
                  </div>
                  <div class="tool-body">
                    <n-data-table
                      size="small"
                      :bordered="false"
                      :columns="[
                        { title: '#', key: 'idx', width: 48 },
                        { title: '车辆', key: 'carId' },
                        { title: '电量', key: 'requestAmount' },
                        { title: '状态', key: 'state' },
                        { title: '桩号', key: 'pileId' },
                      ]"
                      :data="queueRows(slowQueue, '慢充')"
                    />
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section v-if="activeTab === 'pricing'" class="tool-panel">
            <div class="tool-header">
              <h2 class="tool-title"><CircleDollarSign :size="18" /> 计费参数</h2>
              <n-button type="primary" :loading="loading.pricing" @click="savePricing">
                保存参数
              </n-button>
            </div>
            <div class="tool-body">
              <div class="tool-grid">
                <div class="field-stack">
                  <span class="field-label">峰时电价 元/kWh</span>
                  <n-input-number v-model:value="pricing.peakPrice" :min="0" :step="0.1" :precision="2" style="width: 100%" />
                </div>
                <div class="field-stack">
                  <span class="field-label">平时电价 元/kWh</span>
                  <n-input-number v-model:value="pricing.normalPrice" :min="0" :step="0.1" :precision="2" style="width: 100%" />
                </div>
                <div class="field-stack">
                  <span class="field-label">谷时电价 元/kWh</span>
                  <n-input-number v-model:value="pricing.valleyPrice" :min="0" :step="0.1" :precision="2" style="width: 100%" />
                </div>
              </div>
              <div class="tool-grid two">
                <div class="field-stack">
                  <span class="field-label">服务费率 元/kWh</span>
                  <n-input-number v-model:value="pricing.serviceFeeRate" :min="0" :step="0.1" :precision="2" style="width: 100%" />
                </div>
                <div class="info-table">
                  <div class="info-row">
                    <span class="info-label">峰时</span>
                    <span class="info-value">11:00-15:00，18:00-22:00</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">平时</span>
                    <span class="info-value">08:00-11:00，15:00-18:00</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">谷时</span>
                    <span class="info-value">22:00-08:00</span>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </section>
      </div>
    </main>
  </div>
</template>
