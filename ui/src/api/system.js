import request from '@/utils/request'

export const getSystemList = (params) => {
  return request.get('/system/list', { params })
}

export const getSystemById = (id) => {
  return request.get(`/system/${id}`)
}

export const createSystem = (data) => {
  return request.post('/system/add', data)
}

export const updateSystem = (id, data) => {
  return request.post(`/system/update/${id}`, data)
}

export const deleteSystem = (id) => {
  return request.get(`/system/delete/${id}`)
}

export const checkSystemStatus = (address, port, timeout) => {
  const params = { address, port }
  if (timeout !== undefined) params.timeout = timeout
  return request.get('/system/status', { params })
}

export const getSystemAttachments = (id) => {
  return request.get(`/system/${id}/attachments`)
}

