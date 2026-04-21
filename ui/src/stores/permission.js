import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMenuTree } from '@/api/systemMenu'
import { getRoleMenus } from '@/api/role'

export const usePermissionStore = defineStore('permission', () => {
  const menuTree = ref([])
  const roles = ref([])

  // 轮询的内部状态
  // SSE 连接和重连状态
  let _eventSource = null
  let _reconnectTimer = null
  let _reconnectDelay = 1000

  function filterTree(nodes, allowed) {
    if (!nodes) return []
    const out = []
    for (const n of nodes) {
      if (!n) continue
      const children = filterTree(n.children, allowed)
      const keep = allowed.has(n.id) || (children && children.length > 0)
      if (keep) {
        out.push({ ...n, children })
      }
    }
    return out
  }

  async function loadMenus() {
    try {
      // 1) 读取登录时保存的 roleIds
      let raw = null
      try { raw = localStorage.getItem('roleIds') } catch (e) { raw = null }
      let roleIds = []
      if (raw) {
        try { roleIds = JSON.parse(raw) } catch (e) { roleIds = [] }
      }

      // 2) 拉取完整的菜单树（服务端可能返回所有菜单）
      const resTree = await getMenuTree()
      const treeData = resTree && resTree.data
      const fullMenus = Array.isArray(treeData) ? treeData : (treeData?.data || treeData?.records || [])

      // 3) 优先从 localStorage 读取当前用户对象（后端在登录时应返回完整 UserEntity）
      let currentUser = null
      try {
        const raw = localStorage.getItem('currentUser')
        if (raw) currentUser = JSON.parse(raw)
      } catch (e) { currentUser = null }

      // 如果是超级管理员（isSuper === 1），直接展示所有菜单
      const isSuper = currentUser && (currentUser.isSuper === 1 || currentUser.isSuper === '1')
      if (isSuper) {
        menuTree.value = fullMenus
        // ensure SSE running to receive future updates
        startSSE()
        return
      }

      // 4) 如果没有 roleIds，则不展示任何菜单
      if (!roleIds || roleIds.length === 0) {
        menuTree.value = []
        startSSE()
        return
      }

      // 4) 获取每个角色的菜单 id 并合并
      const allowed = new Set()
      await Promise.all(roleIds.map(async (rid) => {
        try {
          const r = await getRoleMenus(rid)
          const d = r && r.data
          const list = Array.isArray(d) ? d : (d?.data || d?.records || [])
          for (const it of list) {
            if (!it) continue
            const id = typeof it === 'string' ? it : (it.id || it)
            if (id) allowed.add(String(id))
          }
          } catch (e) {
          // 忽略单个角色的错误
        }
      }))

      // 5) 根据 allowed 集合过滤 fullMenus
      if (allowed.size === 0) {
        menuTree.value = []
      } else {
        menuTree.value = filterTree(fullMenus, allowed)
      }

      // 启动 SSE 监听菜单变更替代原先的轮询
      startSSE()
    } catch (e) {
      menuTree.value = []
    }
  }
  function startSSE() {
    stopSSE()
    try {
      const token = localStorage.getItem('token')
      if (!token) return
      const url = (window.location.origin || '') + '/api/menus/stream?token=' + encodeURIComponent(token)
      _eventSource = new EventSource(url)
      _eventSource.addEventListener('open', () => {
        // reset reconnect strategy
        _reconnectDelay = 1000
        try { localStorage.setItem('menusSse', 'connected') } catch (e) {}
      })
      _eventSource.addEventListener('menu', async (e) => {
        // 服务端推送菜单变更，重新加载菜单
        try { await loadMenus() } catch (err) {}
      })
      _eventSource.addEventListener('ping', () => {
        // 心跳
      })
      _eventSource.addEventListener('error', (err) => {
        // 尝试重连
        stopSSE()
        if (_reconnectTimer) return
        _reconnectTimer = setTimeout(() => {
          _reconnectTimer = null
          _reconnectDelay = Math.min(60000, _reconnectDelay * 2)
          startSSE()
        }, _reconnectDelay)
      })
    } catch (e) {}
  }

  function stopSSE() {
    try { if (_eventSource) { _eventSource.close(); _eventSource = null } } catch (e) {}
    try { if (_reconnectTimer) { clearTimeout(_reconnectTimer); _reconnectTimer = null } } catch (e) {}
  }

  const reset = () => {
    menuTree.value = []
    roles.value = []
    stopSSE()
  }

  return {
    menuTree,
    roles,
    loadMenus,
    reset
  }
})

