import request from '../utils/request'

export function checkIn() {
  return request.post('/user/check-in')
}

export function getCheckInRecords() {
  return request.get('/user/check-in/record')
}
