import { getToken } from '../utils/request'
import request from '../utils/request'

/** 可作为叙事来源的会话 */
export function getNarrativeSessions() {
  return request.get('/narrative/sessions')
}

/** 我的叙事作品 */
export function getNarrativeWorks() {
  return request.get('/narrative/works')
}

export function getNarrativeWork(id) {
  return request.get(`/narrative/works/${id}`)
}

export function updateNarrativeWork(id, data) {
  return request.put(`/narrative/works/${id}`, data)
}

export function publishNarrativeWork(id) {
  return request.post(`/narrative/works/${id}/publish`)
}

export function unpublishNarrativeWork(id) {
  return request.post(`/narrative/works/${id}/unpublish`)
}

export function deleteNarrativeWork(id) {
  return request.delete(`/narrative/works/${id}`)
}

/** 社区广场：已发布且审核通过的作品 */
export function getPublishedWorks() {
  return request.get('/narrative/published')
}

/** 导出文件流，返回原始 Response 供 blob 下载 */
export async function fetchNarrativeExport(id, format = 'txt') {
  const token = getToken()
  const response = await fetch(exportNarrativeUrl(id, format), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    const err = new Error(`导出失败（HTTP ${response.status}）`)
    throw err
  }
  return response
}

/**
 * SSE 生成叙事作品。
 * 与 chat.js 的 streamChat 同构，但这里多了 done 事件里的作品 id，
 * 以及 meta 事件里的素材统计（消息条数、对话字数）。
 */
export async function streamNarrative(payload, { onMeta, onChunk, onDone, onError, signal } = {}) {
  const token = getToken()
  let response
  try {
    response = await fetch('/api/narrative/generate', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(payload),
      signal,
    })
  } catch (err) {
    if (err.name !== 'AbortError') onError?.(err)
    throw err
  }

  if (response.status === 401) {
    const err = new Error('未授权')
    onError?.(err)
    throw err
  }
  if (!response.ok) {
    const text = await response.text()
    let message = text
    try {
      const parsed = JSON.parse(text)
      message = parsed.message || text
    } catch {
      /* 保留原文 */
    }
    const err = new Error(message || `HTTP ${response.status}`)
    onError?.(err)
    throw err
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  let dataLines = []
  let settled = false
  let workId = null

  const settle = (fn) => {
    if (settled) return
    settled = true
    fn()
  }

  const handle = (name, payload) => {
    if (name === 'error') {
      settle(() => onError?.(new Error(payload || '生成失败')))
      return 'error'
    }
    if (payload === '[DONE]') {
      settle(() => onDone?.(workId))
      return 'done'
    }
    if (name === 'meta') {
      try {
        onMeta?.(JSON.parse(payload))
      } catch {
        /* 忽略解析失败 */
      }
      return
    }
    if (name === 'done') {
      try {
        workId = JSON.parse(payload).id
      } catch {
        /* 忽略 */
      }
      return
    }
    const text = unwrap(payload)
    if (text) onChunk?.(text)
    return
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let idx
      while ((idx = buffer.indexOf('\n')) >= 0) {
        let line = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 1)
        if (line.endsWith('\r')) line = line.slice(0, -1)

        if (line === '') {
          if (dataLines.length) {
            const r = handle(eventName, dataLines.join('\n'))
            dataLines = []
            eventName = 'message'
            if (r === 'done' || r === 'error') return
          } else {
            eventName = 'message'
          }
          continue
        }
        if (line.startsWith(':')) continue
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
          continue
        }
        if (line.startsWith('data:')) {
          let v = line.slice(5)
          if (v.startsWith(' ')) v = v.slice(1)
          dataLines.push(v)
        }
      }
    }
    if (dataLines.length) handle(eventName, dataLines.join('\n'))
    if (!settled) {
      settle(() => onError?.(new Error('连接已断开，未完成生成')))
    }
  } catch (err) {
    if (err?.name === 'AbortError') throw err
    settle(() => onError?.(err instanceof Error ? err : new Error(String(err))))
    throw err
  }
}

function unwrap(payload) {
  if (!payload) return ''
  const t = payload.trim()
  if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) {
    try {
      return JSON.parse(t)
    } catch {
      return payload
    }
  }
  return payload
}
