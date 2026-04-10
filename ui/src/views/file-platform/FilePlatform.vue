<template>
  <div class="upload-page">
    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-input v-model="query.keyword" class="toolbar-search" placeholder="搜索文件名/MD5..." clearable>
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <div class="toolbar-actions">
          <el-button type="primary" :icon="Plus" @click="openUploadDialog">新增文件</el-button>
          <el-button type="primary" plain @click="loadFileList">刷新</el-button>
        </div>
      </div>
    </section>

    <el-dialog v-model="uploadDialogVisible" title="上传文件" width="720px" :before-close="onUploadDialogClose">
      <div class="upload-dialog-body">
        <div class="upload-box-small" :class="{ active: dragActive }" @click="openSelect" @dragover.prevent="onDragOver" @dragleave.prevent="onDragLeave" @drop.prevent="onDrop">
          <div class="upload-icon">📁</div>
          <div>点击选择或拖拽文件</div>
          <div class="upload-tips">支持大文件、自动分片、加密存储 <span class="tip-label">安全加密</span></div>
          <input id="file-input" ref="fileInputRef" type="file" @change="onSelectChange" />
        </div>

        <div class="upload-status-row">
          <div v-show="selectedFile" class="file-info-small">
            <div class="file-name">{{ selectedFile?.name }}</div>
            <div class="progress-container">
              <div class="progress-bar" :style="{ width: `${progress}%` }" />
            </div>
            <div class="status-text">{{ statusText }}</div>
          </div>

          <div class="upload-actions">
            <el-button :disabled="!selectedFile || uploading" type="primary" @click="startUpload">{{ uploading ? '上传中...' : '开始上传' }}</el-button>
            <el-button @click="clearSelection">清除</el-button>
          </div>
        </div>

        <el-form ref="uploadFormRef" :model="uploadForm" label-width="100px" style="margin-top:12px">
          <el-form-item label="文件名">
            <el-input v-model="uploadForm.fileName" readonly />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="uploadForm.description" placeholder="可选：文件用途说明" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmUploadDialog">确定（保存表单）</el-button>
      </template>
    </el-dialog>

      <div class="table-card">
      <div class="table-header">
        <h3>文件管理表</h3>
        <el-button type="primary" plain @click="loadFileList">刷新</el-button>
      </div>
      <el-table :data="fileList" border style="width: 100%">
        <el-table-column prop="fileName" label="文件名" min-width="220" />
        <el-table-column prop="fileMd5" label="MD5" min-width="280" />
        <el-table-column label="大小" width="130">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="uploadCount" label="上传次数" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="download(row)">下载</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="list-footer" style="margin-top:12px; display:flex; align-items:center; justify-content:space-between">
        <div class="footer-text">显示 {{ pageStart }}-{{ pageEnd }} 条，共 {{ total !== null ? total : (fileList ? fileList.length : '--') }} 条</div>
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
    </div>
  </div>
</template>

<script setup>
import SparkMD5 from 'spark-md5'
import { onMounted, ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { downloadFileObject, getUploadStatus, initFileUpload, listFileObjects, mergeFileUpload, uploadFileChunk, deleteFileObject } from '@/api/filePlatform'

const CHUNK_SIZE = 10 * 1024 * 1024

// keyword is stored in query.keyword

const fileInputRef = ref(null)
const selectedFile = ref(null)
const dragActive = ref(false)
const uploading = ref(false)
const progress = ref(0)
const statusText = ref('准备上传...')
const successVisible = ref(false)
const errorVisible = ref(false)
const errorText = ref('上传失败，请重试')
const currentStage = ref('idle')

const uploadDialogVisible = ref(false)
const uploadFormRef = ref(null)
const uploadForm = reactive({ fileName: '', description: '' })

const fileList = ref([])
const total = ref(null)
const query = reactive({ keyword: '', current: 1, size: 10 })

const openSelect = () => {
  fileInputRef.value?.click()
}

const openUploadDialog = () => {
  uploadDialogVisible.value = true
}

const onUploadDialogClose = () => {
  // clear selection when dialog closes
  clearSelection()
  uploadDialogVisible.value = false
}

const clearSelection = () => {
  selectedFile.value = null
  progress.value = 0
  statusText.value = '准备上传...'
  uploadForm.fileName = ''
  uploadForm.description = ''
}

const onDragOver = () => {
  dragActive.value = true
}

const onDragLeave = () => {
  dragActive.value = false
}

const onDrop = (e) => {
  dragActive.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) {
    handleSelect(file)
  }
}

const onSelectChange = (e) => {
  const file = e.target?.files?.[0]
  if (file) {
    handleSelect(file)
  }
}

const handleSelect = (file) => {
  selectedFile.value = file
  progress.value = 0
  statusText.value = '准备上传...'
  successVisible.value = false
  errorVisible.value = false
}

const computeFileMD5 = async (file) => {
  const spark = new SparkMD5.ArrayBuffer()
  const chunks = Math.ceil(file.size / CHUNK_SIZE)

  for (let index = 0; index < chunks; index += 1) {
    const start = index * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, file.size)
    const buffer = await file.slice(start, end).arrayBuffer()
    spark.append(buffer)
  }

  return spark.end()
}

const startUpload = async () => {
  if (!selectedFile.value || uploading.value) {
    return
  }

  try {
    uploading.value = true
    successVisible.value = false
    errorVisible.value = false
    errorText.value = '上传失败，请重试'
    currentStage.value = 'md5'

    const file = selectedFile.value
    statusText.value = '正在计算文件指纹...'
    const fileMd5 = await computeFileMD5(file)
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

    currentStage.value = 'init'
    const initRes = await initFileUpload({
      fileName: file.name,
      contentType: file.type || 'application/octet-stream',
      fileSize: file.size,
      fileMd5,
      chunkSize: CHUNK_SIZE,
      totalChunks
    })

    const initData = initRes?.data?.data
    if (!initData) {
      errorVisible.value = true
      errorText.value = '初始化上传失败'
      return
    }

    if (initData.instantUpload) {
      progress.value = 100
      statusText.value = '秒传成功，文件已存在且已建立文件对象'
      successVisible.value = true
      await loadFileList()
      return
    }

    const uploadId = initData.uploadId
    const uploadedSet = new Set(initData.uploadedChunks || [])

    // 再查一次状态，保证前后端断点视图一致
    const statusRes = await getUploadStatus(uploadId)
    const statusChunks = statusRes?.data?.data?.uploadedChunks || []
    statusChunks.forEach((idx) => uploadedSet.add(idx))

    let uploadedCount = uploadedSet.size
    progress.value = Math.floor((uploadedCount / totalChunks) * 100)

    for (let i = 0; i < totalChunks; i += 1) {
      if (uploadedSet.has(i)) {
        continue
      }

      const start = i * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const chunk = file.slice(start, end)

      const formData = new FormData()
      formData.append('uploadId', uploadId)
      formData.append('md5', fileMd5)
      formData.append('chunkIndex', String(i))
      formData.append('totalChunks', String(totalChunks))
      formData.append('file', chunk)

      currentStage.value = 'chunk'
      await uploadFileChunk(formData)
      uploadedCount += 1
      const percent = Math.floor((uploadedCount / totalChunks) * 100)
      progress.value = percent
      statusText.value = `分片上传中：${uploadedCount}/${totalChunks} (${percent}%)`
    }

    statusText.value = '正在合并文件并加密存储...'
    currentStage.value = 'merge'
    await mergeFileUpload({
      uploadId,
      md5: fileMd5,
      fileName: file.name,
      totalChunks,
      contentType: file.type || 'application/octet-stream'
    })

    progress.value = 100
    statusText.value = '上传完成！文件已加密存储'
    successVisible.value = true
    ElMessage.success('上传成功')
    // 自动回填表单并保留在弹框中，用户可以补充描述后保存
    uploadForm.fileName = file.name
    // 触发列表刷新
    await loadFileList()
  } catch (e) {
    errorVisible.value = true
    const timeout = e?.code === 'ECONNABORTED' || String(e?.message || '').toLowerCase().includes('timeout')
    if (timeout && currentStage.value === 'merge') {
      errorText.value = '文件已上传完成，服务端合并耗时较长，请稍后刷新文件列表确认结果'
    } else if (timeout && currentStage.value === 'chunk') {
      errorText.value = '分片上传超时，可点击开始上传继续断点续传'
    } else {
      errorText.value = e?.response?.data?.msg || e?.message || '上传失败，请重试'
    }
  } finally {
    uploading.value = false
    currentStage.value = 'idle'
  }
}

const confirmUploadDialog = async () => {
  // 在弹框点击确定，关闭弹框并保存表单（这里示例仅关闭弹框并刷新列表）
  uploadDialogVisible.value = false
  ElMessage.success('保存成功')
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认物理删除文件「${row.fileName}」吗？删除后无法恢复。`, '删除确认', { type: 'warning' })
    const res = await deleteFileObject(row.id)
    const result = res.data
    if (result?.code === 200) {
      ElMessage.success('删除成功')
      await loadFileList()
    } else {
      ElMessage.error(result?.msg || '删除失败')
    }
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error('删除失败')
  }
}

const loadFileList = async () => {
  try {
    const params = {
      keyword: query.keyword || undefined,
      page: query.current,
      size: query.size
    }
    const res = await listFileObjects(params)
    const result = res?.data
    if (result?.code === 200) {
      const data = result.data
      if (data && Array.isArray(data.records)) {
        fileList.value = data.records || []
        let t = undefined
        if (data.total !== undefined && data.total !== null) t = data.total
        else if (data.totalCount !== undefined && data.totalCount !== null) t = data.totalCount
        else if (data.totalElements !== undefined && data.totalElements !== null) t = data.totalElements
        const tn = Number(t)
        const reported = (t !== undefined && t !== null && Number.isFinite(tn)) ? tn : 0
        total.value = Math.max(reported, fileList.value ? fileList.value.length : 0)
      } else if (Array.isArray(data)) {
        fileList.value = data
        total.value = data.length
      } else if (data && Array.isArray(data.data)) {
        fileList.value = data.data
        total.value = (data.total !== undefined && data.total !== null) ? Number(data.total) : fileList.value.length
      } else {
        fileList.value = []
        total.value = 0
      }
    } else {
      ElMessage.error(result?.msg || '获取文件列表失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '查询文件列表失败')
  }
}

const handlePageChange = (page) => {
  query.current = page
  loadFileList()
}

const handleSizeChange = (size) => {
  query.size = size
  query.current = 1
  loadFileList()
}

const pageStart = computed(() => {
  const t = total.value
  if (t && t > 0) return (query.current - 1) * query.size + 1
  if (fileList.value && fileList.value.length > 0) return (query.current - 1) * query.size + 1
  return 0
})

const pageEnd = computed(() => {
  const t = total.value
  if (t && t > 0) return Math.min(query.current * query.size, t)
  if (fileList.value && fileList.value.length > 0) return pageStart.value + fileList.value.length - 1
  return 0
})

const formatSize = (size) => {
  const n = Number(size || 0)
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(1)} MB`
  return `${(n / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

const download = async (row) => {
  try {
    const res = await downloadFileObject(row.id)
    const blob = new Blob([res.data], { type: row.contentType || 'application/octet-stream' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName || 'download.bin'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '下载失败')
  }
}

onMounted(() => {
  loadFileList()
})
</script>

<style scoped>
/* Reuse toolbar styles from UserManagement for consistent look */
.toolbar-card,
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar-search {
  width: 320px;
  min-width: 160px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

/* Compact upload dialog styles */
.upload-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upload-box-small {
  border: 2px dashed #b3d4fc;
  border-radius: 8px;
  padding: 18px;
  text-align: center;
  cursor: pointer;
  background: #f9fbff;
}

.upload-box-small.active { border-color: #1a6ff8; background: #e6f2ff }

.upload-status-row { display:flex; align-items:center; justify-content:space-between; gap:12px }

.file-info-small { flex:1 }

.upload-actions { display:flex; gap:8px }

.upload-page {
  min-height: calc(100vh - 132px);
  background-color: #f5f7fa;
  padding: 10px;
}

.upload-container {
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(18, 104, 248, 0.08);
  padding: 30px;
  border: 1px solid #e4edf9;
}

.upload-title {
  text-align: center;
  margin-bottom: 25px;
  color: #1a6ff8;
  font-size: 24px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.upload-title::before {
  content: '🔐';
}

.upload-box {
  border: 2px dashed #b3d4fc;
  border-radius: 12px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  background: #f9fbff;
  margin-bottom: 20px;
}

.upload-box:hover {
  border-color: #1a6ff8;
  background: #f0f7ff;
}

.upload-box.active {
  border-color: #1a6ff8;
  background: #e6f2ff;
}

.upload-icon {
  font-size: 48px;
  color: #1a6ff8;
  margin-bottom: 15px;
}

.upload-tips {
  color: #64748b;
  font-size: 14px;
  margin-top: 8px;
}

#file-input {
  display: none;
}

.file-info {
  padding: 15px;
  background: #f1f7ff;
  border-radius: 8px;
  margin-bottom: 15px;
}

.file-name {
  font-weight: 500;
  color: #1e293b;
  margin-bottom: 8px;
}

.progress-container {
  width: 100%;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
  margin: 10px 0;
}

.progress-bar {
  height: 100%;
  width: 0;
  background: linear-gradient(90deg, #1a6ff8, #3b82f6);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.status-text {
  font-size: 13px;
  color: #64748b;
  margin-top: 5px;
}

.upload-btn {
  width: 100%;
  height: 46px;
  background: #1a6ff8;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.3s;
  margin-top: 8px;
}

.upload-btn:hover {
  background: #0b5cd0;
}

.upload-btn:disabled {
  background: #94c2ff;
  cursor: not-allowed;
}

.tip-label {
  display: inline-block;
  background: #e6f7ff;
  color: #1a6ff8;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 12px;
  margin-left: 6px;
}

.success-msg {
  color: #10b981;
  font-weight: 500;
  text-align: center;
  margin-top: 12px;
}

.error-msg {
  color: #f43f5e;
  font-weight: 500;
  text-align: center;
  margin-top: 12px;
}

.table-card {
  margin: 20px auto 0;
  max-width: 1120px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  padding: 16px;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.table-header h3 {
  margin: 0;
  font-size: 16px;
  color: #1f2937;
}
</style>
