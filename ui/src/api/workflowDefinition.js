import request from '@/utils/request'

// Real backend API calls for workflow definitions

/**
 * 查询流程定义列表（支持查询参数）
 * GET /workflow/definitions
 */
export const getProcessDefinitionList = (params) => {
  return request.get('/workflow/definitions', { params })
}

/**
 * 发布流程定义（后端会在缺失 code 时生成唯一编码）
 * POST /workflow/definition/publish
 */
export const publishProcessDefinition = (payload) => {
  return request.post('/workflow/definition/publish', payload)
}

// 保持兼容的别名
export const createProcessDefinition = (payload) => publishProcessDefinition(payload)

/**
 * 更新流程定义（仅更新 metadata）
 * PUT /workflow/definition/{id}
 */
export const updateProcessDefinition = (id, payload) => {
  return request.put(`/workflow/definition/${id}`, payload)
}

/**
 * 删除流程定义
 * DELETE /workflow/definition/{id}
 */
export const deleteProcessDefinition = (id) => {
  return request.delete(`/workflow/definition/${id}`)
}

/**
 * 保存可视化设计数据
 * PUT /workflow/definition/{id}/design
 */
export const saveProcessDefinitionDesign = (id, designData) => {
  return request.put(`/workflow/definition/${id}/design`, { designJson: designData })
}

/**
 * 查询可视化设计数据
 * GET /workflow/definition/{id}/design
 */
export const getProcessDefinitionDesign = (id) => {
  return request.get(`/workflow/definition/${id}/design`)
}

/**
 * 获取下一个流程定义编码（后端分配）
 */
export const getNextDefinitionCode = () => {
  return request.get('/workflow/definition/next-code')
}

