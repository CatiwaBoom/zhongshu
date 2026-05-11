<template>
  <div class="system-page">
    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-input v-model="query.keyword" class="toolbar-search" placeholder="搜索名称/编码/地址..." clearable>
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <div class="toolbar-actions">
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">注册外部系统</el-button>
        </div>
      </div>
    </section>

    <div v-if="!showInterface">
      <section class="list-card">
      <div class="list-header">
        <h2>外部系统管理</h2>
        <div class="list-total">共 {{ displayTotal }} 条</div>
      </div>

      <div v-loading="loading" class="list-body">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="8" v-for="item in list" :key="item.id">
            <el-card class="card-hover" shadow="hover">
              <div class="card-head">
                <div>
                  <div class="card-title">{{ item.name }}</div>
                  <div class="card-subtitle">{{ item.systemCode }}</div>
                </div>
                <el-tag :type="statusTag(item._statusLabel, item.status)" effect="light">{{ item._statusLabel || '--' }}</el-tag>
              </div>

              <div class="card-body">
                <p class="desc">{{ item.description || '-' }}</p>
                <div class="meta">
                  <div><span class="muted">地址：</span>{{ item.address || '-' }}</div>
                  <div><span class="muted">端口：</span>{{ item.port || '-' }}</div>
                  <div><span class="muted">ID：</span>{{ item.id }}</div>
                </div>
              </div>

                          <div class="card-footer">
                            <el-button type="text" @click="viewItem(item)"><i class="fa fa-eye"></i> 查看</el-button>
                            <el-button type="text" @click="editItem(item)"><i class="fa fa-edit"></i> 编辑</el-button>
                            <el-button type="text" class="danger" @click="deleteItem(item)"><i class="fa fa-trash"></i> 删除</el-button>
                            <el-button type="text" @click="testStatus(item)"><i class="fa fa-plug"></i> 检测连通</el-button>
                            <el-button type="text" @click="openInterfacePage(item)"><i class="fa fa-external-link"></i> 接口管理</el-button>
                          </div>
            </el-card>
          </el-col>
        </el-row>

        <el-empty v-if="!list.length && !loading" description="暂无外部系统" />
      </div>

      <div class="list-footer" v-if="total !== null || list.length > 0">
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '注册外部系统' : '编辑外部系统'" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="系统编码" prop="systemCode">
              <el-input v-model="form.systemCode" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="地址" prop="address">
              <el-input v-model="form.address" placeholder="IP 或 域名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="端口" prop="port">
              <el-input-number v-model="form.port" :min="1" :max="65535" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="系统描述" prop="description">
          <el-input type="textarea" v-model="form.description" rows="3" />
        </el-form-item>

        <el-form-item label="附件管理">
          <div class="attachment-actions">
            <el-button type="primary" @click="openAttachmentDialog">从文件平台选择</el-button>
            <el-button @click="openFilePlatform">上传新附件</el-button>
          </div>

          <div class="attachments-list" v-if="form.attachmentIds && form.attachmentIds.length">
            <el-tag v-for="file in attachments" :key="file.id" closable @close="removeAttachment(file.id)">
              <span>{{ file.fileName }}</span>
              <button class="el-button el-button--text" @click.stop="downloadAttachment(file.id)">
                <i class="fa fa-download"></i>
              </button>
            </el-tag>
          </div>
        </el-form-item>

        <el-form-item label="系统状态">
          <el-button @click="checkFormStatus" size="small">检测系统连通性</el-button>
          <span class="ml-3">{{ form._statusLabel === undefined ? '-' : form._statusLabel }}</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">提交</el-button>
      </template>
    </el-dialog>

    </div>
    <!-- 当 showInterface 为 true 时，在列表区域替换为接口管理页面（类似路由切换效果） -->
    <div v-else class="list-card">
      <SystemApiManagement :system="currentSystem" :inline="true" @close="showInterface=false" />
    </div>


    <!-- 附件选择弹窗 -->
    <el-dialog v-model="attachDialogVisible" title="选择附件" width="800px">
          <div class="attach-search">
            <el-input v-model="attachQuery.keyword" placeholder="搜索文件名/MD5" clearable />
            <el-button type="primary" @click="loadFileObjects">查询</el-button>
          </div>

          <!-- 已选中文件预览 -->
          <div v-if="attachSelected && attachSelected.length" class="attach-selected-list" style="margin:8px 0">
            <span class="muted">已选文件：</span>
            <el-tag v-for="row in attachSelected" :key="row.id" style="margin-right:6px">{{ row.fileName }}</el-tag>
          </div>

          <el-table :data="attachList" style="width:100%" @selection-change="onAttachSelectionChange" :row-key="row => row.id">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="fileName" label="文件名" />
        <el-table-column prop="fileSize" label="大小" width="120" />
        <el-table-column prop="createdAt" label="上传时间" width="180" />
      </el-table>
      <div class="dialog-footer" slot="footer">
        <el-button @click="attachDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAttachSelection">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import * as api from '@/api/system.js'
import * as fileApi from '@/api/filePlatform.js'
import SystemApiManagement from './SystemApiManagement.vue'

const loading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)

const query = reactive({ keyword: '', current: 1, size: 12 })
const list = ref([])
const total = ref(null)

const emptyForm = () => ({ id: '', name: '', description: '', address: '', port: null, systemCode: '', attachmentIds: [] })
const form = reactive(emptyForm())
const attachments = ref([]) // selected file objects metadata
const attachTarget = ref('system') // 'system' or 'api'

const rules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  address: [{ required: true, message: '地址不能为空', trigger: 'blur' }],
  port: [{ required: true, message: '端口不能为空', trigger: 'change' }],
  systemCode: [{ required: true, message: '系统编码不能为空', trigger: 'blur' }]
}

const attachDialogVisible = ref(false)
const attachQuery = reactive({ keyword: '', page: 1, size: 10 })
const attachList = ref([])
const attachSelected = ref([])

const pageStart = computed(() => (query.current - 1) * query.size + 1)
const pageEnd = computed(() => Math.min(query.current * query.size, total.value || list.value.length))
const displayTotal = computed(() => (total.value === null ? list.value.length : total.value))


// ========== 接口管理入口控制（在父组件中触发，具体实现由 SystemApiManagement 组件负责） ==========

// 根据实时标签或状态返回 el-tag 的类型（颜色）
const statusTag = (label, s) => {
  // 优先使用实时检测的标签文本决定颜色
  if (label === '运行中') return 'success'
  if (label === '异常') return 'danger'
  // 回退使用数值或布尔状态：status === 1 或 true 表示运行中
  if (s === true) return 'success'
  return 'info'
}

const loadList = async () => {
  loading.value = true
  try {
    const params = { keyword: query.keyword || undefined, page: query.current, size: query.size }
    const res = await api.getSystemList(params)
    const data = res.data
    if (data?.code === 200) {
      const payload = data.data
      if (Array.isArray(payload.records)) {
        // 后端返回的 records 里包含 boolean 型的 status（true/false），
        // 组件期望使用 _statusLabel 显示中文标签（运行中/异常），
        // 所以在这里把 status 映射为 _statusLabel（除非后端已提供 _statusLabel）
        list.value = payload.records.map(item => {
          if (item._statusLabel === undefined) {
            if (item.status === true) item._statusLabel = '运行中'
            else if (item.status === false) item._statusLabel = '异常'
            else item._statusLabel = undefined
          }
          return item
        })
        total.value = payload.total || payload.totalElements || payload.totalCount || list.value.length
      } else if (Array.isArray(payload)) {
        list.value = payload
        total.value = list.value.length
      } else {
        list.value = []
        total.value = 0
      }
    } else {
      ElMessage.error(data?.msg || '查询失败')
    }
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}
const checkFormStatus = async () => {
  if (!form.address || !form.port) { ElMessage.warning('请先填写地址与端口'); return }
  try {
    const res = await api.checkSystemStatus(form.address, form.port)
    if (res.data?.code === 200) {
      form._statusLabel = res.data.data ? '运行中' : '异常'
    } else ElMessage.error(res.data?.msg || '检测失败')
  } catch (e) { ElMessage.error('检测失败') }
}

const submitForm = async () => {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  try {
    const payload = { name: form.name, description: form.description, address: form.address, port: form.port, systemCode: form.systemCode, attachmentIds: form.attachmentIds }
    let res
    if (dialogMode.value === 'create') res = await api.createSystem(payload)
    else res = await api.updateSystem(form.id, payload)
    if (res.data?.code === 200) {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      await loadList()
    } else ElMessage.error(res.data?.msg || '保存失败')
  } catch (e) { ElMessage.error('保存失败') }
}

const handlePageChange = (p) => { query.current = p; loadList() }
const handleSizeChange = (s) => { query.size = s; query.current = 1; loadList() }

onMounted(() => { loadList() })

// attachments logic
const openAttachmentDialog = (target = 'system') => { attachTarget.value = target; attachDialogVisible.value = true; loadFileObjects() }
const openFilePlatform = () => { window.open('/file/platform', '_blank') }

const loadFileObjects = async () => {
  try {
    const res = await fileApi.listFileObjects({ keyword: attachQuery.keyword, page: attachQuery.page, size: attachQuery.size })
    if (res.data?.code === 200) {
      const data = res.data.data
      attachList.value = Array.isArray(data.records) ? data.records : (Array.isArray(data) ? data : [])
    }
  } catch (e) { }
}

const onAttachSelectionChange = (rows) => { attachSelected.value = rows }

const confirmAttachSelection = () => {
  const ids = attachSelected.value.map(r => r.id)
  if (attachTarget.value === 'system') {
    form.attachmentIds = Array.from(new Set([...(form.attachmentIds || []), ...ids]))
    loadAttachmentsByIds(form.attachmentIds)
  }
  attachDialogVisible.value = false
}

const loadAttachmentsByIds = async (ids) => {
  attachments.value = []
  // simpler: request file list and filter by ids
  try {
    const res = await fileApi.listFileObjects({ page: 1, size: 200 })
    if (res.data?.code === 200) {
      const all = Array.isArray(res.data.data.records) ? res.data.data.records : (Array.isArray(res.data.data) ? res.data.data : [])
      attachments.value = all.filter(f => ids.includes(f.id))
    }
  } catch (e) { attachments.value = [] }
}

const removeAttachment = (id) => {
  form.attachmentIds = (form.attachmentIds || []).filter(x => x !== id)
  attachments.value = attachments.value.filter(a => a.id !== id)
}

const deleteItem = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除系统 ${row.name} 吗？`, '删除确认', { type: 'warning' })
    const res = await api.deleteSystem(row.id)
    if (res.data?.code === 200) {
      ElMessage.success('删除成功')
      await loadList()
    } else ElMessage.error(res.data?.msg || '删除失败')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('删除失败')
  }
}

// 新增：检测系统连通性（在列表卡片上触发）
const testStatus = async (row) => {
  try {
    const res = await api.checkSystemStatus(row.address, row.port)
    if (res.data?.code === 200) {
      const ok = res.data.data
      // 在 UI 上显示临时状态标签
      row._statusLabel = ok ? '运行中' : '异常'
      ElMessage.success(ok ? '连通' : '不通')
    } else {
      ElMessage.error(res.data?.msg || '检测失败')
    }
  } catch (e) {
    ElMessage.error('检测失败')
  }
}


// ========== 接口管理入口控制（在父组件中触发，具体实现由 SystemApiManagement 组件负责） ==========
const showInterface = ref(false)
const currentSystem = ref({})

const openInterfacePage = (row) => {
  currentSystem.value = row
  showInterface.value = true
}

</script>

<style scoped>
.system-page { display: flex; flex-direction: column; gap: 12px }
.toolbar-card { display:flex; justify-content:space-between }
.toolbar-search { width: 360px }
.card-head { display:flex; justify-content:space-between; align-items:center }
.card-title { font-weight:700 }
.card-subtitle { color:#909399; font-size:12px }
.desc { color:#606266; margin:8px 0 }
.meta { color:#909399; font-size:13px }
.card-footer { display:flex; gap:8px; justify-content:flex-end; margin-top:12px }
.attachments-list { margin-top:8px; display:flex; gap:6px; flex-wrap:wrap }
.muted { color:#909399 }
.danger { color: #f56c6c }

/* 覆盖式接口管理视图样式 */
.interface-overlay {
  position: fixed;
  inset: 0;
  background: #f7f8fa;
  padding: 28px 32px;
  z-index: 1200;
  overflow: auto;
}
.interface-overlay .card-hover { box-shadow: 0 6px 20px rgba(16,93,255,0.06); }
.interface-overlay .back-btn { position: relative; }
.method-badge { display:inline-block }

/* 覆盖视图右上角返回按钮 */
.overlay-back {
  position: fixed;
  top: 18px;
  right: 22px;
  z-index: 1300;
  background: white;
  border: 1px solid #e6e6e6;
  box-shadow: 0 6px 18px rgba(16,93,255,0.06);
}
</style>





