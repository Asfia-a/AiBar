/**
 * 世界「示我周行 Show Me Your Ways」：背景 + 设定集 + 时间线。
 * 数据由 scripts/import_show_me_your_ways.py 从作者世界树生成。
 */
import payload from './showMeYourWaysWorldTree.data.json'

function cloneTree(nodes) {
  return (nodes || []).map((n) => ({
    id: n.id,
    label: n.label,
    type: n.type || 'lore',
    summary: n.summary || '',
    content: n.content || '',
    children: cloneTree(n.children),
  }))
}

function countNodes(nodes) {
  let n = 0
  const walk = (list) => {
    for (const x of list || []) {
      n += 1
      walk(x.children)
    }
  }
  walk(nodes)
  return n
}

function findChildByLabel(nodes, label) {
  for (const n of nodes || []) {
    if (n.label === label) return n
    const hit = findChildByLabel(n.children, label)
    if (hit) return hit
  }
  return null
}

/** @returns {Array} 新树实例，避免多次载入共享引用 */
export function createShowMeYourWaysWorldTree() {
  return cloneTree(payload.tree)
}

export const SHOW_ME_YOUR_WAYS_META = {
  title: payload.meta?.title || '示我周行',
  description: payload.meta?.description || '',
  sourceHint: payload.meta?.sourceHint || '',
  definitionCount: payload.meta?.definitionCount || 0,
  eventCount: payload.meta?.eventCount || 0,
  dataVersion: payload.meta?.dataVersion || 1,
}

/**
 * 仅当本机缓存就是「示我周行」且仍是旧版简写时，才判定为过期。
 * 不会误伤用户自己的其它世界树。
 */
export function isStaleShowMeYourWaysTree(nodes, savedVersion) {
  if (!Array.isArray(nodes) || !nodes.length) return false
  const root = nodes[0]
  if (!root || !String(root.label || '').includes('示我周行')) return false

  if (Number(savedVersion) !== Number(SHOW_ME_YOUR_WAYS_META.dataVersion)) return true

  const markers = [
    '正文为自写占位',
    '写外形、禁忌与凡人如何与之共处',
    '设定集骨架样例',
    '世界观正文，按原设定集目录展开',
    '来自设定集条目正文；无公开正文的条目保留标题与摘要',
  ]
  const blob = JSON.stringify(nodes)
  if (markers.some((m) => blob.includes(m))) return true

  // 完整版三大分册正文应很长
  const bg = findChildByLabel(nodes, '背景')
  const defs = findChildByLabel(nodes, '设定集')
  if ((bg?.content || '').length < 8000) return true
  if ((defs?.content || '').length < 40000) return true
  if (countNodes(nodes) < 100) return true

  return false
}
