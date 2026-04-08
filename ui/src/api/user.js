import request from '@/utils/request'

export const getUserList = (params) => {
  // forward params as query parameters
  return request.get('/user/list', { params })
}

export const createUser = (data) => {
  return request.post('/user/add', data)
}

export const updateUser = (id, data) => {
  return request.post(`/user/update/${id}`, data)
}

export const deleteUser = (id) => {
  return request.get(`/user/delete/${id}`)
}

export const getUserById = (id) => {
  return request.get(`/user/${id}`)
}

