<template>
  <div class="workflow-def-page">
    <section class="toolbar">
      <el-input v-model="query.keyword" placeholder="按名称或编码搜索" clearable class="toolbar-search" @keyup.enter="loadList" />
      <div class="toolbar-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建流程</el-button>
        <el-button :icon="RefreshRight" @click="loadList">刷新</el-button>
      </div>
    </section>

    <section class="list-card" v-loading="loading">
      <el-table :data="pagedList" style="width: 100%">
        <el-table-column prop="name" label="流程名称" min-width="220" />
        <el-table-column prop="code" label="流程编码" width="210" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="goDesigner(row.id)">设计</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !pagedList.length" description="暂无流程定义" />

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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建流程定义' : '编辑流程定义'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入流程名称" />
        </el-form-item>
        <el-form-item label="流程编码" prop="code" v-if="dialogMode === 'create'">
          <el-input v-model="form.code" placeholder="为空则自动生成" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import {
  createProcessDefinition,
  deleteProcessDefinition,
  getNextDefinitionCode,
  getProcessDefinitionList,
  updateProcessDefinition
} from '@/api/workflowDefinition'

const router = useRouter()
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
  code: '',
  name: '',
  description: '',
  status: 1
})
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入流程名称', trigger: 'blur' }]
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

const loadList = async () => {
  loading.value = true
  try {
    const res = await getProcessDefinitionList({
      keyword: query.keyword || undefined,
      page: query.current,
      size: query.size
    })
    const result = res.data
    if (result?.code === 200) {
      const data = result.data
      // 兼容分页对象与旧数组结构，避免后端灰度期间页面异常
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
      ElMessage.error(result?.msg || '查询失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '查询失败')
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

const openCreate = async () => {
  dialogMode.value = 'create'
  Object.assign(form, emptyForm())
  try {
    const res = await getNextDefinitionCode()
    const result = res.data
    form.code = result?.data?.code || ''
  } catch (e) {
  }
  dialogVisible.value = true
}

const openEdit = (row) => {
  dialogMode.value = 'edit'
  Object.assign(form, emptyForm(), row)
  dialogVisible.value = true
}

const submit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    const payload = {
      code: form.code,
      name: form.name,
      description: form.description,
      status: form.status
    }
    const res =
      dialogMode.value === 'create'
        ? await createProcessDefinition(payload)
        : await updateProcessDefinition(form.id, payload)
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
    await ElMessageBox.confirm(`确认删除流程“${row.name}”吗？`, '删除确认', { type: 'warning' })
    const res = await deleteProcessDefinition(row.id)
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

const goDesigner = (id) => {
  router.push(`/workflow/designer/${id}`)
}

onMounted(loadList)
</script>

<style scoped>
.workflow-def-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.toolbar-search {
  width: 320px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.list-card {
  background: #fff;
  border: 1px solid #eef2f6;
  border-radius: 8px;
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
</style>

