<template>
  <div>
    <!-- 当进入新建流程设计（id === 'new'）时，渲染全屏视图，隐藏侧栏与头部 -->
    <div v-if="isFullScreen" class="full-screen-wrapper">
      <router-view />
    </div>

    <div v-else class="admin-layout">
      <el-aside width="220px" class="sidebar">
        <el-menu
          :default-active="route.path"
          class="sidebar-menu"
          router
        >
          <el-menu-item index="/dashboard">
            <el-icon><House /></el-icon>
            <template #title>首页</template>
          </el-menu-item>

          <el-menu-item index="/datasource">
            <el-icon><Connection /></el-icon>
            <template #title>数据源管理</template>
          </el-menu-item>

          <el-menu-item index="/user">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>

          <el-menu-item index="/workflow/definition">
            <el-icon><Document /></el-icon>
            <template #title>流程定义</template>
          </el-menu-item>

          <el-menu-item index="/seatunnel/pipeline">
            <el-icon><Operation /></el-icon>
            <template #title>数据采集任务</template>
          </el-menu-item>

          <el-menu-item index="/seatunnel/datasync">
            <el-icon><Sort /></el-icon>
            <template #title>数据同步任务</template>
          </el-menu-item>

          <el-menu-item index="/notification/inbox">
            <el-icon><MessageBox /></el-icon>
            <template #title>站内信收件箱</template>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="header">
          <div class="header-actions">
            <el-popover
              v-model:visible="noticeVisible"
              placement="bottom-end"
              trigger="click"
              :width="380"
              @show="onNoticePopoverShow"
            >
              <template #reference>
                <el-badge :is-dot="notificationStore.hasUnread" class="notice-badge">
                  <button class="notice-entry" type="button">
                    <el-icon><Message /></el-icon>
                    <span>站内信</span>
                  </button>
                </el-badge>
              </template>

              <div class="notice-panel">
                <div class="notice-panel-header">
                  <span>站内信</span>
                  <el-button
                    text
                    type="primary"
                    :disabled="!notificationStore.hasUnread"
                    @click="markAllRead"
                  >
                    全部已读
                  </el-button>
                </div>

                <div v-if="!noticeList.length" class="notice-empty">暂无消息</div>
                <div v-else class="notice-list">
                  <div
                    v-for="item in noticeList"
                    :key="item.id"
                    class="notice-item"
                    @click="markOneRead(item)"
                  >
                    <div class="notice-title-row">
                      <span class="notice-title">{{ item.title || '系统通知' }}</span>
                      <span v-if="Number(item.isRead || 0) === 0" class="unread-dot" />
                    </div>
                    <p class="notice-content">{{ item.content || '-' }}</p>
                    <p class="notice-time">{{ formatTime(item.createdAt) }}</p>
                  </div>
                </div>
              </div>
            </el-popover>

            <el-button type="text" @click="logout">退出登录</el-button>
          </div>
        </el-header>

        <el-main class="main-content">
          <div class="content-panel">
            <router-view />
          </div>
        </el-main>
      </el-container>
    </div>
  </div>
</template>

<script setup>
import { House, Connection, User, Document, Operation, Sort, Message, MessageBox } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const route = useRoute()
const isFullScreen = computed(() => route.name === 'WorkflowDesigner')
const notificationStore = useNotificationStore()
const noticeList = computed(() => notificationStore.latestList)
const noticeVisible = ref(false)

const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${hh}:${mm}`
}

const markOneRead = async (item) => {
  if (!item?.id) return
  // 未读消息在这里统一跳转收件箱，让用户在完整上下文中处理消息
  noticeVisible.value = false
  await router.push({ name: 'NotificationInbox', query: { tab: Number(item.isRead || 0) === 0 ? 'unread' : 'all', focus: item.id } })
}

const markAllRead = async () => {
  await notificationStore.markAllRead()
  ElMessage.success('已全部标记为已读')
}

const onNoticePopoverShow = async () => {
  await notificationStore.refresh()
}

const logout = () => {
  notificationStore.stop()
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('sessionId')
  localStorage.removeItem('lastActivity')
  router.push('/login')
}

onMounted(() => {
  // 站内信入口放在全局布局中初始化，确保进入系统后就开始接收实时推送
  notificationStore.init()
})

onBeforeUnmount(() => {
  notificationStore.stop()
})
</script>

<style scoped lang="scss">
.admin-layout {
  height: 100vh;
  display: flex;
  background: #f5f7fa;
  overflow: hidden;

  .sidebar {
    background-color: #fff;
    color: #fff;
    border-right: 1px solid rgba(255, 255, 255, 0.08);
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);

    .sidebar-menu {
      height: 100%;
      border-right: none;
      background-color: transparent;
    }
  }

  .header {
    height: 56px;
    padding: 0 20px;
    background: #fff;
    border-bottom: 1px solid #e8edf3;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.04);

    display: flex;
    align-items: center;
    justify-content: flex-end;

    .header-actions {
      display: flex;
      align-items: center;
      gap: 14px;
    }
  }

  .main-content {
    padding: 16px;
    background-color: #f5f7fa;
    min-height: 0;
  }

  .content-panel {
    height: 100%;
    min-height: calc(100vh - 92px);
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 12px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    padding: 20px;
    box-sizing: border-box;
    overflow: auto;
  }
}

.notice-badge {
  :deep(.el-badge__content.is-fixed) {
    top: 9px;
    right: 6px;
  }
}

.notice-entry {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  border-radius: 999px;
  padding: 6px 12px;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    box-shadow: 0 8px 16px rgba(64, 158, 255, 0.3);
    transform: translateY(-1px);
  }
}

.notice-panel {
  .notice-panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 8px;
    border-bottom: 1px solid #eef2f7;
    font-weight: 600;
  }

  .notice-empty {
    padding: 24px 0;
    color: #909399;
    text-align: center;
  }

  .notice-list {
    max-height: 360px;
    overflow-y: auto;
    padding-top: 8px;
  }

  .notice-item {
    border-radius: 10px;
    border: 1px solid #edf2f7;
    padding: 10px;
    margin-bottom: 8px;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      border-color: #c6e2ff;
      background: #f5faff;
    }

    .notice-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 6px;
    }

    .notice-title {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }

    .unread-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #f56c6c;
      display: inline-block;
    }

    .notice-content {
      font-size: 13px;
      color: #606266;
      margin: 0;
      line-height: 1.5;
      display: -webkit-box;
      line-clamp: 2;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .notice-time {
      margin: 8px 0 0;
      font-size: 12px;
      color: #909399;
    }
  }
}

.full-screen-wrapper {
  height: 100vh;
  width: 100%;
  background: #fff;
}
</style>
