/**
 * SillyTavern 角色卡解析（V1 / V2 / V3 JSON，以及 PNG 内嵌 chara/ccv3）
 */

const PNG_SIG = [137, 80, 78, 71, 13, 10, 26, 10]

function readU32(view, offset) {
  return view.getUint32(offset, false)
}

function bytesToAscii(bytes, start, end) {
  let s = ''
  for (let i = start; i < end; i++) s += String.fromCharCode(bytes[i])
  return s
}

function findNull(bytes, start, end) {
  for (let i = start; i < end; i++) {
    if (bytes[i] === 0) return i
  }
  return -1
}

function decodeTextPayload(raw) {
  const text = typeof raw === 'string' ? raw.trim() : new TextDecoder().decode(raw).trim()
  if (!text) throw new Error('角色卡数据为空')
  // 常见为 base64(JSON)；也兼容裸 JSON
  if (text.startsWith('{') || text.startsWith('[')) {
    return JSON.parse(text)
  }
  try {
    const decoded = atob(text.replace(/\s+/g, ''))
    const json = new TextDecoder().decode(
      Uint8Array.from(decoded, (c) => c.charCodeAt(0)),
    )
    return JSON.parse(json)
  } catch {
    // 再试 latin1 base64 → utf8
    const bin = atob(text.replace(/\s+/g, ''))
    return JSON.parse(bin)
  }
}

async function inflateZlib(bytes) {
  if (typeof DecompressionStream === 'undefined') {
    throw new Error('当前浏览器不支持解压 zTXt 角色卡，请改用 JSON 或 tEXt PNG')
  }
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate'))
  const buf = await new Response(stream).arrayBuffer()
  return new Uint8Array(buf)
}

/**
 * 从 PNG 提取角色卡 JSON（优先 ccv3，其次 chara）
 */
export async function extractCardFromPng(arrayBuffer) {
  const bytes = new Uint8Array(arrayBuffer)
  for (let i = 0; i < 8; i++) {
    if (bytes[i] !== PNG_SIG[i]) throw new Error('不是有效的 PNG 文件')
  }

  const view = new DataView(arrayBuffer)
  let offset = 8
  const found = {}

  while (offset + 12 <= bytes.length) {
    const length = readU32(view, offset)
    const type = bytesToAscii(bytes, offset + 4, offset + 8)
    const dataStart = offset + 8
    const dataEnd = dataStart + length
    if (dataEnd + 4 > bytes.length) break

    if (type === 'tEXt' || type === 'zTXt') {
      const nullAt = findNull(bytes, dataStart, dataEnd)
      if (nullAt > dataStart) {
        const keyword = new TextDecoder().decode(bytes.subarray(dataStart, nullAt))
        if (keyword === 'chara' || keyword === 'ccv3' || keyword === 'ccv2') {
          let payload
          if (type === 'tEXt') {
            payload = bytes.subarray(nullAt + 1, dataEnd)
          } else {
            // zTXt: keyword\0 + compression method (1) + zlib data
            const method = bytes[nullAt + 1]
            if (method !== 0) throw new Error(`不支持的 zTXt 压缩方式: ${method}`)
            payload = await inflateZlib(bytes.subarray(nullAt + 2, dataEnd))
          }
          found[keyword] = decodeTextPayload(payload)
        }
      }
    }

    if (type === 'IEND') break
    offset = dataEnd + 4
  }

  const card = found.ccv3 || found.chara || found.ccv2
  if (!card) throw new Error('PNG 中未找到 SillyTavern 角色卡数据（chara/ccv3）')
  return card
}

/**
 * 归一化 V1/V2/V3 为统一 data 字段
 */
export function normalizeCard(raw) {
  if (!raw || typeof raw !== 'object') throw new Error('角色卡格式无效')
  let data = raw
  if (raw.data && typeof raw.data === 'object') {
    data = { ...raw.data }
  } else if (raw.character && typeof raw.character === 'object') {
    data = { ...raw.character }
  } else if (raw.card && typeof raw.card === 'object') {
    data = raw.card.data ? { ...raw.card.data } : { ...raw.card }
  }
  if (!data.name && raw.name) data = { ...raw }
  if (!data.name) throw new Error('角色卡缺少 name 字段')
  return data
}

/**
 * 将 SillyTavern mes_example 转为本系统示例对话 JSON 字符串
 */
export function convertMesExample(mesExample) {
  if (!mesExample || !String(mesExample).trim()) return ''
  const text = String(mesExample).trim()
  // 已是 JSON
  if (text.startsWith('[')) {
    try {
      JSON.parse(text)
      return text
    } catch { /* fallthrough */ }
  }

  const blocks = text
    .split(/<START>/i)
    .map((b) => b.trim())
    .filter(Boolean)

  const pairs = []
  // simpler line-by-line
  const pushFrom = (block) => {
    const lines = block.split(/\r?\n/)
    let role = null
    let buf = []
    const flush = () => {
      if (role && buf.length) {
        pairs.push({ role, content: buf.join('\n').trim() })
      }
      buf = []
    }
    for (const line of lines) {
      const userMatch = line.match(/^(?:\{\{user\}\}|User|用户)\s*[:：]\s*(.*)$/i)
      const charMatch = line.match(/^(?:\{\{char\}\}|Char|Assistant|角色)\s*[:：]\s*(.*)$/i)
      if (userMatch) {
        flush()
        role = 'user'
        buf = [userMatch[1]]
      } else if (charMatch) {
        flush()
        role = 'assistant'
        buf = [charMatch[1]]
      } else if (role) {
        buf.push(line)
      }
    }
    flush()
  }

  if (blocks.length) blocks.forEach(pushFrom)
  else pushFrom(text)

  if (pairs.length) {
    return JSON.stringify(pairs, null, 2)
  }
  // 无法结构化时原样保留，供 prompt 使用
  return text
}

/**
 * 映射到本站创建角色表单字段
 */
export function mapCardToForm(data) {
  const descriptionBody = data.description || ''
  const personalityShort = data.personality || ''
  // ST 的 description 通常是完整人设正文，写入 personality 供对话 prompt 使用
  const personality = [descriptionBody, personalityShort].filter(Boolean).join('\n\n').trim()
    || personalityShort
    || descriptionBody

  const creatorNotes = data.creator_notes || data.creatorcomment || data.creator_comment || ''
  const shortDesc = (creatorNotes || descriptionBody || personalityShort || '').slice(0, 200)

  return {
    name: String(data.name || '').slice(0, 50),
    description: shortDesc,
    personality,
    backgroundStory: data.scenario || data.background || '',
    greeting: data.first_mes || data.firstMes || data.greeting || '',
    exampleDialogue: convertMesExample(data.mes_example || data.mesExample || data.example_dialogue || ''),
    tags: Array.isArray(data.tags) ? data.tags.map(String).filter(Boolean) : [],
  }
}

/**
 * 解析文件：.json / .png
 * @returns {{ form: object, tagNames: string[], avatarFile: File|null, previewUrl: string|null }}
 */
export async function parseSillyTavernFile(file) {
  const name = (file.name || '').toLowerCase()
  const buf = await file.arrayBuffer()
  let raw

  if (name.endsWith('.png') || file.type === 'image/png') {
    raw = await extractCardFromPng(buf)
  } else if (name.endsWith('.json') || file.type === 'application/json' || file.type === 'text/plain') {
    const text = new TextDecoder().decode(buf)
    raw = JSON.parse(text)
  } else {
    // 尝试按内容判断
    const head = new Uint8Array(buf, 0, Math.min(8, buf.byteLength))
    const isPng = PNG_SIG.every((b, i) => head[i] === b)
    if (isPng) raw = await extractCardFromPng(buf)
    else raw = JSON.parse(new TextDecoder().decode(buf))
  }

  const data = normalizeCard(raw)
  const form = mapCardToForm(data)
  const isPng = name.endsWith('.png') || file.type === 'image/png'
    || PNG_SIG.every((b, i) => new Uint8Array(buf, 0, 8)[i] === b)

  let avatarFile = null
  let previewUrl = null

  // PNG 角色卡本体即角色立绘；另支持 JSON 内嵌 data URI / HTTP 头像
  if (isPng) {
    const safeName = `${(form.name || 'character').replace(/[\\/:*?"<>|]/g, '_').slice(0, 40)}.png`
    avatarFile = new File([buf], safeName, { type: 'image/png' })
    previewUrl = URL.createObjectURL(avatarFile)
  } else {
    const embedded = data.avatar || data.avatar_url || data.avatarUrl
    if (typeof embedded === 'string' && embedded.startsWith('data:image/')) {
      const m = embedded.match(/^data:(image\/[a-zA-Z0-9.+-]+);base64,(.+)$/)
      if (m) {
        const bin = atob(m[2])
        const bytes = new Uint8Array(bin.length)
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
        const ext = m[1].includes('jpeg') || m[1].includes('jpg') ? 'jpg'
          : m[1].includes('webp') ? 'webp'
            : m[1].includes('gif') ? 'gif' : 'png'
        const safeName = `${(form.name || 'character').replace(/[\\/:*?"<>|]/g, '_').slice(0, 40)}.${ext}`
        avatarFile = new File([bytes], safeName, { type: m[1] })
        previewUrl = URL.createObjectURL(avatarFile)
      }
    } else if (typeof embedded === 'string' && /^https?:\/\//i.test(embedded)) {
      form.avatarUrl = embedded
    }
  }

  return {
    form,
    tagNames: form.tags || [],
    avatarFile,
    previewUrl,
    remoteAvatarUrl: form.avatarUrl || null,
  }
}

/**
 * 将表单字段导出为 SillyTavern V2 角色卡 JSON（可被 ST / 本站导入）
 */
export function buildStCardV2(form = {}) {
  const data = {
    name: form.name || '未命名',
    description: form.personality || form.description || '',
    personality: form.description || '',
    scenario: form.backgroundStory || '',
    first_mes: form.greeting || '',
    mes_example: form.exampleDialogue || '',
    creator_notes: form.description || '',
    tags: Array.isArray(form.tags) ? form.tags : [],
    avatar: form.avatar || form.avatarUrl || '',
  }
  return {
    spec: 'chara_card_v2',
    spec_version: '2.0',
    data,
  }
}

export function downloadStCardJson(form, filename) {
  const card = form?.spec ? form : buildStCardV2(form)
  const name = (card.data?.name || form.name || 'character').replace(/[\\/:*?"<>|]/g, '_').slice(0, 40)
  const blob = new Blob([JSON.stringify(card, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename || `${name}.json`
  a.click()
  URL.revokeObjectURL(a.href)
}
