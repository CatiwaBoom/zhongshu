import request from '@/utils/request'

export const getUnreadCount = () => {
  return request.get('/notification/unread-count')
}

export const getNotificationList = (params) => {
  return request.get('/notification/list', { params })
}

export const markNotificationRead = (id) => {
  return request.post(`/notification/read/${id}`)
}

export const markAllNotificationRead = () => {
  return request.post('/notification/read-all')
}

export const sendNotification = (data) => {
  return request.post('/notification/send', data)
}

