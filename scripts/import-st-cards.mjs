/**
 * 将 SillyTavern PNG 角色卡解析后上传 OSS 并创建/上架角色
 * Usage: node scripts/import-st-cards.mjs [png...]
 */
import fs from 'fs'
import path from 'path'
import zlib from 'zlib'
import { fileURLToPath } from 'url'

const BASE = process.env.API_BASE || 'http://127.0.0.1:8080/api'
const USER = process.env.TAVERN_USER || 'admin'
const PASS = process.env.TAVERN_PASS || 'admin123'

const PNG_SIG = [137, 80, 78, 71, 13, 10, 26, 10]

function readU32(buf, offset) {
  return buf.readUInt32BE(offset)
}

function decodeTextPayload(raw) {
  const text = Buffer.isBuffer(raw) ? raw.toString('utf8').trim() : String(raw).trim()
  if (!text) throw new Error('角色卡数据为空')
  if (text.startsWith('{') || text.startsWith('[')) return JSON.parse(text)
  const decoded = Buffer.from(text.replace(/\s+/g, ''), 'base64')
  return JSON.parse(decoded.toString('utf8'))
}

function extractCardFromPng(buf) {
  for (let i = 0; i < 8; i++) {
    if (buf[i] !== PNG_SIG[i]) throw new Error('不是有效的 PNG')
  }
  let offset = 8
  const found = {}
  while (offset + 12 <= buf.length) {
    const length = readU32(buf, offset)
    const type = buf.toString('ascii', offset + 4, offset + 8)
    const dataStart = offset + 8
    const dataEnd = dataStart + length
    if (dataEnd + 4 > buf.length) break
    if (type === 'tEXt' || type === 'zTXt') {
      let nullAt = -1
      for (let i = dataStart; i < dataEnd; i++) {
        if (buf[i] === 0) { nullAt = i; break }
      }
      if (nullAt > dataStart) {
        const keyword = buf.toString('utf8', dataStart, nullAt)
        if (keyword === 'chara' || keyword === 'ccv3' || keyword === 'ccv2') {
          let payload
          if (type === 'tEXt') {
            payload = buf.subarray(nullAt + 1, dataEnd)
          } else {
            const method = buf[nullAt + 1]
            if (method !== 0) throw new Error(`不支持的 zTXt: ${method}`)
            payload = zlib.inflateSync(buf.subarray(nullAt + 2, dataEnd))
          }
          found[keyword] = decodeTextPayload(payload)
        }
      }
    }
    if (type === 'IEND') break
    offset = dataEnd + 4
  }
  const card = found.ccv3 || found.chara || found.ccv2
  if (!card) throw new Error('PNG 中未找到角色卡数据')
  return card
}

function normalizeCard(raw) {
  let data = raw
  if (raw?.data && typeof raw.data === 'object') data = { ...raw.data }
  else if (raw?.character && typeof raw.character === 'object') data = { ...raw.character }
  if (!data.name && raw.name) data = { ...raw }
  if (!data.name) throw new Error('缺少 name')
  return data
}

function convertMesExample(mesExample) {
  if (!mesExample || !String(mesExample).trim()) return ''
  const text = String(mesExample).trim()
  if (text.startsWith('[')) {
    try { JSON.parse(text); return text } catch { /* */ }
  }
  const pairs = []
  const lines = text.split(/\r?\n/)
  let role = null
  let buf = []
  const flush = () => {
    if (role && buf.length) pairs.push({ role, content: buf.join('\n').trim() })
    buf = []
  }
  for (const line of lines) {
    const userMatch = line.match(/^(?:\{\{user\}\}|User|用户)\s*[:：]\s*(.*)$/i)
    const charMatch = line.match(/^(?:\{\{char\}\}|Char|Assistant|角色)\s*[:：]\s*(.*)$/i)
    if (userMatch) { flush(); role = 'user'; buf = [userMatch[1]] }
    else if (charMatch) { flush(); role = 'assistant'; buf = [charMatch[1]] }
    else if (role) buf.push(line)
  }
  flush()
  return pairs.length ? JSON.stringify(pairs, null, 2) : text
}

function mapCardToForm(data) {
  const descriptionBody = data.description || ''
  const personalityShort = data.personality || ''
  const personality = [descriptionBody, personalityShort].filter(Boolean).join('\n\n').trim()
    || personalityShort || descriptionBody
  const creatorNotes = data.creator_notes || data.creatorcomment || data.creator_comment || ''
  const shortDesc = (creatorNotes || descriptionBody || personalityShort || '').slice(0, 200)
  return {
    name: String(data.name || '').slice(0, 50),
    description: shortDesc,
    personality,
    backgroundStory: data.scenario || data.background || '',
    greeting: data.first_mes || data.firstMes || data.greeting || '',
    exampleDialogue: convertMesExample(data.mes_example || data.mesExample || data.example_dialogue || ''),
    temperature: 0.7,
    topP: 0.9,
    maxTokens: 800,
  }
}

async function api(method, urlPath, { token, body, formData } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (formData) {
    payload = formData
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(`${BASE}${urlPath}`, { method, headers, body: payload })
  const json = await res.json()
  if (json.code !== 0 && json.code !== 200) {
    throw new Error(`${method} ${urlPath}: ${json.message || JSON.stringify(json)}`)
  }
  return json.data
}

async function importOne(filePath, token) {
  const abs = path.resolve(filePath)
  const buf = fs.readFileSync(abs)
  const card = extractCardFromPng(buf)
  const data = normalizeCard(card)
  const form = mapCardToForm(data)
  console.log(`解析: ${form.name} <- ${path.basename(abs)}`)

  const blob = new Blob([buf], { type: 'image/png' })
  const fd = new FormData()
  const safe = `${form.name.replace(/[\\/:*?"<>|]/g, '_').slice(0, 40)}.png`
  fd.append('file', blob, safe)
  const up = await api('POST', '/upload', { token, formData: fd })
  const avatarUrl = up?.url || up
  console.log(`  OSS: ${avatarUrl}`)

  const created = await api('POST', '/characters', {
    token,
    body: { ...form, avatar: avatarUrl },
  })
  console.log(`  创建 id=${created.id}`)

  await api('POST', `/characters/${created.id}/publish`, { token })
  await api('POST', `/admin/characters/${created.id}/approve`, { token })
  console.log(`  已提交并审核通过`)
  return { id: created.id, name: form.name, avatar: avatarUrl }
}

async function main() {
  const __dirname = path.dirname(fileURLToPath(import.meta.url))
  const defaults = [
    'E:\\Game\\H\\ASMR\\ima\\main_yes-my-liege_spec_v2.png',
    'E:\\Game\\H\\ASMR\\ima\\上杉绘梨衣（东京逃亡·全剧情推进版）.st.png',
  ]
  const files = process.argv.slice(2)
  const targets = files.length ? files : defaults

  const login = await api('POST', '/auth/login', {
    body: { username: USER, password: PASS },
  })
  const token = login.token || login.accessToken || login
  if (!token || typeof token !== 'string') {
    // 兼容 { token, user }
    const t = login?.token
    if (!t) throw new Error(`登录返回异常: ${JSON.stringify(login)}`)
  }
  const jwt = typeof token === 'string' ? token : login.token
  console.log(`登录成功: ${USER}`)

  const results = []
  for (const f of targets) {
    if (!fs.existsSync(f)) {
      console.error(`跳过，文件不存在: ${f}`)
      continue
    }
    results.push(await importOne(f, jwt))
  }
  console.log('\n完成:')
  for (const r of results) console.log(`  #${r.id} ${r.name}\n    ${r.avatar}`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
