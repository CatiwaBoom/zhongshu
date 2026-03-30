import { createRouter, createWebHistory } from 'vue-router'

// 布局组件
import Layout from '@/layouts/Layout.vue'

// 静态路由（无需权限）
const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/login/Login.vue'),
        meta: { title: '登录' }
    },
    {
        path: '/',
        component: Layout,
        redirect: '/dashboard',
        children: [
            {
                path: 'dashboard',
                name: 'Dashboard',
                component: () => import('@/views/dashboard/Dashboard.vue'),
                meta: { title: '首页', icon: 'el-icon-s-home' }
            }
        ]
    },
    // 404 页面
    {
        path: '/:pathMatch(.*)*',
        component: () => import('@/views/404/404.vue')
    }
]

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

// 路由守卫（示例：未登录跳转到登录页）
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    if (to.name !== 'Login' && !token) {
        next({ name: 'Login' })
    } else {
        next()
    }
})

export default router