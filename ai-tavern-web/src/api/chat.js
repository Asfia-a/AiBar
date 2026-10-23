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

/** 批量删除消息：删掉后不再进入上下文（硬删除，不可恢复） */
export function deleteMessages(sessionId, ids) {
  return request.post(`/chat/sessions/${sessionId}/messages/delete`, { ids })
}

/** 会话级作者注释：一段插在历史里的强指令 */
export function getAuthorNote(sessionId) {
  return request.get(`/chat/sessions/${sessionId}/author-note`)
}

export function saveAuthorNote(sessionId, data) {
  return request.put(`/chat/sessions/${sessionId}/author-note`, data)
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

/** 所有流式接口共用的 POST 骨架：拿响应后交给 consumeSse */
async function postSse(url, body, { onChunk, onDone, onError, signal } = {}) {
  const token = getToken()
  let response
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body || {}),
      signal,
    })
  } catch (err) {
    if (err.name !== 'AbortError') onError?.(err)
    throw err
  }
  await consumeSse(response, { onChunk, onDone, onError })
}

export function streamChat(sessionId, data, handlers) {
  return postSse(
    `/api/chat/sessions/${sessionId}/messages`,
    { message: data.message || data.content, characterId: data.characterId },
    handlers,
  )
}

export function streamRegenerate(sessionId, messageId, handlers = {}) {
  return postSse(`/api/chat/sessions/${sessionId}/regenerate`, { messageId }, handlers)
}

/** 再生成一版：旧版本保留，前端可以左右切 */
export function streamSwipe(messageId, handlers = {}) {
  return postSse(`/api/chat/messages/${messageId}/swipe`, {}, handlers)
}

/** 切到第 index 版（从 0 开始），不调模型 */
export function selectSwipe(messageId, index) {
  return request.post(`/chat/messages/${messageId}/swipe/select`, { index })
}

export function editMessage(messageId, content) {
  return request.put(`/chat/messages/${messageId}`, { content })
}

/** 以这条消息为终点复制出一段新会话 */
export function branchSession(messageId) {
  return request.post(`/chat/messages/${messageId}/branch`)
}
