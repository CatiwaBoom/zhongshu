import request from '@/utils/request'

export const getMenuTree = () => {
  return request.get('/menus/tree')
}

export const createMenu = (data) => {
  return request.post('/menus', data)
}

export const updateMenu = (id, data) => {
  return request.put(`/menus/${id}`, data)
}

export const deleteMenu = (id) => {
  return request.delete(`/menus/${id}`)
}

export const getMenuById = (id) => {
  return request.get(`/menus/${id}`)
}

