<template>
  <div :class="['designer-page', { 'designer-fullscreen': isNewDefinition }]">
    <header class="designer-header">
      <div class="header-left">
        <el-icon class="header-icon"><Share /></el-icon>
        <div>
          <h1>流程设计器</h1>
          <p>{{ titleText }}</p>
        </div>
      </div>

      <div class="header-actions">
        <el-button v-if="isNewDefinition" @click="onBack">返回</el-button>
        <el-button @click="saveDesign">保存</el-button>
        <el-button type="primary" @click="runProcess">运行流程</el-button>
      </div>
    </header>

    <main class="designer-main">
      <aside class="node-library">
        <div class="panel-title">节点库</div>
        <div class="node-list">
          <div
            v-for="node in paletteNodes"
            :key="node.type"
            class="task-node"
            :class="`task-node--${node.type}`"
            draggable="true"
            @dragstart="onDragStart($event, node.type)"
          >
            <div class="task-node__name">{{ node.label }}</div>
            <div class="task-node__desc">{{ node.desc }}</div>
          </div>
        </div>
      </aside>

      <section class="canvas-wrap">
        <div
          ref="canvasRef"
          class="flow-canvas"
          @dragover.prevent
          @drop="onDrop"
          @click="clearSelection"
        >
          <div class="flow-viewport" :style="viewportStyle">
            <svg class="line-layer">
              <defs>
                <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto">
                  <path d="M0,0 L0,6 L9,3 z" fill="#165dff" />
                </marker>
              </defs>
              <line
                v-for="line in connections"
                :key="line.id"
                :x1="linePosition(line).x1"
                :y1="linePosition(line).y1"
                :x2="linePosition(line).x2"
                :y2="linePosition(line).y2"
                class="flow-line"
                :class="{ 'is-selected': selectedConnectionId === line.id }"
                marker-end="url(#arrow)"
                @click.stop="selectConnection(line.id)"
              />
            </svg>

            <div
              v-for="node in nodes"
              :key="node.id"
              class="flow-node"
              :class="[`flow-node--${node.type}`, { 'is-selected': selectedNodeId === node.id, 'is-link-source': linkingFromId === node.id }]"
              :style="nodeStyle(node)"
              @mousedown.stop="startDrag(node, $event)"
              @click.stop="selectNode(node)"
            >
              <div class="flow-node__title">{{ node.name }}</div>
              <div class="flow-node__meta">{{ node.status }}</div>
            </div>
          </div>
        </div>

        <div class="canvas-tools">
          <el-button circle @click="addDefaultTask"><el-icon><Plus /></el-icon></el-button>
          <el-button circle @click="deleteSelected"><el-icon><Delete /></el-icon></el-button>
          <el-button circle @click="zoomIn"><el-icon><ZoomIn /></el-icon></el-button>
          <el-button circle @click="zoomOut"><el-icon><ZoomOut /></el-icon></el-button>
          <el-button circle @click="resetView"><el-icon><RefreshRight /></el-icon></el-button>
        </div>

        <div class="canvas-tip">提示：单击一个节点，再单击另一个节点可建立连线。</div>
      </section>

      <aside class="property-panel" :class="{ 'is-open': panelOpen }">
        <div class="panel-head">
          <h3>节点属性</h3>
          <el-button text @click="closePanel">关闭</el-button>
        </div>

        <div v-if="currentNode" class="panel-body">
          <el-form label-position="top">
            <el-form-item label="节点名称">
              <el-input v-model="editForm.name" />
            </el-form-item>

            <el-form-item label="节点描述">
              <el-input v-model="editForm.desc" type="textarea" :rows="3" />
            </el-form-item>

            <el-form-item label="执行时间">
              <el-input v-model="editForm.time" />
            </el-form-item>

            <el-form-item label="节点状态">
              <el-select v-model="editForm.status">
                <el-option label="待执行" value="待执行" />
                <el-option label="执行中" value="执行中" />
                <el-option label="已完成" value="已完成" />
                <el-option label="执行失败" value="执行失败" />
              </el-select>
            </el-form-item>

            <el-button type="primary" class="save-btn" @click="saveNodeConfig">保存配置</el-button>
          </el-form>
        </div>

        <el-empty v-else description="请先选择节点" :image-size="80" />
      </aside>
    </main>
  </div>

  <!-- 发布对话框：仅在新增时弹出 -->
  <el-dialog v-model="publishDialogVisible" title="发布流程定义" width="520px" destroy-on-close>
    <el-form ref="publishFormRef" :model="publishForm" :rules="publishRules" label-width="100px">
      <el-form-item label="流程编码">
        <el-input v-model="publishForm.code" readonly placeholder="后端生成的编码（只读）" />
      </el-form-item>
      <el-form-item label="流程名称" prop="name">
        <el-input v-model="publishForm.name" placeholder="请输入流程名称" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="publishForm.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="publishDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="doPublish">发布并保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Share, Plus, Delete, ZoomIn, ZoomOut, RefreshRight } from '@element-plus/icons-vue'
import { getProcessDefinitionDesign, saveProcessDefinitionDesign, publishProcessDefinition, getNextDefinitionCode } from '@/api/workflowDefinition'

const route = useRoute()
const router = useRouter()
const canvasRef = ref(null)

const NODE_WIDTH = 180
const NODE_HEIGHT = 72

const definitionId = computed(() => String(route.params.id || ''))
const isNewDefinition = computed(() => definitionId.value === 'new')
const titleText = computed(() => {
  if (isNewDefinition.value) return `新建流程`
  return `${route.query.name || '未命名流程'}（${route.query.code || '--'}）`
})

const paletteNodes = [
  { type: 'start', label: '开始节点', desc: '流程入口' },
  { type: 'task', label: '任务节点', desc: '执行任务' },
  { type: 'condition', label: '条件节点', desc: '逻辑判断' },
  { type: 'end', label: '结束节点', desc: '流程出口' }
]

const nodes = ref([])
const connections = ref([])
const selectedNodeId = ref('')
const selectedConnectionId = ref('')
const linkingFromId = ref('')
const panelOpen = ref(false)
const scale = ref(1)
const nodeSeq = ref(1)

const editForm = reactive({
  name: '',
  desc: '',
  time: '立即执行',
  status: '待执行'
})

const dragState = reactive({
  nodeId: '',
  startX: 0,
  startY: 0,
  originX: 0,
  originY: 0,
  moving: false
})

const viewportStyle = computed(() => ({
  transform: `scale(${scale.value})`,
  transformOrigin: 'center center'
}))

const currentNode = computed(() => nodes.value.find((item) => item.id === selectedNodeId.value))

const defaultNodeName = (type) => {
  if (type === 'start') return '开始节点'
  if (type === 'task') return '任务节点'
  if (type === 'condition') return '条件节点'
  return '结束节点'
}

const clampPos = (value, max) => {
  if (value < 0) return 0
  if (value > max) return max
  return value
}

const onDragStart = (event, type) => {
  event.dataTransfer.setData('text/plain', type)
}

const createNode = (type, x, y) => {
  const id = `node-${Date.now()}-${nodeSeq.value++}`
  const maxX = Math.max(0, (canvasRef.value?.clientWidth || 1200) - NODE_WIDTH)
  const maxY = Math.max(0, (canvasRef.value?.clientHeight || 700) - NODE_HEIGHT)

  nodes.value.push({
    id,
    type,
    x: clampPos(x, maxX),
    y: clampPos(y, maxY),
    name: defaultNodeName(type),
    desc: '',
    time: '立即执行',
    status: '待执行'
  })
}

const onDrop = (event) => {
  const type = event.dataTransfer.getData('text/plain')
  if (!type) return

  const rect = canvasRef.value.getBoundingClientRect()
  const x = (event.clientX - rect.left) / scale.value - NODE_WIDTH / 2
  const y = (event.clientY - rect.top) / scale.value - NODE_HEIGHT / 2
  createNode(type, x, y)
}

const nodeStyle = (node) => ({
  left: `${node.x}px`,
  top: `${node.y}px`
})

const nodeCenter = (nodeId) => {
  const node = nodes.value.find((item) => item.id === nodeId)
  if (!node) return { x: 0, y: 0 }
  return {
    x: node.x + NODE_WIDTH / 2,
    y: node.y + NODE_HEIGHT / 2
  }
}

const linePosition = (line) => {
  const from = nodeCenter(line.from)
  const to = nodeCenter(line.to)
  return { x1: from.x, y1: from.y, x2: to.x, y2: to.y }
}

const hasConnection = (fromId, toId) => connections.value.some((line) => line.from === fromId && line.to === toId)

const selectNode = (node) => {
  selectedConnectionId.value = ''

  if (linkingFromId.value && linkingFromId.value !== node.id) {
    if (!hasConnection(linkingFromId.value, node.id)) {
      connections.value.push({
        id: `line-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
        from: linkingFromId.value,
        to: node.id
      })
      ElMessage.success('已建立连线')
    }
    linkingFromId.value = ''
  } else {
    linkingFromId.value = node.id
  }

  selectedNodeId.value = node.id
  editForm.name = node.name
  editForm.desc = node.desc
  editForm.time = node.time
  editForm.status = node.status
  panelOpen.value = true
}

const selectConnection = (lineId) => {
  selectedConnectionId.value = lineId
  selectedNodeId.value = ''
  linkingFromId.value = ''
  panelOpen.value = false
}

const clearSelection = () => {
  selectedNodeId.value = ''
  selectedConnectionId.value = ''
  linkingFromId.value = ''
  panelOpen.value = false
}

const closePanel = () => {
  panelOpen.value = false
  linkingFromId.value = ''
}

const saveNodeConfig = () => {
  const node = currentNode.value
  if (!node) return

  node.name = editForm.name || node.name
  node.desc = editForm.desc || ''
  node.time = editForm.time || ''
  node.status = editForm.status || '待执行'
  ElMessage.success('节点配置已保存')
}

const addDefaultTask = () => {
  createNode('task', 360, 220)
}

const deleteSelected = () => {
  if (selectedConnectionId.value) {
    connections.value = connections.value.filter((line) => line.id !== selectedConnectionId.value)
    selectedConnectionId.value = ''
    ElMessage.success('已删除连线')
    return
  }

  if (selectedNodeId.value) {
    const nodeId = selectedNodeId.value
    nodes.value = nodes.value.filter((item) => item.id !== nodeId)
    connections.value = connections.value.filter((line) => line.from !== nodeId && line.to !== nodeId)
    selectedNodeId.value = ''
    panelOpen.value = false
    linkingFromId.value = ''
    ElMessage.success('已删除节点')
  }
}

const zoomIn = () => {
  scale.value = Math.min(2, Number((scale.value + 0.1).toFixed(2)))
}

const zoomOut = () => {
  scale.value = Math.max(0.5, Number((scale.value - 0.1).toFixed(2)))
}

const resetView = () => {
  scale.value = 1
}

const startDrag = (node, event) => {
  dragState.nodeId = node.id
  dragState.startX = event.clientX
  dragState.startY = event.clientY
  dragState.originX = node.x
  dragState.originY = node.y
  dragState.moving = true
}

const onMouseMove = (event) => {
  if (!dragState.moving) return
  const node = nodes.value.find((item) => item.id === dragState.nodeId)
  if (!node) return

  const dx = (event.clientX - dragState.startX) / scale.value
  const dy = (event.clientY - dragState.startY) / scale.value
  const maxX = Math.max(0, (canvasRef.value?.clientWidth || 1200) - NODE_WIDTH)
  const maxY = Math.max(0, (canvasRef.value?.clientHeight || 700) - NODE_HEIGHT)

  node.x = clampPos(dragState.originX + dx, maxX)
  node.y = clampPos(dragState.originY + dy, maxY)
}

const onMouseUp = () => {
  dragState.moving = false
  dragState.nodeId = ''
}

const saveDesign = async () => {
  const payload = {
    nodes: nodes.value,
    connections: connections.value,
    scale: scale.value
  }

  // 如果已有 definitionId，则直接保存设计
  if (definitionId.value) {
    try {
      const res = await saveProcessDefinitionDesign(definitionId.value, payload)
      if (res?.data?.code === 200) {
        ElMessage.success('设计保存成功')
      } else {
        ElMessage.error(res?.data?.msg || '设计保存失败')
      }
    } catch (error) {
      ElMessage.error(error?.response?.data?.msg || '设计保存失败')
    }
    return
  }

  // 新增流程：弹出发布对话框，用户填写基础信息后由后端发布并返回 ID，然后保存设计
  openPublishDialog()
}

// Publish dialog state and handlers
const publishDialogVisible = ref(false)
const publishFormRef = ref(null)
const publishForm = reactive({
  code: '',
  name: '',
  status: 1,
  isLatest: 1,
  remark: ''
})

const openPublishDialog = async () => {
  // try to get next code from backend for preview
  try {
    const res = await getNextDefinitionCode()
    if (res?.data?.code === 200) {
      publishForm.code = res.data.data || ''
    } else {
      publishForm.code = ''
    }
  } catch (e) {
    publishForm.code = ''
  }
  publishForm.name = route.query.name || ''
  publishForm.remark = ''
  publishForm.status = 1
  publishForm.isLatest = 1
  publishDialogVisible.value = true
}

const publishRules = {
  name: [{ required: true, message: '请输入流程名称', trigger: 'blur' }]
}

const doPublish = async () => {
  if (!publishFormRef.value) return
  const valid = await publishFormRef.value.validate().catch(() => false)
  if (!valid) return

  // build nodes for PublishDefinitionRequest
  // Map visual nodes to minimal NodeDefDTO expected by backend
  const dtos = nodes.value.map((n, idx) => {
    return {
      nodeKey: n.id || `n${idx + 1}`,
      nodeName: n.name || (n.type === 'start' ? '开始节点' : '任务节点'),
      approvalMode: 'BASIC',
      assignees: ['system']
    }
  })

  const payload = {
    code: publishForm.code || undefined,
    name: publishForm.name.trim(),
    remark: publishForm.remark?.trim() || '',
    operator: 'system',
    nodes: dtos
  }

  try {
    const res = await publishProcessDefinition(payload)
    const result = res?.data
    if (result?.code === 200) {
      const newId = result.data
      ElMessage.success('流程定义发布成功')
      publishDialogVisible.value = false

      // save design to new definition id
      try {
        const saveRes = await saveProcessDefinitionDesign(newId, { nodes: nodes.value, connections: connections.value, scale: scale.value })
        if (saveRes?.data?.code === 200) {
          ElMessage.success('设计保存成功')
        } else {
          ElMessage.error(saveRes?.data?.msg || '设计保存失败')
        }
      } catch (err) {
        ElMessage.error(err?.response?.data?.msg || '设计保存失败')
      }

      // navigate to designer with new id
      router.replace({ name: 'WorkflowDesigner', params: { id: newId }, query: { code: publishForm.code, name: publishForm.name, versionNo: 1 } })
      return
    }
    ElMessage.error(result?.msg || '发布失败')
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '发布失败')
  }
}

const runProcess = () => {
  ElMessage.success('流程运行模拟成功（示例）')
}

const onBack = () => {
  // 返回到流程定义列表
  router.push({ name: 'WorkflowDefinitionManagement' })
}


const buildDefaultGraph = () => {
  nodes.value = [
    { id: 'node-1', type: 'start', x: 120, y: 180, name: '开始节点', desc: '', time: '立即执行', status: '待执行' },
    { id: 'node-2', type: 'task', x: 360, y: 180, name: '任务节点', desc: '', time: '立即执行', status: '待执行' },
    { id: 'node-3', type: 'condition', x: 600, y: 120, name: '条件节点', desc: '', time: '立即执行', status: '待执行' },
    { id: 'node-4', type: 'task', x: 600, y: 280, name: '任务节点', desc: '', time: '立即执行', status: '待执行' },
    { id: 'node-5', type: 'end', x: 860, y: 200, name: '结束节点', desc: '', time: '立即执行', status: '待执行' }
  ]

  connections.value = [
    { id: 'line-1', from: 'node-1', to: 'node-2' },
    { id: 'line-2', from: 'node-2', to: 'node-3' },
    { id: 'line-3', from: 'node-3', to: 'node-4' },
    { id: 'line-4', from: 'node-4', to: 'node-5' }
  ]
}

const loadDesign = async () => {
  // 新建模式直接构建默认图，不去后端请求
  if (isNewDefinition.value) {
    buildDefaultGraph()
    return
  }

  if (!definitionId.value) {
    ElMessage.warning('未识别流程定义ID，返回列表页')
    router.push('/workflow/definition')
    return
  }

  try {
    const res = await getProcessDefinitionDesign(definitionId.value)
    const result = res?.data
    const data = result?.data

    if (result?.code === 200 && data?.nodes?.length) {
      nodes.value = data.nodes
      connections.value = Array.isArray(data.connections) ? data.connections : []
      scale.value = Number(data.scale || 1)
      return
    }

    buildDefaultGraph()
  } catch (error) {
    buildDefaultGraph()
  }
}

onMounted(() => {
  loadDesign()
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})
</script>

<style scoped lang="scss">
.designer-page {
  height: calc(100vh - 96px);
  min-height: 700px;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  overflow: hidden;
  background: #f8fafd;
}

.designer-fullscreen {
  position: fixed;
  inset: 0;
  margin: 0;
  padding: 0;
  border-radius: 0;
  z-index: 9999;
  background: #ffffff;
}

.designer-fullscreen .designer-header {
  padding: 12px 24px;
}

.designer-fullscreen .designer-main {
  height: calc(100vh - 64px);
}

.designer-fullscreen .canvas-wrap {
  padding: 20px;
}

.header-actions el-button,
.header-actions .el-button {
  margin-left: 8px;
}

.header-actions .el-button:first-child {
  margin-right: 8px;
}

.designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  color: #fff;
  background: linear-gradient(135deg, #165dff, #36a3ff);

  h1 {
    margin: 0;
    font-size: 20px;
  }

  p {
    margin: 4px 0 0;
    font-size: 13px;
    opacity: 0.9;
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  font-size: 24px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.designer-main {
  flex: 1;
  min-height: 0;
  display: flex;
  position: relative;
}

.node-library {
  width: 248px;
  border-right: 1px solid #e9eef4;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.panel-title {
  padding: 16px;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #edf2f8;
}

.node-list {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
}

.task-node {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #dfe7f3;
  cursor: move;
  transition: all 0.2s;
}

.task-node:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 16px rgba(30, 91, 255, 0.08);
}

.task-node__name {
  font-size: 14px;
  font-weight: 600;
}

.task-node__desc {
  margin-top: 4px;
  color: #8a95a6;
  font-size: 12px;
}

.task-node--start {
  background: #effaf2;
  border-color: #b7e8c4;
}

.task-node--task {
  background: #f4f9ff;
  border-color: #cfe2ff;
}

.task-node--condition {
  background: #f6f8fb;
  border-color: #d9dee8;
}

.task-node--end {
  background: #fff4f4;
  border-color: #ffcaca;
}

.canvas-wrap {
  flex: 1;
  min-width: 0;
  position: relative;
  padding: 16px;
  background: #fff;
}

.flow-canvas {
  width: 100%;
  height: 100%;
  overflow: hidden;
  border: 2px solid rgba(22, 93, 255, 0.12);
  border-radius: 12px;
  position: relative;
  background-image: radial-gradient(circle, rgba(22, 93, 255, 0.12) 1px, transparent 1px),
    linear-gradient(rgba(22, 93, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(22, 93, 255, 0.05) 1px, transparent 1px);
  background-size: 20px 20px, 40px 40px, 40px 40px;
}

.flow-viewport {
  position: relative;
  width: 100%;
  height: 100%;
}

.line-layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.flow-line {
  stroke: #165dff;
  stroke-width: 2;
  pointer-events: auto;
  cursor: pointer;
}

.flow-line.is-selected {
  stroke: #ff6b6b;
  stroke-width: 3;
}

.flow-node {
  position: absolute;
  width: 180px;
  min-height: 72px;
  padding: 12px;
  border-radius: 10px;
  border: 2px solid rgba(22, 93, 255, 0.25);
  background: #fff;
  box-shadow: 0 8px 20px rgba(22, 93, 255, 0.08);
  cursor: move;
  user-select: none;
}

.flow-node__title {
  font-weight: 600;
  color: #2d3648;
}

.flow-node__meta {
  margin-top: 8px;
  font-size: 12px;
  color: #7b8798;
}

.flow-node.is-selected {
  border-color: #165dff;
}

.flow-node.is-link-source {
  box-shadow: 0 0 0 2px rgba(255, 149, 0, 0.35);
}

.flow-node--start {
  border-color: rgba(60, 176, 84, 0.45);
}

.flow-node--task {
  border-color: rgba(22, 93, 255, 0.35);
}

.flow-node--condition {
  border-color: rgba(97, 112, 138, 0.45);
}

.flow-node--end {
  border-color: rgba(245, 108, 108, 0.45);
}

.canvas-tools {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.12);
  padding: 8px;
  display: flex;
  gap: 6px;
}

.canvas-tip {
  position: absolute;
  left: 24px;
  bottom: 24px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 8px;
}

.property-panel {
  width: 288px;
  background: #fff;
  border-left: 1px solid #e9eef4;
  transform: translateX(100%);
  transition: transform 0.25s ease;
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 9;
}

.property-panel.is-open {
  transform: translateX(0);
}

.panel-head {
  padding: 14px 16px;
  border-bottom: 1px solid #edf2f8;
  display: flex;
  align-items: center;
  justify-content: space-between;

  h3 {
    margin: 0;
    font-size: 16px;
  }
}

.panel-body {
  padding: 14px 16px;
}

.save-btn {
  width: 100%;
}

@media (max-width: 1280px) {
  .node-library {
    width: 220px;
  }
}
</style>

