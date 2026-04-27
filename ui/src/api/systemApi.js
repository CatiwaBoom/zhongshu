import request from '@/utils/request'

export const listSystemApis = (systemId) => {
  return request.get('/system/api/list', { params: { systemId } })
}

export const getSystemApi = (id) => {
  return request.get(`/system/api/${id}`)
}

export const createSystemApi = (data) => {
  return request.post('/system/api/add', data)
}

export const updateSystemApi = (id, data) => {
  return request.post(`/system/api/update/${id}`, data)
}

export const deleteSystemApi = (id) => {
  return request.get(`/system/api/delete/${id}`)
}

