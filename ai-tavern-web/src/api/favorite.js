import request from '../utils/request'

export function getFavorites(params) {
  return request.get('/favorites', { params })
}
