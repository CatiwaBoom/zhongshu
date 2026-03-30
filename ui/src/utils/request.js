import axios from 'axios'

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

request.interceptors.response.use(
    (response) => response,
    (error) => Promise.reject(error)
)

export default request