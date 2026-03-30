import request from '@/utils/request'

export const getDataSourceList = () => {
  return request.get('/datasource/list')
}

export const createDataSource = (data) => {
  return request.post('/datasource/add', data)
}

export const updateDataSource = (id, data) => {
  return request.post(`/datasource/update/${id}`, data)
}

export const deleteDataSource = (id) => {
  return request.get(`/datasource/delete/${id}`)
}

export const testDataSourceConnect = (id) => {
  return request.get(`/datasource/test/connect/${id}`)
}