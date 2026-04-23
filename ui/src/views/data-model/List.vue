<template>
  <div class="data-model-page">
    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-input v-model="keyword" placeholder="搜索模型名称/表名..." clearable class="toolbar-search" />
        <div class="toolbar-actions">
          <el-button type="primary" @click="goCreate">创建模型</el-button>
        </div>
      </div>
    </section>

    <section class="list-card">
      <div class="list-body" v-loading="loading">
        <el-table :data="list" style="width: 100%">
          <el-table-column prop="name" label="模型名称" />
          <el-table-column prop="tableName" label="物理表名" />
          <el-table-column label="操作" width="240">
            <template #default="{ row }">
              <el-button size="small" @click="edit(row)">编辑</el-button>
              <el-button size="small" @click="viewDdl(row)">生成 DDL</el-button>
              <el-button size="small" type="danger" @click="del(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <!-- SQL 结果对话框：先展示生成的建表语句，再可选择目标数据源执行（或仅查看/导出/复制） -->
    <el-dialog v-model="sqlDialogVisible" title="生成的建表语句" width="900px">
      <div>
        <div style="display:flex; gap:12px; align-items:center; margin-bottom:8px;">
          <div style="flex:1">
            <el-input type="textarea" v-model="sqlContent" rows="12" readonly />
          </div>
          <div style="width:320px">
            <div style="margin-bottom:8px; font-weight:600">选择目标数据源（可选，选择后可执行建表）</div>
            <el-select v-model="selectedDsId" placeholder="选择数据源" filterable style="width:100%">
              <el-option v-for="ds in dsList" :key="ds.id" :label="ds.name + ' | ' + (ds.url || '')" :value="ds.id" />
            </el-select>
            <!-- 当选择了数据源时，显示模式选择下拉 -->
            <div v-if="selectedDsId" style="margin-top:8px">
              <div style="margin-bottom:6px; font-weight:600">选择目标模式（Schema / Catalog）</div>
              <el-select v-model="selectedSchema" placeholder="选择模式" filterable style="width:100%" :disabled="dsLoading || checkingTable">
                <el-option v-for="s in schemaList" :key="s.name" :label="s.name" :value="s.name" />
              </el-select>
              <div v-if="tableExists === true" style="margin-top:6px; color:#e65; font-size:12px">该模式下已存在同名表，无法在目标数据源执行建表，请更换表名或选择其它模式。</div>
              <div v-else-if="checkingTable" style="margin-top:6px; color:#666; font-size:12px; display:flex; align-items:center">
                <Loading class="icon-spin" style="margin-right:6px" /> 正在检查目标模式下是否存在同名表...
              </div>
            </div>
            <div style="margin-top:8px; color:#999; font-size:12px">若不选择数据源，则仅显示 SQL；选择后可选择目标模式并点击下方“在目标数据源执行”（两者都选中时可执行）。</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="sqlDialogVisible = false">关闭</el-button>
        <el-button @click="copySqlToClipboard">复制</el-button>
        <el-button @click="downloadSqlFile">导出 .sql</el-button>
        <el-button type="primary" :disabled="!(selectedDsId && selectedSchema && tableExists === false)" @click="executeOnDs">在目标数据源执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { getModelList, deleteModel, generateDdl } from '@/api/dataModel'
import { getDataSourceList, getDataSourceSchemas, checkTableExists } from '@/api/dataSource'
import { Loading } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const keyword = ref('')

// 数据源与 SQL 弹窗相关状态
const dsList = ref([])
const selectedModel = ref(null)
const selectedDsId = ref(null)
const schemaList = ref([])
const selectedSchema = ref(null)
const dsLoading = ref(false)
const tableExists = ref(null) // null=unknown/checking, true=exists, false=not exists
const checkingTable = ref(false)

const load = async () => {
  loading.value = true
  try {
    const res = await getModelList({ keyword: keyword.value })
    const result = res.data
    if (result?.code === 200) {
      list.value = result.data || []
    } else {
      ElMessage.error(result?.msg || '加载失败')
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

const goCreate = () => router.push({ path: '/models/create' })
const edit = (row) => router.push({ path: `/models/${row.id}/edit` })

const viewDdl = async (row) => {
  // 先生成 SQL 并在弹窗中展示
  selectedModel.value = row
  selectedDsId.value = null
  try {
    const res = await generateDdl(row.id)
    const result = res.data
    if (result?.code === 200) {
      sqlContent.value = result.data || ''
      sqlDialogVisible.value = true
      // 重置数据源相关选择（每次打开弹窗均重新选择）
      selectedDsId.value = null
      selectedSchema.value = null
      schemaList.value = []
      // 弹窗打开后异步加载数据源列表（用于下拉选择）
      dsLoading.value = true
      try {
        const r2 = await getDataSourceList({ page: 1, size: 100 })
        const d2 = r2.data
          if (d2?.code === 200) {
              const payload = d2.data
              let listRaw = []
              if (payload && Array.isArray(payload.records)) listRaw = payload.records
              else if (Array.isArray(payload)) listRaw = payload
              else listRaw = payload?.data || []
              // 确保 id 为字符串以便 el-select 精确高亮
              dsList.value = listRaw.map(item => ({ ...item, id: item.id == null ? item.id : String(item.id) }))
            }
      } catch (e) {
        // ignore loading ds errors; user can still view SQL
      } finally {
        dsLoading.value = false
      }
    } else {
      ElMessage.error(result?.msg || '生成失败')
    }
  } catch (e) {
    ElMessage.error('生成失败')
  }
}

  const sqlDialogVisible = ref(false)
  const sqlContent = ref('')

  const copySqlToClipboard = async () => {
    try {
      if (navigator && navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(sqlContent.value)
      } else {
        // 兜底实现
        const ta = document.createElement('textarea')
        ta.value = sqlContent.value
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
      }
      ElMessage.success('已复制到剪贴板')
    } catch (e) {
      ElMessage.error('复制失败')
    }
  }

  const downloadSqlFile = () => {
    try {
      const blob = new Blob([sqlContent.value], { type: 'text/sql;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${selectedModel.value ? selectedModel.value.tableName : 'schema'}.sql`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch (e) {
      ElMessage.error('导出失败')
    }
  }

  // confirmGenerate 已被合并进 viewDdl，移除冗余函数

const del = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除模型「${row.name}」吗？`, '删除确认', { type: 'warning' })
    const res = await deleteModel(row.id)
    const result = res.data
    if (result?.code === 200) {
      ElMessage.success('删除成功')
      load()
    } else {
      ElMessage.error(result?.msg || '删除失败')
    }
  } catch (e) {}
}

// 通过 el-select 选择数据源，自动高亮已选项，无需额外的行点击处理

// 监听数据源选择，异步加载该数据源下的 schema 列表
watch(selectedDsId, async (val) => {
  selectedSchema.value = null
  schemaList.value = []
  if (!val) return
  try {
    const res = await getDataSourceSchemas(val)
    const r = res.data
    if (r?.code === 200) {
      // r.data 可能是数组或分页对象
      let payload = r.data
      let listRaw = []
      if (payload && Array.isArray(payload.records)) listRaw = payload.records
      else if (Array.isArray(payload)) listRaw = payload
      else listRaw = payload?.data || payload || []
      // 期望项形如 { name }
      schemaList.value = listRaw.map(item => ({ name: item.name || item }))
    } else {
      ElMessage.error(r?.msg || '加载模式列表失败')
    }
  } catch (e) {
    ElMessage.error('加载模式列表失败')
  }
})


// 监听模式选择，如果模式被选中则立即检查该模式下是否已存在同名表
watch(selectedSchema, async (val) => {
  if (!val || !selectedDsId.value || !selectedModel.value) {
    // 清除之前的存在性提示
    tableExists.value = null
    return
  }
  try {
    const tableName = selectedModel.value.tableName
    if (!tableName) {
      tableExists.value = null
      return
    }
    // 标记为检查中，禁用下拉并显示 spinner
    tableExists.value = null
    checkingTable.value = true
    const res = await checkTableExists(selectedDsId.value, val, tableName)
    const r = res.data
    if (r?.code === 200) {
      tableExists.value = !!(r.data && r.data.exists)
      if (tableExists.value) {
        ElMessage.warning(`目标模式 ${val} 下已存在同名表 ${tableName}，请更换表名或选择其它模式`)
      }
    } else {
      tableExists.value = null
      ElMessage.error(r?.msg || '检查表存在性失败')
    }
  } catch (e) {
    tableExists.value = null
    ElMessage.error('检查表存在性失败')
  } finally {
    checkingTable.value = false
  }
}, { immediate: false })
const executeOnDs = async () => {
  if (!selectedModel.value || !selectedDsId.value || !selectedSchema.value) return
  try {
    const res = await generateDdl(selectedModel.value.id, selectedDsId.value, true, selectedSchema.value)
    const result = res.data
    if (result?.code === 200) {
      ElMessage.success('执行成功')
      sqlDialogVisible.value = false
    } else {
      ElMessage.error(result?.msg || '执行失败')
    }
  } catch (e) {
    ElMessage.error('执行失败')
  }
}
</script>

<style scoped>
.toolbar-search { width: 320px }
.icon-spin { animation: spin 1s linear infinite; display: inline-block }
@keyframes spin { from { transform: rotate(0deg) } to { transform: rotate(360deg) } }
</style>

