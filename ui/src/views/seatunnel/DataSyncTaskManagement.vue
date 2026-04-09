<template>
  <div class="page">
    <section class="toolbar">
      <el-input v-model="query.keyword" class="toolbar-search" placeholder="搜索任务名称..." clearable />
      <div class="toolbar-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增同步任务</el-button>
        <el-button :icon="RefreshRight" @click="loadList">刷新</el-button>
      </div>
    </section>

    <section class="card" v-loading="loading">
      <el-table :data="pagedList" style="width: 100%">
        <el-table-column prop="name" label="任务名称" min-width="200" />
        <el-table-column label="源表" min-width="240">
          <template #default="{ row }">
            {{ tablePath(row.sourceSchema, row.sourceTable) }}
          </template>
        </el-table-column>
        <el-table-column label="目标表" min-width="240">
          <template #default="{ row }">
            {{ tablePath(row.sinkSchema, row.sinkTable) }}
          </template>
        </el-table-column>
        <el-table-column prop="saveMode" label="写入策略" width="160" />
        <el-table-column prop="parallelism" label="并行度" width="100" />
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
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="VideoPlay" type="primary" @click="run(row)">运行</el-button>
            <el-button size="small" :icon="Document" @click="preview(row)">配置</el-button>
            <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :icon="Delete" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!pagedList.length && !loading" description="暂无同步任务" />

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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增同步任务' : '编辑同步任务'" width="980px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
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
            <el-form-item label="源数据源" prop="sourceDsId">
              <el-select v-model="form.sourceDsId" placeholder="请选择源数据源" filterable>
                <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标数据源" prop="sinkDsId">
              <el-select v-model="form.sinkDsId" placeholder="请选择目标数据源" filterable>
                <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">源表</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="源 Schema" prop="sourceSchema">
              <el-select v-model="form.sourceSchema" placeholder="可选" clearable filterable @change="onSourceSchemaChange">
                <el-option v-for="s in sourceSchemas" :key="s.name" :label="s.name" :value="s.name" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="源表" prop="sourceTable">
              <el-select v-model="form.sourceTable" placeholder="请选择源表" filterable>
                <el-option v-for="t in sourceTables" :key="t.name" :label="t.name" :value="t.name" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">目标表</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="目标 Schema" prop="sinkSchema">
              <el-select v-model="form.sinkSchema" placeholder="可选" clearable filterable @change="onSinkSchemaChange">
                <el-option v-for="s in sinkSchemas" :key="s.name" :label="s.name" :value="s.name" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标表" prop="sinkTable">
              <el-input v-model="form.sinkTable" placeholder="请输入目标表名（不存在可自动建表）" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="写入策略" prop="saveMode">
              <el-select v-model="form.saveMode" placeholder="请选择策略">
                <el-option label="APPEND_DATA" value="APPEND_DATA" />
                <el-option label="DROP_DATA" value="DROP_DATA" />
                <el-option label="ERROR_WHEN_DATA_EXISTS" value="ERROR_WHEN_DATA_EXISTS" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="并行度" prop="parallelism">
              <el-input-number v-model="form.parallelism" :min="1" :max="64" />
            </el-form-item>
          </el-col>
        </el-row>

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

    <el-dialog v-model="configVisible" title="配置预览（已脱敏）" width="980px" destroy-on-close>
      <el-input v-model="configText" type="textarea" :rows="20" readonly />
    </el-dialog>

    <el-dialog v-model="runVisible" title="运行日志" width="980px" destroy-on-close>
      <div class="run-head">
        <el-tag :type="statusTagType(execution.status)" effect="light">{{ execution.status || '--' }}</el-tag>
        <span class="run-text">ExecutionId：{{ execution.id || '--' }}</span>
        <span class="run-text">ExitCode：{{ execution.exitCode ?? '--' }}</span>
      </div>
      <el-input v-model="runLog" type="textarea" :rows="20" readonly />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshRight, Edit, Delete, VideoPlay, Document } from '@element-plus/icons-vue'
import { getDataSourceList } from '@/api/dataSource'
import { getSchemas, getTables } from '@/api/dataSourceMeta'
import {
  getDataSyncTaskList,
  createDataSyncTask,
  updateDataSyncTask,
  deleteDataSyncTask,
  previewDataSyncConfig,
  runDataSyncTask
} from '@/api/dataSync'
import { getSeatunnelExecution, getSeatunnelExecutionLog } from '@/api/seatunnel'

const loading = ref(false)
const list = ref([])
const total = ref(null)
const query = reactive({
  keyword: '',
  current: 1,
  size: 10
})

const dataSources = ref([])
const sourceSchemas = ref([])
const sourceTables = ref([])
const sinkSchemas = ref([])

const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref()

const emptyForm = () => ({
  id: '',
  name: '',
  sourceDsId: '',
  sourceSchema: '',
  sourceTable: '',
  sinkDsId: '',
  sinkSchema: '',
  sinkTable: '',
  saveMode: 'APPEND_DATA',
  parallelism: 1,
  status: 1,
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  sourceDsId: [{ required: true, message: '请选择源数据源', trigger: 'change' }],
  sourceTable: [{ required: true, message: '请选择源表', trigger: 'change' }],
  sinkDsId: [{ required: true, message: '请选择目标数据源', trigger: 'change' }],
  sinkTable: [{ required: true, message: '请输入目标表名', trigger: 'blur' }]
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

const tablePath = (schema, table) => {
  const s = String(schema || '').trim()
  const t = String(table || '').trim()
  return s ? `${s}.${t}` : t
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await getDataSyncTaskList({
      keyword: query.keyword || undefined,
      page: query.current,
      size: query.size
    })
    const result = res.data
    if (result?.code === 200) {
      const data = result.data
      // 兼容分页对象与旧数组结构，便于后端平滑升级
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
      ElMessage.error(result?.msg || '获取列表失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '获取列表失败')
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

const loadDataSources = async () => {
  try {
    const res = await getDataSourceList()
    const result = res.data
    if (result?.code === 200 && Array.isArray(result.data)) {
      dataSources.value = result.data
    }
  } catch (e) {
  }
}

const openCreate = () => {
  dialogMode.value = 'create'
  Object.assign(form, emptyForm())
  sourceSchemas.value = []
  sourceTables.value = []
  sinkSchemas.value = []
  dialogVisible.value = true
}

const openEdit = async (row) => {
  dialogMode.value = 'edit'
  Object.assign(form, emptyForm(), row)
  dialogVisible.value = true
  await refreshSourceSchemas()
  await refreshSourceTables()
  await refreshSinkSchemas()
}

const refreshSourceSchemas = async () => {
  if (!form.sourceDsId) {
    sourceSchemas.value = []
    return
  }
  try {
    const res = await getSchemas(form.sourceDsId)
    const result = res.data
    if (result?.code === 200 && Array.isArray(result.data)) {
      sourceSchemas.value = result.data
    } else {
      sourceSchemas.value = []
    }
  } catch (e) {
    sourceSchemas.value = []
  }
}

const refreshSinkSchemas = async () => {
  if (!form.sinkDsId) {
    sinkSchemas.value = []
    return
  }
  try {
    const res = await getSchemas(form.sinkDsId)
    const result = res.data
    if (result?.code === 200 && Array.isArray(result.data)) {
      sinkSchemas.value = result.data
    } else {
      sinkSchemas.value = []
    }
  } catch (e) {
    sinkSchemas.value = []
  }
}

const refreshSourceTables = async () => {
  if (!form.sourceDsId) {
    sourceTables.value = []
    return
  }
  try {
    const res = await getTables(form.sourceDsId, { schema: form.sourceSchema || '' })
    const result = res.data
    if (result?.code === 200 && Array.isArray(result.data)) {
      sourceTables.value = result.data
    } else {
      sourceTables.value = []
    }
  } catch (e) {
    sourceTables.value = []
  }
}

const onSourceSchemaChange = async () => {
  form.sourceTable = ''
  await refreshSourceTables()
}

const onSinkSchemaChange = async () => {
}

watch(
  () => form.sourceDsId,
  async () => {
    form.sourceSchema = ''
    form.sourceTable = ''
    await refreshSourceSchemas()
    await refreshSourceTables()
  }
)

watch(
  () => form.sinkDsId,
  async () => {
    form.sinkSchema = ''
    await refreshSinkSchemas()
  }
)

const submit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    const payload = { ...form }
    const res =
      dialogMode.value === 'create'
        ? await createDataSyncTask(payload)
        : await updateDataSyncTask(form.id, payload)
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
    const res = await deleteDataSyncTask(row.id)
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

const configVisible = ref(false)
const configText = ref('')

const preview = async (row) => {
  try {
    const res = await previewDataSyncConfig(row.id)
    const result = res.data
    if (result?.code === 200) {
      configText.value = result.data || ''
      configVisible.value = true
    } else {
      ElMessage.error(result?.msg || '预览失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '预览失败')
  }
}

const runVisible = ref(false)
const runLog = ref('')
const execution = reactive({ id: '', status: '', exitCode: null })

const statusTagType = (status) => {
  const s = String(status || '').toUpperCase()
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'CANCELLED' || s === 'CANCELED') return 'warning'
  if (s === 'RUNNING') return 'primary'
  return 'info'
}

let pollTimer = null

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const refreshExecution = async () => {
  if (!execution.id) return
  try {
    const res = await getSeatunnelExecution(execution.id)
    const result = res.data
    if (result?.code === 200 && result.data) {
      execution.id = result.data.id
      execution.status = result.data.status
      execution.exitCode = result.data.exitCode ?? null
    }
  } catch (e) {
  }

  try {
    const resLog = await getSeatunnelExecutionLog(execution.id, 400)
    const resultLog = resLog.data
    if (resultLog?.code === 200) {
      runLog.value = resultLog.data || ''
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

watch(runVisible, (v) => {
  if (!v) {
    stopPolling()
  }
})

const run = async (row) => {
  try {
    const res = await runDataSyncTask(row.id)
    const result = res.data
    if (result?.code === 200 && result.data) {
      execution.id = result.data.id
      execution.status = result.data.status
      execution.exitCode = result.data.exitCode ?? null
      runLog.value = ''
      runVisible.value = true
      await refreshExecution()
      startPolling()
      ElMessage.success(result?.msg || '已触发运行')
    } else {
      ElMessage.error(result?.msg || '运行失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '运行失败')
  }
}

onMounted(async () => {
  await loadDataSources()
  await loadList()
})
</script>

<style scoped lang="scss">
.page {
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

.card {
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

.run-head {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.run-text {
  color: #606266;
  font-size: 12px;
}
</style>

