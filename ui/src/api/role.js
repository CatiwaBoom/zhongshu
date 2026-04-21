import request from '@/utils/request'

export const getRoleList = (params) => {
  return request.get('/roles', { params })
}

export const createRole = (data) => {
  return request.post('/roles', data)
}

export const updateRole = (id, data) => {
  return request.put(`/roles/${id}`, data)
}

export const deleteRole = (id) => {
  return request.delete(`/roles/${id}`)
}

export const getRoleById = (id) => {
  return request.get(`/roles/${id}`)
}


export const getRoleMenus = (roleId) => {
  return request.get(`/roles/${roleId}/menus`)
}

export const assignRoleMenus = (roleId, menuIds) => {
  return request.post(`/roles/${roleId}/menus`, { menuIds })
}

// 获取某角色已分配的用户（返回用户 id 列表或用户对象数组，兼容不同后端）
export const getRoleUsers = (roleId) => {
  return request.get(`/roles/${roleId}/users`)
}

// 为角色分配用户（userIds: 数组）
export const assignRoleUsers = (roleId, userIds) => {
  return request.post(`/roles/${roleId}/users`, { userIds })
}

// NOTE: menus version polling 已移除，前端改为使用 SSE 实时推送


