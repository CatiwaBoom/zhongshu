<template>
  <div class="seatunnel-page">
    <section class="toolbar">
      <el-input
        v-model="query.keyword"
        class="toolbar-search"
        placeholder="搜索任务名称..."
        clearable
      />
      <div class="toolbar-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增任务</el-button>
        <el-button :icon="RefreshRight" @click="loadList">刷新</el-button>
      </div>
    </section>

    <section class="list-card" v-loading="loading">
      <el-table :data="pagedList" style="width: 100%">
        <el-table-column prop="name" label="任务名称" min-width="220" />
        <el-table-column prop="execMode" label="模式" width="110">
          <template #default="{ row }">
            <el-tag effect="plain">{{ (row.execMode || 'local').toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="VideoPlay" type="primary" @click="run(row)">运行</el-button>
            <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :icon="Delete" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!pagedList.length && !loading" description="暂无任务" />

      <div class="list-footer" v-if="total !== null || (list && list.length > 0)">
        <div class="footer-text">
          显示 {{ pageStart }}-{{ pageEnd }} 条，共 {{ total !== null ? total : (list ? list.length : '--') }} 条
        </div>
        <el-pagination
          background
          small
          layout="sizes, prev, pager, next"
          :current-page="query.current"
          :page-size="query.size"
          :total="total || 0"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增采集任务' : '编辑采集任务'"
      width="860px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :label="1">启用</el-radio>
                <el-radio :label="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="执行模式" prop="execMode">
              <el-select v-model="form.execMode" placeholder="请选择模式">
                <el-option label="LOCAL" value="local" />
                <el-option label="CLUSTER" value="cluster" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="集群名" prop="clusterName">
              <el-input v-model="form.clusterName" placeholder="cluster 模式可填" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="配置格式" prop="configFormat">
          <el-select v-model="form.configFormat" placeholder="请选择格式">
            <el-option label="HOCON" value="hocon" />
            <el-option label="JSON" value="json" />
          </el-select>
        </el-form-item>

        <el-form-item label="作业配置" prop="configContent">
          <el-input
            v-model="form.configContent"
            type="textarea"
            :rows="14"
            placeholder="粘贴 SeaTunnel 作业配置内容（hocon/json）"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="可选" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">
          {{ dialogMode === 'create' ? '确认新增' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logVisible" title="运行日志" width="980px" destroy-on-close>
      <div class="log-head">
        <div class="log-meta">
          <el-tag :type="statusTagType(execution.status)" effect="light">
            {{ execution.status || '--' }}
          </el-tag>
          <span class="log-text">ExecutionId：{{ execution.id || '--' }}</span>
          <span class="log-text">ExitCode：{{ execution.exitCode ?? '--' }}</span>
          <span class="log-text">Started：{{ formatTime(execution.startedAt) }}</span>
          <span class="log-text">Finished：{{ formatTime(execution.finishedAt) }}</span>
        </div>
        <div class="log-actions">
          <el-button size="small" :icon="RefreshRight" @click="refreshExecution">刷新</el-button>
          <el-button
            size="small"
            :icon="CircleClose"
            type="danger"
            plain
            :disabled="String(execution.status || '').toUpperCase() !== 'RUNNING'"
            @click="stop"
          >
            停止
          </el-button>
        </div>
      </div>

      <el-input v-model="logText" type="textarea" :rows="18" readonly />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshRight, Edit, Delete, VideoPlay, CircleClose } from '@element-plus/icons-vue'
import {
  getSeatunnelPipelineList,
  createSeatunnelPipeline,
  updateSeatunnelPipeline,
  deleteSeatunnelPipeline,
  runSeatunnelPipeline,
  getSeatunnelExecution,
  getSeatunnelExecutionLog,
  stopSeatunnelExecution
} from '@/api/seatunnel'

const loading = ref(false)
const list = ref([])
const total = ref(null)
const query = reactive({
  keyword: '',
  current: 1,
  size: 10
})

const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref()

const emptyForm = () => ({
  id: '',
  name: '',
  configFormat: 'hocon',
  configContent: '',
  execMode: 'cluster',
  clusterName: '',
  status: 1,
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  execMode: [{ required: true, message: '请选择执行模式', trigger: 'change' }],
  configFormat: [{ required: true, message: '请选择配置格式', trigger: 'change' }],
  configContent: [{ required: true, message: '请输入作业配置', trigger: 'blur' }]
}

watch(
  () => query.keyword,
  () => {
    query.current = 1
    loadList()
  }
)

const pagedList = computed(() => list.value)
const pageStart = computed(() => {
  const t = total.value
  if (t && t > 0) return (query.current - 1) * query.size + 1
  if (list.value && list.value.length > 0) return (query.current - 1) * query.size + 1
  return 0
})
const pageEnd = computed(() => {
  const t = total.value
  if (t && t > 0) return Math.min(query.current * query.size, t)
  if (list.value && list.value.length > 0) return pageStart.value + list.value.length - 1
  return 0
})

const formatTime = (value) => {
  if (!value) return '--'
  return String(value).replace('T', ' ')
}

const statusTagType = (status) => {
  const s = String(status || '').toUpperCase()
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'CANCELLED') return 'warning'
  if (s === 'RUNNING') return 'primary'
  return 'info'
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await getSeatunnelPipelineList({
      keyword: query.keyword || undefined,
      page: query.current,
      size: query.size
    })
    const result = res.data
    if (result?.code === 200) {
      const data = result.data
      // 兼容分页对象与旧数组结构，避免接口升级过程中的页面空白
      if (data && Array.isArray(data.records)) {
        list.value = data.records || []
        total.value = Number(data.total ?? list.value.length)
      } else if (Array.isArray(data)) {
        list.value = data
        total.value = data.length
      } else {
        list.value = []
        total.value = 0
      }
    } else {
      ElMessage.error(result?.msg || '获取任务列表失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '获取任务列表失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page) => {
  query.current = page
  loadList()
}

const handleSizeChange = (size) => {
  query.size = size
  query.current = 1
  loadList()
}

const openCreate = () => {
  dialogMode.value = 'create'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

const openEdit = (row) => {
  dialogMode.value = 'edit'
  Object.assign(form, emptyForm(), row)
  dialogVisible.value = true
}

const submit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    const payload = { ...form }
    const res =
      dialogMode.value === 'create'
        ? await createSeatunnelPipeline(payload)
        : await updateSeatunnelPipeline(form.id, payload)
    const result = res.data
    if (result?.code === 200) {
      ElMessage.success(result?.msg || '操作成功')
      dialogVisible.value = false
      await loadList()
    } else {
      ElMessage.error(result?.msg || '操作失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '操作失败')
  }
}

const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除任务“${row.name}”吗？`, '删除确认', { type: 'warning' })
    const res = await deleteSeatunnelPipeline(row.id)
    const result = res.data
    if (result?.code === 200) {
      ElMessage.success(result?.msg || '删除成功')
      await loadList()
    } else {
      ElMessage.error(result?.msg || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.msg || '删除失败')
    }
  }
}

const logVisible = ref(false)
const logText = ref('')
const execution = reactive({
  id: '',
  status: '',
  exitCode: null,
  startedAt: '',
  finishedAt: ''
})

let pollTimer = null

const setExecution = (data) => {
  Object.assign(execution, {
    id: data?.id || '',
    status: data?.status || '',
    exitCode: data?.exitCode ?? null,
    startedAt: data?.startedAt || '',
    finishedAt: data?.finishedAt || ''
  })
}

const refreshExecution = async () => {
  if (!execution.id) return
  try {
    const res = await getSeatunnelExecution(execution.id)
    const result = res.data
    if (result?.code === 200 && result.data) {
      setExecution(result.data)
    }
  } catch (e) {
  }

  try {
    const resLog = await getSeatunnelExecutionLog(execution.id, 400)
    const resultLog = resLog.data
    if (resultLog?.code === 200) {
      logText.value = resultLog.data || ''
    }
  } catch (e) {
  }
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    await refreshExecution()
    const s = String(execution.status || '').toUpperCase()
    if (s && s !== 'RUNNING') {
      stopPolling()
    }
  }, 2000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const run = async (row) => {
  try {
    const res = await runSeatunnelPipeline(row.id)
    const result = res.data
    if (result?.code === 200 && result.data) {
      setExecution(result.data)
      logText.value = ''
      logVisible.value = true
      await refreshExecution()
      startPolling()
      ElMessage.success(result?.msg || '已触发运行')
    } else {
      ElMessage.error(result?.msg || '触发运行失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '触发运行失败')
  }
}

const stop = async () => {
  if (!execution.id) return
  try {
    const res = await stopSeatunnelExecution(execution.id)
    const result = res.data
    if (result?.code === 200) {
      ElMessage.success(result?.msg || '停止成功')
      await refreshExecution()
    } else {
      ElMessage.error(result?.msg || '停止失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '停止失败')
  }
}

watch(logVisible, (v) => {
  if (!v) {
    stopPolling()
  }
})

onMounted(() => {
  loadList()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped lang="scss">
.seatunnel-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-search {
  width: 360px;
  max-width: 100%;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
}

.list-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 12px;
}

.list-footer {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.footer-text {
  color: #909399;
  font-size: 13px;
}

.log-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.log-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.log-text {
  color: #606266;
  font-size: 12px;
}

.log-actions {
  display: flex;
  gap: 10px;
}
</style>
