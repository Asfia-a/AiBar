/** 世界广场游玩壳：树结构工具与探索进度 */

const DISCOVER_PREFIX = 'tavern_world_discover:'
const ROAM_PREFIX = 'tavern_world_roam_day:'

export function worldRoot(tree) {
  const list = Array.isArray(tree) ? tree : []
  return list[0] || null
}

/** 大厅区域：优先用根的子节点；没有则把根自己当唯一区域 */
export function worldZones(tree) {
  const root = worldRoot(tree)
  if (!root) return []
  const kids = root.children || []
  if (kids.length) return kids
  return [root]
}

export function isTimelineZone(node) {
  if (!node) return false
  const label = String(node.label || '')
  return node.type === 'era' || /时间线|年表|纪年|timeline/i.test(label)
}

export function nodeBrief(node, max = 72) {
  const raw = String(node?.summary || node?.content || '')
    .replace(/[#>*_`]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!raw) return '尚未留下可辨认的痕迹。'
  return raw.length > max ? `${raw.slice(0, max - 1)}…` : raw
}

export function nodeBody(node) {
  return String(node?.content || node?.summary || '').trim()
}

export function flattenNodes(tree) {
  const out = []
  const walk = (list, parent = null) => {
    for (const n of list || []) {
      out.push({ node: n, parent })
      walk(n.children || [], n)
    }
  }
  walk(Array.isArray(tree) ? tree : [])
  return out
}

export function countNodes(tree) {
  return flattenNodes(tree).length
}

/** 体量感文案，避免后台味「274 节点」 */
export function worldScaleLabel(nodeCount) {
  const n = Number(nodeCount) || 0
  if (n >= 150) return '大型世界'
  if (n >= 40) return '中型世界'
  if (n > 0) return '小型世界'
  return '未探明'
}

export function loadDiscovered(worldId) {
  if (worldId == null) return new Set()
  try {
    const raw = JSON.parse(localStorage.getItem(DISCOVER_PREFIX + worldId) || '[]')
    return new Set(Array.isArray(raw) ? raw.map(String) : [])
  } catch {
    return new Set()
  }
}

export function saveDiscovered(worldId, set) {
  if (worldId == null) return
  localStorage.setItem(DISCOVER_PREFIX + worldId, JSON.stringify([...set]))
}

export function markDiscovered(worldId, nodeId, set) {
  if (nodeId == null) return set
  const next = new Set(set)
  next.add(String(nodeId))
  saveDiscovered(worldId, next)
  return next
}

function todayKey() {
  const d = new Date()
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
}

/** 每日漫游剩余次数（默认 5） */
export function roamRemaining(worldId, daily = 5) {
  if (worldId == null) return daily
  try {
    const raw = JSON.parse(localStorage.getItem(ROAM_PREFIX + worldId) || 'null')
    if (!raw || raw.day !== todayKey()) return daily
    return Math.max(0, daily - Number(raw.used || 0))
  } catch {
    return daily
  }
}

export function consumeRoam(worldId, daily = 5) {
  if (worldId == null) return roamRemaining(worldId, daily)
  const key = ROAM_PREFIX + worldId
  let used = 0
  try {
    const raw = JSON.parse(localStorage.getItem(key) || 'null')
    if (raw && raw.day === todayKey()) used = Number(raw.used || 0)
  } catch {
    /* */
  }
  used += 1
  localStorage.setItem(key, JSON.stringify({ day: todayKey(), used }))
  return Math.max(0, daily - used)
}

/** 优先抽未发现的叶子/深层节点 */
export function pickEncounter(tree, discovered) {
  const flat = flattenNodes(tree)
  if (!flat.length) return null
  const unseen = flat.filter((x) => !discovered.has(String(x.node.id)))
  const pool = unseen.length ? unseen : flat
  // 偏向有正文的点
  const rich = pool.filter((x) => nodeBody(x.node).length > 40)
  const finalPool = rich.length ? rich : pool
  return finalPool[Math.floor(Math.random() * finalPool.length)] || null
}

export function timelineStops(zone) {
  if (!zone) return []
  const kids = zone.children || []
  if (kids.length) return kids
  return [zone]
}

export function gateTone(index) {
  const tones = [
    'from-[#3a2a22] via-[#1a1512] to-[#0e1013]',
    'from-[#1e2e35] via-[#141a1e] to-[#0e1013]',
    'from-[#243028] via-[#151a16] to-[#0e1013]',
    'from-[#2a2433] via-[#16141c] to-[#0e1013]',
    'from-[#2e261c] via-[#18140f] to-[#0e1013]',
  ]
  return tones[index % tones.length]
}
