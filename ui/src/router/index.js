import { createRouter, createWebHistory } from 'vue-router'

import Layout from '@/layouts/Layout.vue'

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
            },
            {
                path: 'datasource',
                name: 'DataSourceManagement',
                component: () => import('@/views/data-source/DataSourceManagement.vue'),
                meta: { title: '数据源管理', icon: 'Connection' }
            },
            {
                path: 'file/platform',
                name: 'FilePlatform',
                component: () => import('@/views/file-platform/FilePlatform.vue'),
                meta: { title: '文件平台', icon: 'FolderOpened' }
            },
            {
                path: 'user',
                name: 'UserManagement',
                component: () => import('@/views/user/UserManagement.vue'),
                meta: { title: '用户管理', icon: 'User' }
            },
            {
                path: 'workflow/definition',
                name: 'WorkflowDefinitionManagement',
                component: () => import('@/views/workflow-definition/WorkflowDefinitionManagement.vue'),
                meta: { title: '流程定义', icon: 'Document' }
            },
            {
                path: 'workflow/designer/:id',
                name: 'WorkflowDesigner',
                component: () => import('@/views/workflow-design/WorkflowDesigner.vue'),
                meta: { title: '流程设计器', icon: 'Operation' }
            },
            {
                path: 'seatunnel/pipeline',
                name: 'SeatunnelPipelineManagement',
                component: () => import('@/views/seatunnel/SeatunnelPipelineManagement.vue'),
                meta: { title: '数据采集任务', icon: 'Operation' }
            },
            {
                path: 'seatunnel/datasync',
                name: 'DataSyncTaskManagement',
                component: () => import('@/views/seatunnel/DataSyncTaskManagement.vue'),
                meta: { title: '数据同步任务', icon: 'Sort' }
            },
            {
                path: 'notification/inbox',
                name: 'NotificationInbox',
                component: () => import('@/views/notification/Inbox.vue'),
                meta: { title: '站内信收件箱', icon: 'MessageBox' }
            }
        ]
    },
    {
        path: '/:pathMatch(.*)*',
        component: () => import('@/views/404/404.vue')
    }
]

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    if (to.name !== 'Login' && !token) {
        next({ name: 'Login' })
    } else {
        next()
    }
})

export default router
