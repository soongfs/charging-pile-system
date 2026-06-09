import axios from 'axios'

const http = axios.create({
  baseURL: '',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  if (config.url?.includes('/api/') && config.method === 'get') {
    config.params = Object.fromEntries(
      Object.entries(config.params || {}).filter(([, value]) => value !== undefined && value !== null && value !== '')
    )
  }
  return config
})

const unwrap = (request) =>
  request.then((response) => {
    const body = response.data

    if (body?.code === 0) {
      return body.data
    }

    throw new Error(body?.message || '请求失败')
  })

export const login = (data) => unwrap(http.post('/api/user/login', data))
export const register = (data) => unwrap(http.post('/api/user/register', data))
export const setPassword = (data) => unwrap(http.post('/api/user/set-password', data))
export const submitRequest = (data) => unwrap(http.post('/api/charging/request', data))
export const modifyAmount = (data) => unwrap(http.put('/api/charging/amount', data))
export const modifyMode = (data) => unwrap(http.put('/api/charging/mode', data))
export const queryCarState = (carId) => unwrap(http.get(`/api/charging/state/${carId}`))
export const startCharging = (data) => unwrap(http.post('/api/charging/start', data))
export const queryChargingState = (carId) => unwrap(http.get(`/api/charging/progress/${carId}`))
export const endCharging = (data) => unwrap(http.post('/api/charging/end', data))
export const requestBill = (carId, date) => unwrap(http.get(`/api/bill/${carId}`, { params: { date } }))
export const requestDetailedList = (billId) => unwrap(http.get(`/api/bill/detail/${billId}`))
export const powerOn = (data) => unwrap(http.post('/api/admin/pile/power-on', data))
export const setParameters = (data) => unwrap(http.put('/api/admin/pricing', data))
export const startChargingPile = (data) => unwrap(http.post('/api/admin/pile/start', data))
export const powerOff = (data) => unwrap(http.post('/api/admin/pile/power-off', data))
export const queryPileState = (pileId) => unwrap(http.get('/api/admin/pile/state', { params: { pileId } }))
export const queryQueueState = (type) => unwrap(http.get('/api/admin/queue/state', { params: { type } }))

export default {
  login,
  register,
  setPassword,
  submitRequest,
  modifyAmount,
  modifyMode,
  queryCarState,
  startCharging,
  queryChargingState,
  endCharging,
  requestBill,
  requestDetailedList,
  powerOn,
  setParameters,
  startChargingPile,
  powerOff,
  queryPileState,
  queryQueueState,
}
