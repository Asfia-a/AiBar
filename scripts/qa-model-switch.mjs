#!/usr/bin/env node
/**
 * 验收：同会话切换 deepseek-flash / deepseek-v4-pro 各聊一轮，
 * 核对 meta.modelName、积分变化、会话落库模型名。
 *
 * 用法：
 *   node scripts/qa-model-switch.mjs
 * 依赖 %TEMP%/aibar-qa.json：{ token, sessionId }
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const qaPath = path.join(os.tmpdir(), 'aibar-qa.json')
const qa = JSON.parse(fs.readFileSync(qaPath, 'utf8'))
const token = qa.token
const sessionId = qa.sessionId
const base = process.env.TAVERN_API_BASE || 'http://127.0.0.1:8080/api'

async function api(method, urlPath, body) {
  const res = await fetch(`${base}${urlPath}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json()
  if (!res.ok || json.code !== 0) {
    throw new Error(`${method} ${urlPath} => HTTP ${res.status} ${JSON.stringify(json)}`)
  }
  return json.data
}

async function streamChat(message) {
  const res = await fetch(`${base}/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ message, thinking: false }),
  })
  if (!res.ok) {
    const t = await res.text()
    throw new Error(`stream HTTP ${res.status}: ${t}`)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  let event = 'message'
  let meta = null
  let err = null
  let content = ''
  let done = false

  const flush = (payload) => {
    if (payload === '[DONE]') {
      done = true
      return
    }
    if (event === 'error') {
      err = payload
      done = true
      return
    }
    if (event === 'meta' || (payload.startsWith('{') && payload.includes('sessionId'))) {
      try {
        meta = JSON.parse(payload)
      } catch {
        /* ignore */
      }
      return
    }
    if (event !== 'reasoning') content += payload
  }

  while (!done) {
    const { value, done: eof } = await reader.read()
    if (eof) break
    buf += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buf.indexOf('\n')) >= 0) {
      let line = buf.slice(0, idx)
      buf = buf.slice(idx + 1)
      if (line.endsWith('\r')) line = line.slice(0, -1)
      if (line === '') {
        event = 'message'
        continue
      }
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
        continue
      }
      if (line.startsWith('data:')) {
        let data = line.slice(5)
        if (data.startsWith(' ')) data = data.slice(1)
        flush(data)
        if (done) break
      }
    }
  }

  return {
    meta,
    err,
    content,
    preview: content.replace(/\s+/g, ' ').slice(0, 120),
  }
}

async function round(model, message) {
  console.log(`\n===== ${model} =====`)
  const sw = await api('PUT', `/chat/sessions/${sessionId}/model`, { modelName: model })
  console.log('switch =>', sw)
  const out = await streamChat(message)
  console.log('meta.modelName =', out.meta?.modelName)
  console.log('len =', out.content.length, 'err =', out.err)
  console.log('preview =', out.preview)
  if (out.meta) console.log('meta =', JSON.stringify(out.meta))
  return { model, switch: sw, ...out }
}

const before = (await api('GET', '/user/profile')).points
console.log('points before =', before)

const flash = await round('deepseek-flash', '用五个字以内打招呼。')
const mid = (await api('GET', '/user/profile')).points
console.log('points after flash =', mid)

const pro = await round('deepseek-v4-pro', '回一个字：好')
const after = (await api('GET', '/user/profile')).points
console.log('points after pro =', after, 'delta =', before - after)

const sessions = await api('GET', '/chat/sessions')
const cur = sessions.find((s) => Number(s.id) === Number(sessionId))
console.log('session.modelName =', cur?.modelName)

let tokenLogs = null
try {
  tokenLogs = await api('GET', '/user/token/logs?page=1&size=5')
  console.log('token logs =', JSON.stringify(tokenLogs).slice(0, 500))
} catch (e) {
  console.log('token logs skip:', e.message)
}

const report = {
  before,
  mid,
  after,
  delta: before - after,
  flash: {
    model: flash.model,
    metaModel: flash.meta?.modelName,
    len: flash.content.length,
    err: flash.err,
    preview: flash.preview,
  },
  pro: {
    model: pro.model,
    metaModel: pro.meta?.modelName,
    len: pro.content.length,
    err: pro.err,
    preview: pro.preview,
  },
  sessionModel: cur?.modelName,
  pass: {
    flashMeta: flash.meta?.modelName === 'deepseek-flash',
    proMeta: pro.meta?.modelName === 'deepseek-v4-pro',
    sessionSaved: cur?.modelName === 'deepseek-v4-pro',
    charged: before > after,
    noStreamError: !flash.err && !pro.err,
    gotContent: flash.content.length > 0 && pro.content.length > 0,
  },
}
fs.writeFileSync(path.join(os.tmpdir(), 'aibar-qa-model-accept.json'), JSON.stringify(report, null, 2))
console.log('\nPASS checklist =', report.pass)
const ok = Object.values(report.pass).every(Boolean)
console.log(ok ? 'ACCEPTANCE OK' : 'ACCEPTANCE FAIL')
process.exit(ok ? 0 : 1)
