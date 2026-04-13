import request from '@/utils/request'

// 获取模型列表（不分页简版）
export const getModelList = (params) => {
  return request.get('/models', { params })
}

// 获取单个模型（包含字段定义）
export const getModel = (id) => {
  return request.get(`/models/${id}`)
}

// 创建模型
export const createModel = (data) => {
  return request.post('/models', data)
}

// 更新模型
export const updateModel = (id, data) => {
  return request.put(`/models/${id}`, data)
}

// 删除模型
export const deleteModel = (id) => {
  return request.delete(`/models/${id}`)
}

// 生成 DDL（仅返回 SQL，不执行），可传入 dsId 用于指定目标数据库以选择生成策略
// 生成 DDL（仅返回 SQL，不执行），可传入 dsId 用于指定目标数据库以选择生成策略
// 可选参数 execute=true 表示在目标数据源上执行 DDL（高危操作，需谨慎）
export const generateDdl = (id, dsId, execute = false, schema = null) => {
  const params = {}
  if (dsId) params.dsId = dsId
  if (schema) params.schema = schema
  if (execute) params.execute = true
  return request.post(`/models/${id}/generate-ddl`, null, { params })
}

