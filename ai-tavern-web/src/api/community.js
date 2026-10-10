import request from '../utils/request'

/** 社区广场列表。sort: latest 最新 / hot 最热 */
export function getCommunityWorks(sort = 'latest', page = 1, size = 12) {
  return request.get('/community/works', { params: { sort, page, size } })
}

/** 作品详情。未过审仅作者本人可见 */
export function getCommunityWork(id) {
  return request.get(`/community/works/${id}`)
}

/** 点赞/取消点赞，返回切换后的状态 */
export function toggleWorkLike(id) {
  return request.post(`/community/works/${id}/like`)
}

/** 收藏/取消收藏 */
export function toggleWorkCollect(id) {
  return request.post(`/community/works/${id}/collect`)
}

export function getWorkComments(id, page = 1, size = 20) {
  return request.get(`/community/works/${id}/comments`, { params: { page, size } })
}

export function addWorkComment(id, content) {
  return request.post(`/community/works/${id}/comments`, { content })
}

export function deleteWorkComment(commentId) {
  return request.delete(`/community/comments/${commentId}`)
}
