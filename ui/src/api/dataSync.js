import request from '@/utils/request'

export const getDataSyncTaskList = () => {
  return request.get('/seatunnel/datasync/list')
}

export const getDataSyncTask = (id) => {
  return request.get(`/seatunnel/datasync/${id}`)
}

export const createDataSyncTask = (data) => {
  return request.post('/seatunnel/datasync/add', data)
}

export const updateDataSyncTask = (id, data) => {
  return request.post(`/seatunnel/datasync/update/${id}`, data)
}

export const deleteDataSyncTask = (id) => {
  return request.get(`/seatunnel/datasync/delete/${id}`)
}

export const previewDataSyncConfig = (id) => {
  return request.get(`/seatunnel/datasync/${id}/config`)
}

export const runDataSyncTask = (id) => {
  return request.post(`/seatunnel/datasync/run/${id}`)
}
