import request from '../utils/request'

/** 上下文占用概览：最近一次采样 + 历史平均 */
export function getContextOverview() {
  return request.get('/metrics/context/overview')
}

/** 上下文占用曲线 */
export function getContextSamples(limit = 60) {
  return request.get('/metrics/context', { params: { limit } })
}

/** 缓存命中对比 */
export function getCacheComparison() {
  return request.get('/metrics/cache')
}

/** 缓存埋点原始流水 */
export function getCacheTrace(limit = 50) {
  return request.get('/metrics/cache/trace', { params: { limit } })
}

/** MCP 调用时间线 */
export function getMcpTimeline(limit = 50) {
  return request.get('/metrics/mcp', { params: { limit } })
}

/** Token 明细 */
export function getTokenDetail(top = 10) {
  return request.get('/metrics/token', { params: { top } })
}

export function resetMetrics() {
  return request.delete('/metrics/reset')
}
