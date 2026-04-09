<template>
  <div class="inbox-page">
    <section class="toolbar">
      <el-input
        v-model.trim="keyword"
        placeholder="搜索消息标题/内容"
        clearable
        class="toolbar-search"
        @keyup.enter="handleSearch"
      />
      <div class="toolbar-actions">
        <el-radio-group v-model="activeTab" @change="switchTab">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="unread">未读({{ notificationStore.unreadCount }})</el-radio-button>
          <el-radio-button label="read">已读</el-radio-button>
          <el-radio-button label="system">系统公告</el-radio-button>
        </el-radio-group>
        <el-button @click="loadData">刷新</el-button>
        <el-button type="primary" plain @click="markAllRead">全部已读</el-button>
      </div>
    </section>

    <section class="list-card" v-loading="loading">
      <el-table
        :data="list"
        style="width: 100%"
        row-key="id"
        :row-class-name="rowClassName"
        @row-click="openMessage"
      >
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-badge is-dot :hidden="Number(row.isRead || 0) === 1">
              <el-tag :type="Number(row.isRead || 0) === 0 ? 'danger' : 'info'" effect="plain">
                {{ Number(row.isRead || 0) === 0 ? '未读' : '已读' }}
              </el-tag>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="360" show-overflow-tooltip />
        <el-table-column prop="bizType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.bizType === 'SYSTEM' ? 'warning' : 'success'">
              {{ row.bizType || 'NORMAL' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click.stop="openMessage(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !list.length" description="暂无站内信" />

      <div class="pager-wrapper">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :current-page="page"
          :page-size="size"
          @current-change="onPageChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getNotificationList } from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'

const route = useRoute()
const router = useRouter()
const notificationStore = useNotificationStore()

const loading = ref(false)
const list = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const activeTab = ref('all')
const focusedId = ref('')

const tabParams = computed(() => {
  if (activeTab.value === 'unread') return { isRead: 0 }
  if (activeTab.value === 'read') return { isRead: 1 }
  if (activeTab.value === 'system') return { bizType: 'SYSTEM' }
  return {}
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getNotificationList({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      ...tabParams.value
    })
    const result = res?.data
    if (result?.code !== 200) {
      ElMessage.error(result?.msg || '加载收件箱失败')
      return
    }
    const data = result?.data || {}
    list.value = Array.isArray(data.records) ? data.records : []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const switchTab = async () => {
  page.value = 1
  await loadData()
}

const handleSearch = async () => {
  page.value = 1
  await loadData()
}

const onPageChange = async (newPage) => {
  page.value = newPage
  await loadData()
}

const markAllRead = async () => {
  await notificationStore.markAllRead()
  await Promise.all([loadData(), notificationStore.refresh()])
}

const openMessage = async (item) => {
  if (!item?.id) return
  if (Number(item.isRead || 0) === 0) {
    await notificationStore.markRead(item.id)
    item.isRead = 1
  }

  // focus 参数用于刷新后定位消息行，避免长列表里用户找不到刚打开的消息
  focusedId.value = item.id
  router.replace({ name: 'NotificationInbox', query: { ...route.query, focus: item.id, tab: activeTab.value } })
}

const rowClassName = ({ row }) => {
  if (row.id === focusedId.value) return 'focused-row'
  if (Number(row.isRead || 0) === 0) return 'unread-row'
  return ''
}

const formatTime = (value) => {
  if (!value) return '--'
  return String(value).replace('T', ' ')
}

onMounted(async () => {
  focusedId.value = route.query?.focus || ''
  if (route.query?.tab && ['all', 'unread', 'read', 'system'].includes(route.query.tab)) {
    activeTab.value = route.query.tab
  }
  await Promise.all([loadData(), notificationStore.refresh()])
})

watch(
  () => route.query?.focus,
  (newFocus) => {
    focusedId.value = newFocus || ''
  }
)
</script>

<style scoped>
.inbox-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-search {
  width: 320px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.list-card {
  background: #fff;
}

.pager-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 12px;
}

:deep(.el-table .unread-row) {
  background: #f5f9ff;
}

:deep(.el-table .focused-row) {
  box-shadow: inset 3px 0 0 #409eff;
}
</style>
