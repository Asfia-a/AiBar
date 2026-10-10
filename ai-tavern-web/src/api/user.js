import request from '../utils/request'

export function getProfile() {
  return request.get('/user/profile')
}

export function updateProfile(data) {
  return request.put('/user/profile', data)
}

export function getTokenBalance() {
  return request.get('/user/token/balance')
}

export function getTokenLogs(params) {
  return request.get('/user/token/logs', { params })
}

export function recharge(data) {
  return request.post('/user/recharge', data)
}
