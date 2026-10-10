import request from '../utils/request'

/** AI 生成角色卡（含可选立绘） */
export function workshopGenerateCharacter(data) {
  return request.post('/workshop/character/generate', data, { timeout: 180000 })
}

/** 仅生成立绘 */
export function workshopGenerateAvatar(data) {
  return request.post('/workshop/character/avatar', data, { timeout: 180000 })
}

/** 生成并保存为草稿角色 */
export function workshopSaveCharacter(data) {
  return request.post('/workshop/character/save', data, { timeout: 180000 })
}

/** AI 一键生成世界树 */
export function workshopGenerateWorldTree(data) {
  return request.post('/workshop/world-tree/generate', data, { timeout: 180000 })
}

/** AI 扩展世界树节点 */
export function workshopExpandWorldNode(data) {
  return request.post('/workshop/world-tree/expand', data, { timeout: 180000 })
}

/** AI 润色灵感 */
export function workshopPolishInspiration(data) {
  return request.post('/workshop/inspiration/polish', data, { timeout: 120000 })
}
