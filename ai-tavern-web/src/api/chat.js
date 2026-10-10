import { getToken } from '../utils/request'
import request from '../utils/request'

export function getSessions() {
  return request.get('/chat/sessions')
}

export function createSession(data) {
  return request.post('/chat/sessions', data)
}

export function deleteSession(id) {
  return request.delete(`/chat/sessions/${id}`)
}

export function getMessages(sessionId) {
  return request.get(`/chat/sessions/${sessionId}/messages`)
}

/**
 * 解析 SSE。成功收到 [DONE] 才 onDone；出错只走 onError，避免失败后再刷新把本地消息清掉。
 */
async function consumeSse(response, { onChunk, onDone, onError }) {
  if (response.status === 401) {
    const { default: router } = await import('../router')
    const { setToken } = await import('../utils/request')
    setToken(null)
    router.push({ name: 'Login' })
    const err = new Error('未授权')
    onError?.(err)
    throw err
  }
  if (!response.ok) {
    const err = new Error((await response.text()) || `HTTP ${response.status}`)
    onError?.(err)
    throw err
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  let dataLines = []
  let settled = false
  let sawDone = false

  const settleDone = () => {
    if (settled) return
    settled = true
    onDone?.()
  }
  const settleError = (err) => {
    if (settled) return
    settled = true
    onError?.(err)
  }

  const flushEvent = () => {
    if (!dataLines.length) {
      eventName = 'message'
      return
    }
    const payload = dataLines.join('\n')
    dataLines = []
    const name = eventName
    eventName = 'message'

    if (name === 'error') {
      settleError(new Error(payload || '流式错误'))
      return 'error'
    }
    if (payload === '[DONE]') {
      sawDone = true
      settleDone()
      return 'done'
    }
    if (name === 'meta' || (payload.startsWith('{') && payload.includes('sessionId'))) {
      try {
        onChunk?.({ meta: JSON.parse(payload) })
      } catch {
        const text = unwrapData(payload)
        if (text) onChunk?.({ content: text })
      }
      return
    }
    const text = unwrapData(payload)
    if (text) onChunk?.({ content: text })
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
          const r = flushEvent()
          if (r === 'done' || r === 'error') return
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
    if (dataLines.length) {
      const r = flushEvent()
      if (r === 'done' || r === 'error') return
    }
    // 连接结束但没收到 [DONE]：视为异常，保留本地气泡
    if (!sawDone) {
      settleError(new Error('连接已断开，未完成流式输出'))
    }
  } catch (err) {
    if (err?.name === 'AbortError') throw err
    settleError(err instanceof Error ? err : new Error(String(err)))
    throw err
  }
}

function unwrapData(payload) {
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

export async function streamChat(sessionId, data, { onChunk, onDone, onError, signal }) {
  const token = getToken()
  let response
  try {
    response = await fetch(`/api/chat/sessions/${sessionId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ message: data.message || data.content, characterId: data.characterId }),
      signal,
    })
  } catch (err) {
    if (err.name !== 'AbortError') onError?.(err)
    throw err
  }
  await consumeSse(response, { onChunk, onDone, onError })
}

export async function streamRegenerate(sessionId, messageId, { onChunk, onDone, onError, signal } = {}) {
  const token = getToken()
  let response
  try {
    response = await fetch(`/api/chat/sessions/${sessionId}/regenerate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ messageId }),
      signal,
    })
  } catch (err) {
    if (err.name !== 'AbortError') onError?.(err)
    throw err
  }
  await consumeSse(response, { onChunk, onDone, onError })
}
