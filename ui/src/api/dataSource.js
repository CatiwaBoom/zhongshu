import request from '@/utils/request'

export const getDataSourceList = (params) => {
  // allow passing filter/pagination params: { keyword, type, connectivity, page, size }
  return request.get('/datasource/list', { params })
}

// 获取指定数据源下的 schema 列表（或 catalog），用于生成 DDL 时选择目标模式
export const getDataSourceSchemas = (id) => {
  return request.get(`/datasource/${id}/schemas`)
}

// 检查指定数据源下模式中是否存在给定表名
export const checkTableExists = (dataSourceId, schema, tableName) => {
  const params = { tableName }
  if (schema) params.schema = schema
  return request.get(`/datasource/${dataSourceId}/tables/exists`, { params })
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