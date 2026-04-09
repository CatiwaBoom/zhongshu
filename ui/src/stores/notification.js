import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElNotification } from 'element-plus'
import {
  getNotificationList,
  getUnreadCount,
  markAllNotificationRead,
  markNotificationRead
} from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const latestList = ref([])
  const loading = ref(false)
  const connected = ref(false)
  const inited = ref(false)

  let eventSource = null
  let reconnectTimer = null
  let reconnectDelay = 1000

  const hasUnread = computed(() => unreadCount.value > 0)

  const safeParse = (text) => {
    try {
      return JSON.parse(text)
    } catch (e) {
      return null
    }
  }

  const closeStream = () => {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    connected.value = false
  }

  const scheduleReconnect = () => {
    if (reconnectTimer) return
    // 采用指数退避，避免网络抖动时反复快速重连造成额外压力
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      startStream()
      reconnectDelay = Math.min(reconnectDelay * 2, 15000)
    }, reconnectDelay)
  }

  const handleIncomingNotice = (payload) => {
    if (!payload) return
    const item = {
      id: payload.messageId,
      title: payload.title,
      content: payload.content,
      isRead: 0,
      bizType: payload.bizType,
      bizId: payload.bizId,
      createdAt: payload.createdAt
    }

    latestList.value = [item, ...latestList.value.filter(x => x.id !== item.id)].slice(0, 8)
    unreadCount.value = Number(payload.unreadCount || unreadCount.value + 1)

    ElNotification({
      title: payload.title || '新站内信',
      message: payload.content || '您收到一条新消息',
      position: 'top-right',
      duration: 4200,
      type: 'success'
    })
  }

  const startStream = () => {
    const token = localStorage.getItem('token')
    if (!token) return

    closeStream()
    const streamUrl = `/api/notification/stream?token=${encodeURIComponent(token)}`
    eventSource = new EventSource(streamUrl)

    eventSource.onopen = () => {
      connected.value = true
      reconnectDelay = 1000
    }

    eventSource.addEventListener('init', (event) => {
      const payload = safeParse(event.data)
      if (payload && typeof payload.unreadCount !== 'undefined') {
        unreadCount.value = Number(payload.unreadCount || 0)
      }
    })

    eventSource.addEventListener('notice', (event) => {
      handleIncomingNotice(safeParse(event.data))
    })

    eventSource.onerror = () => {
      closeStream()
      scheduleReconnect()
    }
  }

  const loadUnreadCount = async () => {
    const res = await getUnreadCount()
    const result = res.data
    if (result?.code === 200) {
      unreadCount.value = Number(result?.data?.count || 0)
    }
  }

  const loadLatestList = async () => {
    const res = await getNotificationList({ page: 1, size: 8 })
    const result = res.data
    if (result?.code === 200) {
      latestList.value = Array.isArray(result?.data?.records) ? result.data.records : []
    }
  }

  const init = async () => {
    if (inited.value) return
    loading.value = true
    try {
      await Promise.all([loadUnreadCount(), loadLatestList()])
      startStream()
      inited.value = true
    } finally {
      loading.value = false
    }
  }

  const refresh = async () => {
    await Promise.all([loadUnreadCount(), loadLatestList()])
  }

  const markRead = async (id) => {
    if (!id) return
    const res = await markNotificationRead(id)
    const result = res.data
    if (result?.code !== 200) return

    latestList.value = latestList.value.map(item => item.id === id ? { ...item, isRead: 1 } : item)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  const markAllRead = async () => {
    const res = await markAllNotificationRead()
    const result = res.data
    if (result?.code !== 200) return

    latestList.value = latestList.value.map(item => ({ ...item, isRead: 1 }))
    unreadCount.value = 0
  }

  const stop = () => {
    inited.value = false
    closeStream()
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  return {
    unreadCount,
    latestList,
    loading,
    connected,
    hasUnread,
    init,
    refresh,
    markRead,
    markAllRead,
    stop
  }
})

