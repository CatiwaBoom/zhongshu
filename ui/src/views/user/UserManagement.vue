<template>
  <div class="user-page">
    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-input
            v-model="query.keyword"
            class="toolbar-search"
            placeholder="搜索用户名/显示名/邮箱/手机号..."
            clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <div class="toolbar-actions">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>

          <el-select v-model="query.isSuper" placeholder="全部角色" clearable>
            <el-option label="超级管理员" :value="1" />
            <el-option label="普通用户" :value="0" />
          </el-select>

          <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增用户</el-button>
        </div>
      </div>
    </section>

    <section class="list-card">
      <div class="list-header">
        <div>
          <h2>用户列表</h2>
        </div>
        <div class="list-total">共 {{ displayTotal }} 条</div>
      </div>

      <div v-loading="loading" class="list-body">
        <template v-if="pagedList.length">
          <div class="source-list">
            <article v-for="item in pagedList" :key="item.id" class="source-item">
              <div class="source-item__top">
                <div class="source-basic">
                  <div class="source-icon source-icon--user">
                    {{ item.username ? item.username.charAt(0).toUpperCase() : 'U' }}
                  </div>

                  <div class="source-title-wrap">
                    <div class="source-title">{{ item.displayName || item.username || '--' }}</div>
                    <div class="source-subtitle">
                      {{ item.email || '--' }} · {{ item.mobile || '--' }}
                    </div>
                  </div>
                </div>

                <div class="source-ops">
                  <el-tag :type="item.status === 1 ? 'success' : 'info'" effect="light">
                    {{ item.status === 1 ? '启用' : '禁用' }}
                  </el-tag>

                  <el-tag :type="item.isSuper === 1 ? 'danger' : 'warning'" effect="plain">
                    {{ item.isSuper === 1 ? '超级管理员' : '普通用户' }}
                  </el-tag>

                  <div class="op-buttons">
                    <el-button circle :icon="Edit" @click="openEditDialog(item)" />
                    <el-button circle :icon="Delete" type="danger" plain @click="handleDelete(item)" />
                  </div>
                </div>
              </div>

              <div class="source-meta">
                <div><span>主键ID：</span>{{ item.id || '--' }}</div>
                <div><span>用户名：</span>{{ item.username || '--' }}</div>
                <div><span>创建时间：</span>{{ formatDate(item.createdAt) }}</div>
                <div><span>更新时间：</span>{{ formatDate(item.updatedAt) }}</div>
              </div>
            </article>
          </div>
        </template>

        <el-empty v-else description="暂无用户数据" />
      </div>

      <div class="list-footer" v-if="total !== null || (list && list.length > 0)">
        <div class="footer-text">显示 {{ pageStart }}-{{ pageEnd }} 条，共 {{ displayTotal }} 条</div>
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

    <div class="page-footer">© 2026 用户管理</div>

    <el-dialog
        v-model="dialogVisible"
        :title="dialogMode === 'create' ? '新增用户' : '编辑用户'"
        width="720px"
        destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" placeholder="请输入用户名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示名称" prop="displayName">
              <el-input v-model="form.displayName" placeholder="请输入显示名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="form.mobile" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" type="password" show-password placeholder="不修改请留空" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :label="1">启用</el-radio>
                <el-radio :label="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="是否超级管理员">
          <el-switch v-model="form.isSuper" active-value="1" inactive-value="0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">
          {{ dialogMode === 'create' ? '确认新增' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getUserList,
  createUser,
  updateUser,
  deleteUser
} from '@/api/user'

const loading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref()

const query = reactive({
  keyword: '',
  status: undefined,
  isSuper: undefined,
  current: 1,
  size: 10
})

const list = ref([])
const total = ref(null)

const emptyForm = () => ({
  id: '',
  username: '',
  password: '',
  displayName: '',
  email: '',
  mobile: '',
  status: 1,
  isSuper: 0
})

const form = reactive(emptyForm())

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
}

watch(
    () => [query.keyword, query.status, query.isSuper],
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

const formatDate = (val) => {
  if (val === null || val === undefined || val === '') return '--'
  // if number (ms) or numeric string
  let ts = val
  if (typeof val === 'string' && /^\d+$/.test(val)) ts = Number(val)
  if (typeof ts === 'number') {
    // if looks like seconds (10 digits) convert to ms
    if (ts > 0 && ts < 1e11) ts = ts * 1000
    const d = new Date(ts)
    if (!isNaN(d.getTime())) return d.toLocaleString()
    return String(val)
  }
  // try parse as ISO
  const d = new Date(val)
  if (!isNaN(d.getTime())) return d.toLocaleString()
  return String(val)
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

const resetForm = () => Object.assign(form, emptyForm())

const openCreateDialog = () => {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  dialogMode.value = 'edit'
  Object.assign(form, emptyForm(), row)
  dialogVisible.value = true
}

const loadList = async () => {
  loading.value = true
  try {
    const params = {
      keyword: query.keyword || undefined,
      status: query.status !== undefined ? query.status : undefined,
      isSuper: query.isSuper !== undefined ? query.isSuper : undefined,
      page: query.current,
      size: query.size
    }
    const res = await getUserList(params)
    const result = res.data
    // debug: print response structure to console for quick troubleshooting
    // eslint-disable-next-line no-console
    console.debug('getUserList result:', result)

    if (result?.code === 200) {
      const data = result.data
      // Case 1: data is Page-like object with records + total
        if (data && Array.isArray(data.records)) {
        list.value = data.records || []
        let t = undefined
        if (data.total !== undefined && data.total !== null) t = data.total
        else if (data.totalCount !== undefined && data.totalCount !== null) t = data.totalCount
        else if (data.totalElements !== undefined && data.totalElements !== null) t = data.totalElements
        const tn = Number(t)
        const reported = (t !== undefined && t !== null && Number.isFinite(tn)) ? tn : 0
        // ensure total is at least the loaded records length
        total.value = Math.max(reported, list.value ? list.value.length : 0)
      } else if (Array.isArray(data)) {
        // Case 2: backend returned raw array
        list.value = data
        total.value = data.length
      } else if (data && Array.isArray(data.data)) {
        // Case 3: nested data.data
        list.value = data.data
        total.value = (data.total !== undefined && data.total !== null) ? Number(data.total) : list.value.length
      } else {
        // unknown shape: try to infer
        list.value = []
        total.value = 0
      }
    } else {
      ElMessage.error(result?.msg || '获取用户失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '获取用户失败')
  } finally {
    loading.value = false
  }
}

const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    const payload = { ...form }
    const res = dialogMode.value === 'create'
        ? await createUser(payload)
        : await updateUser(form.id, payload)

    const result = res.data
    if (result?.code === 200) {
      ElMessage.success('操作成功')
      dialogVisible.value = false
      loadList()
    } else {
      ElMessage.error(result?.msg || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '操作失败')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.displayName || row.username}」吗？`, '删除确认', { type: 'warning' })
    const res = await deleteUser(row.id)
    const result = res.data
    if (result?.code === 200) {
      ElMessage.success('删除成功')
      loadList()
    } else {
      ElMessage.error(result?.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadList()
})

const displayTotal = computed(() => {
  if (total.value === null) return list.value ? list.value.length : '--'
  return total.value
})
</script>

<style scoped lang="scss">
.user-page {
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

.source-icon--user {
  background: rgba(64, 158, 255, 0.14);
  color: #409eff;
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