<template>
  <div class="workflow-page">
    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-input
          v-model="query.keyword"
          class="toolbar-search"
          placeholder="搜索流程编码/名称..."
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <div class="toolbar-actions">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>

          <el-select v-model="query.latestOnly" placeholder="全部版本" clearable>
            <el-option label="仅最新版本" :value="1" />
            <el-option label="包含历史版本" :value="0" />
          </el-select>

          <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增流程定义</el-button>
        </div>
      </div>
    </section>

    <section class="list-card">
      <div class="list-header">
        <div>
          <h2>流程定义列表</h2>
          <p>字段映射 ProcessDefinitionEntity</p>
        </div>
        <div class="list-total">共 {{ filteredList.length }} 条</div>
      </div>

      <div v-loading="loading" class="list-body">
        <template v-if="pagedList.length">
          <div class="source-list">
            <article v-for="item in pagedList" :key="item.id" class="source-item">
              <div class="source-item__top">
                <div class="source-basic">
                  <div class="source-icon" :class="item.isLatest === 1 ? 'source-icon--latest' : 'source-icon--history'">
                    {{ item.isLatest === 1 ? 'L' : 'H' }}
                  </div>

                  <div class="source-title-wrap">
                    <div class="source-title">{{ item.name || '--' }}</div>
                    <div class="source-subtitle">
                      {{ item.code || '--' }} · v{{ item.versionNo }}
                    </div>
                  </div>
                </div>

                <div class="source-ops">
                  <el-tag :type="item.status === 1 ? 'success' : 'info'" effect="light">
                    {{ item.status === 1 ? '启用' : '停用' }}
                  </el-tag>

                  <el-tag :type="item.isLatest === 1 ? 'danger' : 'warning'" effect="plain">
                    {{ item.isLatest === 1 ? '最新' : '历史' }}
                  </el-tag>

                  <div class="op-buttons">
                    <el-button circle :icon="Edit" @click="openEditDialog(item)" />
                    <el-button circle :icon="SetUp" type="primary" plain @click="handleDesign(item)" />
                    <el-button circle :icon="Delete" type="danger" plain @click="handleDelete(item)" />
                  </div>
                </div>
              </div>

              <div class="source-meta">
                <div><span>主键ID：</span>{{ item.id || '--' }}</div>
                <div><span>版本号：</span>{{ item.versionNo || '--' }}</div>
                <div><span>创建时间：</span>{{ item.createdAt || '--' }}</div>
                <div><span>更新时间：</span>{{ item.updatedAt || '--' }}</div>
                <div class="meta-row"><span>备注：</span>{{ item.remark || '--' }}</div>
              </div>
            </article>
          </div>
        </template>

        <el-empty v-else description="暂无流程定义" />
      </div>

      <div class="list-footer" v-if="filteredList.length">
        <div class="footer-text">显示 {{ pageStart }}-{{ pageEnd }} 条，共 {{ filteredList.length }} 条</div>
        <el-pagination
          background
          small
          layout="prev, pager, next"
          :current-page="query.current"
          :page-size="query.size"
          :total="filteredList.length"
          @current-change="handlePageChange"
        />
      </div>
    </section>

    <div class="page-footer">© 2026 流程定义管理</div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增流程定义' : '编辑流程定义'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <!-- 显示生成后的流程编码预览（只读），仅在新增模式下可见 -->
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="流程编码" prop="code" v-if="dialogMode === 'create'">
              <el-input v-model="form.code" placeholder="生成后的编码预览（只读）" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="流程名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入流程名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :label="1">启用</el-radio>
                <el-radio :label="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="最新版本" prop="isLatest">
              <el-radio-group v-model="form.isLatest" :disabled="dialogMode === 'create'">
                <el-radio :label="1">是</el-radio>
                <el-radio :label="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">{{ dialogMode === 'create' ? '确认新增' : '保存修改' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, SetUp } from '@element-plus/icons-vue'
import {
  getProcessDefinitionList,
  updateProcessDefinition,
  deleteProcessDefinition,
  publishProcessDefinition,
  getNextDefinitionCode
} from '@/api/workflowDefinition'

const router = useRouter()
const loading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref()

const query = reactive({
  keyword: '',
  status: undefined,
  latestOnly: undefined,
  current: 1,
  size: 4
})

const list = ref([])

const emptyForm = () => ({
  id: '',
  code: '',
  name: '',
  versionNo: 1,
  status: 1,
  isLatest: 1,
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入流程名称', trigger: 'blur' }]
}

watch(
  () => [query.keyword, query.status, query.latestOnly],
  () => {
    query.current = 1
  }
)

const filteredList = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()

  return list.value.filter((item) => {
    const text = [item.code, item.name, item.remark].filter(Boolean).join(' ').toLowerCase()
    const matchKeyword = !keyword || text.includes(keyword)
    const matchStatus = query.status === undefined || Number(item.status) === Number(query.status)
    const matchLatest = query.latestOnly === undefined || Number(item.isLatest) === Number(query.latestOnly)
    return matchKeyword && matchStatus && matchLatest
  })
})

watch(filteredList, (value) => {
  const maxPage = Math.max(1, Math.ceil(value.length / query.size))
  if (query.current > maxPage) {
    query.current = maxPage
  }
})

const pagedList = computed(() => {
  const start = (query.current - 1) * query.size
  return filteredList.value.slice(start, start + query.size)
})

const pageStart = computed(() => (filteredList.value.length ? (query.current - 1) * query.size + 1 : 0))
const pageEnd = computed(() => Math.min(query.current * query.size, filteredList.value.length))

const handlePageChange = (page) => {
  query.current = page
}

const resetForm = () => {
  Object.assign(form, emptyForm())
}

const openCreateDialog = () => {
  // 新增流程定义直接跳转到流程设计器，新建模式使用特殊 id 'new'
  router.push({ name: 'WorkflowDesigner', params: { id: 'new' } })
}

const openEditDialog = (row) => {
  dialogMode.value = 'edit'
  Object.assign(form, emptyForm(), row)
  dialogVisible.value = true
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await getProcessDefinitionList()
    const result = res.data
    if (result?.code === 200 && Array.isArray(result.data)) {
      list.value = result.data
    } else {
      ElMessage.error(result?.msg || '获取流程定义失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '获取流程定义失败')
  } finally {
    loading.value = false
  }
}

// 生成流程编码，格式 WF_YYYYMMDD_XXX（XXX 三位自增序号）
const generateWorkflowCode = () => {
  const d = new Date()
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const dateStr = `${yyyy}${mm}${dd}`
  const prefix = `WF_${dateStr}_`

  // 统计已有同前缀的最大序号
  const seqs = list.value
    .map((it) => it.code || '')
    .filter((c) => c.startsWith(prefix))
    .map((c) => {
      const tail = c.substring(prefix.length)
      const n = parseInt(tail, 10)
      return Number.isFinite(n) ? n : 0
    })
  const maxSeq = seqs.length ? Math.max(...seqs) : 0
  const next = maxSeq + 1
  return `${prefix}${String(next).padStart(3, '0')}`
}

const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    let payload
    if (dialogMode.value === 'create') {
      // 使用后端发布接口，后端会在缺失 code 时生成唯一编码；优先使用表单中的预览编码
      payload = {
        code: form.code || undefined,
        name: form.name.trim(),
        status: form.status,
        isLatest: form.isLatest,
        remark: form.remark?.trim() || ''
      }
    } else {
      // 编辑时不允许修改编码，因此不在 payload 中包含 code
      payload = {
        name: form.name.trim(),
        status: form.status,
        isLatest: form.isLatest,
        remark: form.remark?.trim() || ''
      }
    }

    const res = dialogMode.value === 'create'
      ? await publishProcessDefinition(payload)
      : await updateProcessDefinition(form.id, payload)

    const result = res.data
    if (result?.code === 200) {
      ElMessage.success(result?.msg || '操作成功')
      dialogVisible.value = false
      await loadList()
    } else {
      ElMessage.error(result?.msg || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '操作失败')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除流程定义“${row.name}”吗？`, '删除确认', { type: 'warning' })
    const res = await deleteProcessDefinition(row.id)
    const result = res.data
    if (result?.code === 200) {
      ElMessage.success(result?.msg || '删除成功')
      await loadList()
    } else {
      ElMessage.error(result?.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.msg || '删除失败')
    }
  }
}

const handleDesign = (row) => {
  router.push({
    name: 'WorkflowDesigner',
    params: { id: row.id },
    query: { code: row.code, name: row.name, versionNo: row.versionNo }
  })
}

onMounted(() => {
  loadList()
})
</script>

<style scoped lang="scss">
.workflow-page {
  --primary: #409eff;
  --success: #67c23a;
  --warning: #e6a23c;
  --danger: #f56c6c;
  --text-main: #303133;
  --text-sub: #909399;
  --line: rgba(64, 158, 255, 0.14);
  --bg-card: linear-gradient(135deg, #ffffff 0%, #f6fbff 100%);
  --shadow: 0 12px 30px rgba(31, 45, 61, 0.08);

  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;

  color: var(--text-main);
}

.toolbar-card,
.list-card,
.source-item {
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: var(--shadow);
}

.toolbar-card {
  padding: 18px;
  margin-bottom: 18px;
  flex-shrink: 0;
}

.toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar-search {
  flex: 1;
  min-width: 260px;
  max-width: 420px;
}

.toolbar-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.list-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-header {
  padding: 18px 20px;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.list-header h2 {
  margin: 0;
  font-size: 18px;
}

.list-header p,
.list-total,
.footer-text,
.page-footer {
  margin: 4px 0 0;
  color: var(--text-sub);
  font-size: 13px;
}

.list-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.source-list {
  display: grid;
  gap: 16px;
  padding: 18px;
}

.source-item {
  padding: 18px;
  transition: all 0.2s ease;
}

.source-item:hover {
  transform: translateY(-2px);
  border-color: rgba(64, 158, 255, 0.28);
}

.source-item__top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.source-basic {
  display: flex;
  gap: 14px;
  align-items: center;
  min-width: 0;
}

.source-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}

.source-icon--latest {
  background: rgba(245, 108, 108, 0.14);
  color: #f56c6c;
}

.source-icon--history {
  background: rgba(230, 162, 60, 0.14);
  color: #e6a23c;
}

.source-title-wrap {
  min-width: 0;
}

.source-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2d3d;
  word-break: break-all;
}

.source-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-sub);
  word-break: break-all;
}

.source-ops {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.op-buttons {
  display: flex;
  gap: 8px;
}

.source-meta {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed rgba(64, 158, 255, 0.18);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
  font-size: 13px;
  color: #606266;
}

.meta-row {
  grid-column: 1 / -1;
}

.source-meta span {
  color: #909399;
}

.list-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.page-footer {
  flex-shrink: 0;
  text-align: center;
  padding: 18px 0 6px;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.14) inset !important;
}

:deep(.el-dialog) {
  border-radius: 18px;
  overflow: hidden;
}

@media (max-width: 768px) {
  .source-item__top {
    flex-direction: column;
  }

  .source-ops {
    width: 100%;
    justify-content: space-between;
  }

  .source-meta {
    grid-template-columns: 1fr;
  }
}
</style>

