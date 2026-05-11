<template>
  <div :class="inline ? 'interface-inline' : 'interface-overlay'">
    <!-- 右上角返回按钮（固定） -->
    <el-button class="overlay-back" @click="close" circle>
      <i class="fa fa-arrow-left"></i>
    </el-button>
    <div class="interface-header" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <div>
        <h2 style="margin:0">系统：<span style="color:#165DFF">{{ system.name }}</span> - 接口管理</h2>
      </div>
      <div style="display:flex;gap:8px;align-items:center">
        <el-button type="primary" @click="openApiModal">发布接口</el-button>
      </div>
    </div>

    <div class="api-list" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:16px">
      <div v-for="apiItem in apis" :key="apiItem.id" class="bg-white rounded-xl shadow-md p-4 card-hover" style="border:1px solid #f0f0f0">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:8px">
          <span :class="['method-badge', apiItem.method && apiItem.method.toUpperCase()==='GET' ? 'method-get' : 'method-post']">{{ apiItem.method || '-' }}</span>
          <div>
            <el-button type="text" @click="viewApi(apiItem)">查看详情</el-button>
          </div>
        </div>
        <h3 style="margin:0 0 6px;font-weight:700">{{ apiItem.apiName }}</h3>
        <p style="margin:0 0 8px;color:#909399;font-size:13px">{{ apiItem.description || '-' }}</p>
        <div style="background:#f7f7fa;padding:8px;border-radius:6px;font-family:monospace;font-size:12px;word-break:break-all">{{ apiItem.url }}</div>
        <div style="margin-top:10px;display:flex;gap:10px;font-size:13px">
          <el-button type="text" @click="editApi(apiItem)">编辑</el-button>
          <el-button type="text" class="danger" @click="deleteApi(apiItem)">删除</el-button>
        </div>
      </div>
    </div>

    <!-- 发布/编辑 接口弹窗 -->
    <el-dialog v-model="apiDialogVisible" :title="apiDialogMode==='create' ? '发布接口' : '编辑接口'" width="760px">
      <el-form ref="apiFormRef" :model="apiForm" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="接口名称">
              <el-input v-model="apiForm.apiName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="请求类型">
              <el-select v-model="apiForm.method">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="接口地址">
              <el-input v-model="apiForm.url" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="接口详情">
              <el-input type="textarea" v-model="apiForm.description" rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="请求示例">
              <el-input type="textarea" v-model="apiForm.requestExample" rows="6" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="响应示例">
              <el-input type="textarea" v-model="apiForm.responseExample" rows="6" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="请求字段注释">
              <el-input type="textarea" v-model="apiForm.reqFieldComment" rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="响应字段注释">
              <el-input type="textarea" v-model="apiForm.resFieldComment" rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="接口附件">
              <div style="display:flex;gap:8px">
                <el-button @click="openAttachmentDialog">从文件平台选择</el-button>
                <el-button @click="openFilePlatform">上传新附件</el-button>
              </div>
              <div style="margin-top:8px">
                <el-tag v-for="f in apiAttachments" :key="f.id" closable @close="removeApiAttachment(f.id)">{{ f.fileName }}</el-tag>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="apiDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApiForm">提交发布</el-button>
      </template>
    </el-dialog>

    <!-- API 详情弹窗 -->
    <el-dialog v-model="apiDetailVisible" title="接口详情" width="760px">
      <div v-if="apiDetail">
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:12px">
          <span :class="['method-badge', apiDetail.method && apiDetail.method.toUpperCase()==='GET' ? 'method-get' : 'method-post']">{{ apiDetail.method }}</span>
          <h3 style="margin:0">{{ apiDetail.apiName }}</h3>
        </div>
        <div style="background:#f7f7fa;padding:12px;border-radius:8px;margin-bottom:12px;font-family:monospace">{{ apiDetail.url }}</div>
        <p>{{ apiDetail.description }}</p>
        <el-divider />
        <div>
          <label class="font-bold">请求示例</label>
          <pre style="background:#f5f7fa;padding:10px;border-radius:6px">{{ apiDetail.requestExample }}</pre>
        </div>
        <div>
          <label class="font-bold">响应示例</label>
          <pre style="background:#f5f7fa;padding:10px;border-radius:6px">{{ apiDetail.responseExample }}</pre>
        </div>
      </div>
    </el-dialog>

    <!-- 附件选择弹窗（仅用于 API 附件） -->
    <el-dialog v-model="attachDialogVisible" title="选择附件" width="800px">
      <div class="attach-search">
        <el-input v-model="attachQuery.keyword" placeholder="搜索文件名/MD5" clearable />
        <el-button type="primary" @click="loadFileObjects">查询</el-button>
      </div>

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
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as fileApi from '@/api/filePlatform.js'
import * as apiApi from '@/api/systemApi.js'

const props = defineProps({ system: { type: Object, required: true }, inline: { type: Boolean, default: false } })
const emit = defineEmits(['close'])

const apis = ref([])

const apiDialogVisible = ref(false)
const apiDialogMode = ref('create')
const apiFormRef = ref(null)
const apiForm = reactive({ id: '', systemId: '', apiName: '', method: 'GET', url: '', description: '', requestExample: '', responseExample: '', reqFieldComment: '', resFieldComment: '', attachmentIds: [] })
const apiDetailVisible = ref(false)
const apiDetail = ref(null)

// Attach dialog (for API attachments)
const attachDialogVisible = ref(false)
const attachQuery = reactive({ keyword: '', page: 1, size: 10 })
const attachList = ref([])
const attachSelected = ref([])
const apiAttachments = ref([])

const loadApis = async (systemId) => {
  try {
    const res = await apiApi.listSystemApis(systemId)
    if (res.data?.code === 200) {
      apis.value = Array.isArray(res.data.data) ? res.data.data : (res.data.data?.records || [])
    } else {
      ElMessage.error(res.data?.msg || '加载接口失败')
    }
  } catch (e) { ElMessage.error('加载接口失败') }
}

watch(() => props.system && props.system.id, (id) => {
  if (id) loadApis(id)
}, { immediate: true })

const openApiModal = () => {
  apiDialogMode.value = 'create'
  Object.assign(apiForm, { id: '', systemId: props.system.id, apiName: '', method: 'GET', url: '', description: '', requestExample: '', responseExample: '', reqFieldComment: '', resFieldComment: '', attachmentIds: [] })
  apiAttachments.value = []
  apiDialogVisible.value = true
}

const editApi = (row) => {
  apiDialogMode.value = 'edit'
  Object.assign(apiForm, row)
  if (row.attachmentIds) {
    const ids = String(row.attachmentIds).split(',').map(s => s.trim()).filter(Boolean)
    apiForm.attachmentIds = ids
    loadApiAttachmentsByIds(ids)
  } else apiAttachments.value = []
  apiDialogVisible.value = true
}

const submitApiForm = async () => {
  try {
    // ensure attachmentIds is a comma separated string when sending to backend
    const attachmentsPayload = Array.isArray(apiForm.attachmentIds) ? apiForm.attachmentIds.join(',') : apiForm.attachmentIds || ''
    const payload = { systemId: apiForm.systemId, apiName: apiForm.apiName, method: apiForm.method, url: apiForm.url, description: apiForm.description, requestExample: apiForm.requestExample, responseExample: apiForm.responseExample, reqFieldComment: apiForm.reqFieldComment, resFieldComment: apiForm.resFieldComment, attachmentIds: attachmentsPayload }
    let res
    if (apiDialogMode.value === 'create') res = await apiApi.createSystemApi(payload)
    else res = await apiApi.updateSystemApi(apiForm.id, payload)
    if (res.data?.code === 200) {
      ElMessage.success('保存成功')
      apiDialogVisible.value = false
      loadApis(props.system.id)
    } else ElMessage.error(res.data?.msg || '保存失败')
  } catch (e) { ElMessage.error('保存失败') }
}

const deleteApi = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除接口 ${row.apiName} 吗？`, '删除确认', { type: 'warning' })
    const res = await apiApi.deleteSystemApi(row.id)
    if (res.data?.code === 200) {
      ElMessage.success('删除成功')
      loadApis(props.system.id)
    } else ElMessage.error(res.data?.msg || '删除失败')
  } catch (e) { }
}

const viewApi = async (row) => {
  try {
    const res = await apiApi.getSystemApi(row.id)
    if (res.data?.code === 200) {
      apiDetail.value = res.data.data
      apiDetailVisible.value = true
    } else ElMessage.error(res.data?.msg || '查询失败')
  } catch (e) { ElMessage.error('查询失败') }
}

// Attachment helpers for API
const openAttachmentDialog = () => { attachDialogVisible.value = true; loadFileObjects() }
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
  apiForm.attachmentIds = Array.from(new Set([...(apiForm.attachmentIds || []), ...ids]))
  loadApiAttachmentsByIds(apiForm.attachmentIds)
  attachDialogVisible.value = false
}

const loadApiAttachmentsByIds = async (ids) => {
  apiAttachments.value = []
  try {
    const res = await fileApi.listFileObjects({ page: 1, size: 200 })
    if (res.data?.code === 200) {
      const all = Array.isArray(res.data.data.records) ? res.data.data.records : (Array.isArray(res.data.data) ? res.data.data : [])
      apiAttachments.value = all.filter(f => ids.includes(f.id))
    }
  } catch (e) { apiAttachments.value = [] }
}

const removeApiAttachment = (id) => {
  apiForm.attachmentIds = (apiForm.attachmentIds || []).filter(x => x !== id)
  apiAttachments.value = apiAttachments.value.filter(a => a.id !== id)
}

const downloadAttachment = async (id) => {
  try {
    const res = await fileApi.downloadFileObject(id)
    const blob = res.data
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ''
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  } catch (e) { ElMessage.error('下载失败') }
}

const close = () => emit('close')
</script>

<style scoped>
.interface-overlay { position: fixed; inset: 0; background: #f7f8fa; padding: 28px 32px; z-index: 1200; overflow: auto; }
.overlay-back { position: fixed; top: 18px; right: 22px; z-index: 1300; background: white; border: 1px solid #e6e6e6; box-shadow: 0 6px 18px rgba(16,93,255,0.06); }
.method-badge { display:inline-block }

/* inline mode: render inside parent container instead of full-screen overlay */
.interface-inline { position: relative; inset: auto; background: transparent; padding: 0; }
.interface-inline .overlay-back { position: relative; top: auto; right: auto; margin-bottom: 12px; }
.interface-inline .api-list { margin-top: 8px; }
</style>

