import request from '../utils/request'

/** 某个角色的世界书条目 + 我的全局条目 */
export function getWorldBook(characterId) {
  return request.get(`/characters/${characterId}/worldbook`)
}

export function createWorldEntry(characterId, data) {
  return request.post(`/characters/${characterId}/worldbook`, data)
}

export function createGlobalEntry(data) {
  return request.post('/worldbook/global', data)
}

export function updateWorldEntry(id, data) {
  return request.put(`/worldbook/${id}`, data)
}

export function toggleWorldEntry(id, enabled) {
  return request.post(`/worldbook/${id}/toggle`, { enabled })
}

export function deleteWorldEntry(id) {
  return request.delete(`/worldbook/${id}`)
}

/** 命中预演：不调模型、不花钱，纯本地跑一遍关键词匹配 */
export function previewWorldBook(characterId, text, budget) {
  return request.post(`/characters/${characterId}/worldbook/preview`, { text, budget })
}
