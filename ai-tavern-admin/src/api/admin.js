import request from '../utils/request'

export function getDashboardStats() {
  return request.get('/admin/dashboard/stats')
}

export function getDashboardTrend(params) {
  return request.get('/admin/dashboard/trend', { params })
}

export function getPopularCharacters(params) {
  return request.get('/admin/dashboard/popular-characters', { params })
}

export function getPendingCharacters(params) {
  const { pageSize, ...rest } = params || {}
  return request.get('/admin/characters', {
    params: {
      status: 'pending',
      ...rest,
      size: rest.size || pageSize || 10,
    },
  })
}

export function getAuditCharacters(params) {
  const { pageSize, ...rest } = params || {}
  return request.get('/admin/characters', {
    params: {
      ...rest,
      size: rest.size || pageSize || 10,
    },
  })
}

export function getAdminCharacterDetail(id) {
  return request.get(`/admin/characters/${id}`)
}

export function approveCharacter(id) {
  return request.post(`/admin/characters/${id}/approve`)
}

export function rejectCharacter(id, data) {
  return request.post(`/admin/characters/${id}/reject`, data)
}

export function offlineCharacter(id) {
  return request.post(`/admin/characters/${id}/offline`)
}

export function getUsers(params) {
  return request.get('/admin/users', { params })
}

export function banUser(id, data) {
  return request.put(`/admin/users/${id}/ban`, data)
}

export function updateMembership(id, data) {
  return request.put(`/admin/users/${id}/membership`, data)
}

export function getViolations(params) {
  return request.get('/admin/violations', { params })
}

export function getModelConfig() {
  return request.get('/admin/model/config')
}

export function updateModelConfig(data) {
  return request.put('/admin/model/config', data)
}
