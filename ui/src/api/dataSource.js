import request from '@/utils/request'

export const getDataSourceList = (params) => {
  // allow passing filter/pagination params: { keyword, type, connectivity, page, size }
  return request.get('/datasource/list', { params })
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