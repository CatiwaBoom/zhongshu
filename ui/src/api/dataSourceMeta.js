import request from '@/utils/request'

export const getSchemas = (dataSourceId) => {
  return request.get(`/datasource/meta/schemas/${dataSourceId}`)
}

export const getTables = (dataSourceId, params) => {
  return request.get(`/datasource/meta/tables/${dataSourceId}`, { params })
}

export const getColumns = (dataSourceId, params) => {
  return request.get(`/datasource/meta/columns/${dataSourceId}`, { params })
}
