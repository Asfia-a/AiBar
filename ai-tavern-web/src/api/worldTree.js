import request from '../utils/request'

/** 世界广场列表（默认已发布） */
export function getWorlds(params) {
  return request.get('/worlds', { params })
}

export function getHotWorlds(params) {
  return request.get('/worlds/hot', { params })
}

export function getWorldDetail(id) {
  return request.get(`/worlds/${id}`)
}

export function createWorld(data, { publish = false } = {}) {
  return request.post('/worlds', data, { params: { publish } })
}

export function updateWorld(id, data) {
  return request.put(`/worlds/${id}`, data)
}

export function publishWorld(id) {
  return request.post(`/worlds/${id}/publish`)
}

export function offlineWorld(id) {
  return request.post(`/worlds/${id}/offline`)
}

export function deleteWorld(id) {
  return request.delete(`/worlds/${id}`)
}
