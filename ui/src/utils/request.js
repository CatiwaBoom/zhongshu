import axios from 'axios'
import router from '@/router'
import { ElMessageBox } from 'element-plus'

const request = axios.create({
    baseURL: '/api',
    timeout: 10000
})

request.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers = config.headers || {}
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error) => Promise.reject(error)
)

// prevent multiple relogin dialogs when many requests fail at once
let isReloginShowing = false
function handleAuthExpired() {
    if (isReloginShowing) return
    isReloginShowing = true

    // clear stored auth info
    try {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
    } catch (e) {
        // ignore
    }

    const redirectToLogin = () => {
        isReloginShowing = false
        // use named route 'Login' defined in router
        try {
            // If already on Login, no need to navigate
            const current = router.currentRoute && router.currentRoute.value
            if (current && current.name === 'Login') {
                return
            }

            // Use replace to avoid polluting history and use a short delay so navigation happens after the dialog fully closes
            setTimeout(() => {
                try {
                    router.replace({ name: 'Login' })
                } catch (e) {
                    window.location.href = '/login'
                    return
                }

                // If router.replace didn't result in a visible route change (some apps need a reload),
                // fall back to a hard redirect after a short delay.
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
            // fallback: set location immediately
            window.location.href = '/login'
        }
    }

    if (ElMessageBox && typeof ElMessageBox.alert === 'function') {
        ElMessageBox.alert('登录失效，请重新登录', '提示', {
            confirmButtonText: '确定',
            closeOnClickModal: false,
            closeOnPressEscape: false
        }).then(redirectToLogin).catch(redirectToLogin)
    } else {
        // fallback
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
        // backend may use HTTP 200 with data.code === 401 to indicate auth error
        if (data && (data.code === 401 || data.code === '401')) {
            handleAuthExpired()
            return Promise.reject(new Error('Unauthorized'))
        }
        return response
    },
    (error) => {
        const status = error && error.response && error.response.status
        if (status === 401) {
            handleAuthExpired()
        }
        return Promise.reject(error)
    }
)

export default request