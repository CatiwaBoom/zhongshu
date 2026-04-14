import { createRouter, createWebHistory } from 'vue-router'

import Layout from '@/layouts/Layout.vue'
import { usePermissionStore } from '@/stores/permission'

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
            ,
            // 系统管理模块：角色管理与系统菜单管理（路由已注册，菜单可由后端权限控制展示）
            {
                path: 'admin/roles',
                name: 'RoleManagement',
                component: () => import('@/views/admin/RoleManagement.vue'),
                meta: { title: '角色管理', icon: 'User' }
            },
            {
                path: 'admin/menus',
                name: 'SystemMenuManagement',
                component: () => import('@/views/admin/SystemMenuManagement.vue'),
                meta: { title: '系统菜单', icon: 'Document' }
            },
            {
                path: 'models',
                name: 'DataModelManagement',
                component: () => import('@/views/data-model/List.vue'),
                meta: { title: '数据模型管理', icon: 'Document' }
            }
            ,
            {
                path: 'models/create',
                name: 'DataModelCreate',
                component: () => import('@/views/data-model/Form.vue'),
                meta: { title: '创建数据模型', hidden: true }
            },
            {
                path: 'models/:id/edit',
                name: 'DataModelEdit',
                component: () => import('@/views/data-model/Form.vue'),
                meta: { title: '编辑数据模型', hidden: true }
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

router.beforeEach(async (to, from, next) => {
    const token = localStorage.getItem('token')
    if (to.name !== 'Login' && !token) {
        next({ name: 'Login' })
        return
    }

    // 若已登录但权限菜单尚未加载，主动拉取（支持页面刷新后仍能获取菜单）
    if (token) {
        try {
            const perm = usePermissionStore()
            if (!perm.menuTree || perm.menuTree.length === 0) {
                await perm.loadMenus()
            }
            // 若用户访问根路径，则根据权限菜单决定默认跳转
            if (to.path === '/') {
                // 优先跳转到角色管理或系统菜单管理（若用户有权限）
                const available = (perm.menuTree || []).flatMap(m => [m].concat(m.children || []))
                const hasRole = available.some(m => m.path === '/admin/roles')
                const hasMenu = available.some(m => m.path === '/admin/menus')
                if (hasRole) { next({ path: '/admin/roles' }); return }
                if (hasMenu) { next({ path: '/admin/menus' }); return }
                // 否则跳到第一个有 path 的菜单或回退到 dashboard
                const first = available.find(m => m.path)
                if (first && first.path) { next({ path: first.path }); return }
            }
        } catch (e) {
            // ignore
        }
    }

    next()
})

export default router
