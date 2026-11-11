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

/** 按角色导出条目（JSON 结构，前端下载成文件） */
export function exportWorldBook(characterId) {
  return request.get(`/characters/${characterId}/worldbook/export`)
}

/** 导入条目：脏条目服务端会跳过，返回 { imported, skipped } */
export function importWorldBook(characterId, entries) {
  return request.post(`/characters/${characterId}/worldbook/import`, { entries })
}

/** 拖拽排序：ids 按界面上的新顺序全量提交 */
export function reorderWorldEntries(characterId, ids) {
  return request.put(`/characters/${characterId}/worldbook/reorder`, { ids })
}
