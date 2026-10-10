import request from '../utils/request'

export function getCharacters(params) {
  return request.get('/characters', { params })
}

export function getHotCharacters(params) {
  return request.get('/characters/hot', { params })
}

export function getNewCharacters(params) {
  return request.get('/characters/new', { params })
}

export function getRanking(params) {
  return request.get('/characters/hot', { params })
}

export function searchCharacters(params) {
  return request.get('/characters/search', { params })
}

export function getCharacterDetail(id) {
  return request.get(`/characters/${id}`)
}

export function createCharacter(data) {
  return request.post('/characters', data)
}

export function updateCharacter(id, data) {
  return request.put(`/characters/${id}`, data)
}

export function publishCharacter(id) {
  return request.post(`/characters/${id}/publish`)
}

export function deleteCharacter(id) {
  return request.delete(`/characters/${id}`)
}

export function favoriteCharacter(id) {
  return request.post(`/characters/${id}/favorite`)
}

export function rateCharacter(id, data) {
  return request.post(`/characters/${id}/rate`, data)
}

export function getComments(id, params) {
  return request.get(`/characters/${id}/comments`, { params })
}

export function addComment(id, data) {
  return request.post(`/characters/${id}/comments`, data)
}

export function cloneCharacter(id) {
  return request.post(`/characters/${id}/replica`)
}

export function getCategories() {
  return request.get('/categories')
}

export function getTags() {
  return request.get('/tags')
}

export function getFavorites() {
  return request.get('/favorites')
}
