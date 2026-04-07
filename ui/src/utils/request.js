import axios from 'axios'
import router from '@/router'
import { ElMessageBox } from 'element-plus'

// 用于直接调用刷新接口的 axios 实例（不走拦截器，避免递归）
const plainAxios = axios.create({ baseURL: '/api', timeout: 5000 })

// single-flight 刷新承诺：合并同一时间并发触发的刷新请求，避免重复发起
let refreshPromise = null

/**
 * 读取本地记录的最后活动时间（毫秒）。
 * 返回 0 表示不存在或解析失败。
 */
function getLastActivity() {
    try {
        const v = localStorage.getItem('lastActivity')
        return v ? parseInt(v, 10) : 0
    } catch (e) {
        return 0
    }
}

/**
 * 设置最后活动时间（毫秒）。
 */
function setLastActivity(ts) {
    try { localStorage.setItem('lastActivity', String(ts)) } catch (e) {}
}

/**
 * 确保在发起请求前 token 是“新鲜的”。
 * 策略：
 * - 当用户在 60 分钟内有操作时，尝试调用后端 /auth/refresh 进行 refresh token 的 rotation 并获取新的 access token
 * - 使用 single-flight 合并并发刷新请求
 * - 如果用户在 60 分钟内没有操作，则不做自动刷新（让 token 自然过期，触发重新登录流程）
 * - 避免对 /auth/refresh 本身重复刷新（检测 URL）
 */
async function ensureFreshToken(config) {
    // 避免在刷新接口本身再次触发刷新，导致循环
    if (config && config.url && config.url.includes('/auth/refresh')) return

    const token = localStorage.getItem('token')
    const refreshToken = localStorage.getItem('refreshToken')
    const sessionId = localStorage.getItem('sessionId')
    if (!token || !refreshToken || !sessionId) return

    const now = Date.now()
    const last = getLastActivity()
    const INACTIVITY_MS = 60 * 60 * 1000 // 60 分钟
    // 如果用户已超过 60 分钟无操作，不自动刷新（由后端返回 401，前端处理重新登录）
    if (now - last >= INACTIVITY_MS) return

    // 如果已有刷新在进行中，等待它完成（合并并发请求）
    if (refreshPromise) {
        try { await refreshPromise } catch (e) { /* 刷新失败由后续逻辑处理 */ }
        return
    }

    // 发起刷新请求（在单独的 axios 实例 plainAxios 上），并在 finally 中清理 refreshPromise
    refreshPromise = (async () => {
        try {
            const body = { sessionId: sessionId, refreshToken: refreshToken }
            const res = await plainAxios.post('/auth/refresh', body)
            const data = res && res.data
            const access = data?.accessToken || data?.data?.accessToken
            const newRefresh = data?.refreshToken || data?.data?.refreshToken
            const newSessionId = data?.sessionId || data?.data?.sessionId
            if (access) {
                try { localStorage.setItem('token', access) } catch (e) {}
            }
            if (newRefresh) {
                try { localStorage.setItem('refreshToken', newRefresh) } catch (e) {}
            }
            if (newSessionId) {
                try { localStorage.setItem('sessionId', newSessionId) } catch (e) {}
            }
            // 刷新成功后把最后活动时间设置为现在，表示用户刚刚有操作
            setLastActivity(Date.now())
        } finally {
            refreshPromise = null
        }
    })()

    try { await refreshPromise } catch (e) { /* downstream 会处理 401 */ }
}

const request = axios.create({
    baseURL: '/api',
    timeout: 10000
})

request.interceptors.request.use(
    async (config) => {
        try {
            await ensureFreshToken(config)
        } catch (e) {
            // ignore - if refresh failed, request may receive 401 which is handled globally
        }
        const token = localStorage.getItem('token')
        if (token) {
            config.headers = config.headers || {}
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error) => Promise.reject(error)
)

// 防止大量请求同时失败时重复弹出登录对话框
let isReloginShowing = false
function handleAuthExpired() {
    if (isReloginShowing) return
    isReloginShowing = true

    // 清理存储的认证信息（token / refresh / session / 活动时间）
    try {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
    } catch (e) {
        // 忽略错误
    }
    try {
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('sessionId')
        localStorage.removeItem('lastActivity')
    } catch (e) {}

    const redirectToLogin = () => {
        isReloginShowing = false
        // 使用命名路由 'Login' 进行跳转（优先），避免在历史中产生多余记录
        try {
            // 如果当前已经在 Login 页面则无需再次跳转
            const current = router.currentRoute && router.currentRoute.value
            if (current && current.name === 'Login') {
                return
            }

            // 使用 replace 避免污染历史，并稍微延迟以保证对话框关闭动画完成
            setTimeout(() => {
                try {
                    router.replace({ name: 'Login' })
                } catch (e) {
                    // 若路由不可用则回退到直接设置 location
                    window.location.href = '/login'
                    return
                }

                // 若 router.replace 未能产生可见路由变更（某些 SPA 需要强制刷新），则短延迟后硬跳转
                setTimeout(() => {
                    try {
                        const now = router.currentRoute && router.currentRoute.value
                        if (!now || now.name !== 'Login') {
                            window.location.href = '/login'
                        }
                    } catch (e) {
                        window.location.href = '/login'
                    }
                }, 250)
            }, 50)
        } catch (e) {
            // 最后的兜底：直接跳转
            window.location.href = '/login'
        }
    }

    // 使用 Element Plus 的对话框提示用户重新登录
    if (ElMessageBox && typeof ElMessageBox.alert === 'function') {
        ElMessageBox.alert('登录失效，请重新登录', '提示', {
            confirmButtonText: '确定',
            closeOnClickModal: false,
            closeOnPressEscape: false
        }).then(redirectToLogin).catch(redirectToLogin)
    } else {
        // 兜底：浏览器 alert
        try {
            window.alert('登录失效，请重新登录')
        } finally {
            redirectToLogin()
        }
    }
}

request.interceptors.response.use(
    (response) => {
            const data = response && response.data
            // 有些后端会在 HTTP 200 的响应中通过 data.code === 401 表示鉴权失败，兼容此种情况
            if (data && (data.code === 401 || data.code === '401')) {
                handleAuthExpired()
                return Promise.reject(new Error('Unauthorized'))
            }
        return response
    },
    (error) => {
        const status = error && error.response && error.response.status
        if (status === 401) {
            // 清理挂起的刷新承诺，避免后续请求被阻塞
            refreshPromise = null
            handleAuthExpired()
        }
        return Promise.reject(error)
    }
)

export default request