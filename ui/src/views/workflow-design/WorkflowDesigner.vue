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
          @dragover.prevent="onCanvasDragOver"
          @drop="onDrop"
          @click="clearSelection"
        >
            <div class="flow-viewport" ref="viewportRef" :style="viewportStyle">
            <svg class="line-layer">
              <defs>
                <!--
                  使用 userSpaceOnUse 保证在 CSS transform(scale) 下箭头方向与大小稳定，
                  调整 refX/refY 与路径使箭头正确位于折线/直线末端。
                -->
                <marker id="arrow" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto" markerUnits="userSpaceOnUse">
                  <!-- 三角形箭头，默认填充与连线颜色一致 -->
                  <path d="M0 0 L12 6 L0 12 L3 6 z" fill="#165dff" />
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

              <!-- temporary dragging connection while creating a new link -->
              <line
                v-if="draggingConnection.active"
                :x1="draggingConnection.x1"
                :y1="draggingConnection.y1"
                :x2="draggingConnection.x2"
                :y2="draggingConnection.y2"
                class="flow-line"
                style="stroke-dasharray:6; opacity:0.8"
                marker-end="url(#arrow)"
              />
            </svg>

            <div
              v-for="node in nodes"
              :key="node.id"
              class="flow-node"
              :class="[`flow-node--${node.type}`, { 'is-selected': selectedNodeId === node.id }]"
              :style="nodeStyle(node)"
              @mousedown.stop="startDrag(node, $event)"
              @click.stop="selectNode(node)"
            >
              <!-- connection ports: top(0), right(1), bottom(2), left(3) -->
              <div class="node-ports">
                <div
                  class="node-port node-port--top"
                  draggable="true"
                  @dragstart.stop.prevent="onPortDragStart($event, node.id, 0)"
                  @dragend.stop="onPortDragEnd"
                  @dragover.prevent
                  @drop.stop.prevent="onPortDrop($event, node.id, 0)"
                ></div>
                <div
                  class="node-port node-port--right"
                  draggable="true"
                  @dragstart.stop.prevent="onPortDragStart($event, node.id, 1)"
                  @dragend.stop="onPortDragEnd"
                  @dragover.prevent
                  @drop.stop.prevent="onPortDrop($event, node.id, 1)"
                ></div>
                <div
                  class="node-port node-port--bottom"
                  draggable="true"
                  @dragstart.stop.prevent="onPortDragStart($event, node.id, 2)"
                  @dragend.stop="onPortDragEnd"
                  @dragover.prevent
                  @drop.stop.prevent="onPortDrop($event, node.id, 2)"
                ></div>
                <div
                  class="node-port node-port--left"
                  draggable="true"
                  @dragstart.stop.prevent="onPortDragStart($event, node.id, 3)"
                  @dragend.stop="onPortDragEnd"
                  @dragover.prevent
                  @drop.stop.prevent="onPortDrop($event, node.id, 3)"
                ></div>
              </div>

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
const viewportRef = ref(null)

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

// draggingConnection 用于在从端口拖拽时绘制临时连线（鼠标或拖拽事件）
// 字段说明：active 是否激活，fromId/fromPort 源端口，x1/y1 起点，x2/y2 终点，isMouse 是否为鼠标拖拽
const draggingConnection = reactive({ active: false, fromId: '', fromPort: null, x1: 0, y1: 0, x2: 0, y2: 0, isMouse: false })
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
const lastDragAt = ref(0)

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

const portCenter = (nodeId, portIndex) => {
  const node = nodes.value.find((item) => item.id === nodeId)
  if (!node) return { x: 0, y: 0 }
  const centerX = node.x + NODE_WIDTH / 2
  const centerY = node.y + NODE_HEIGHT / 2
  if (portIndex === 0) return { x: centerX, y: node.y }
  if (portIndex === 1) return { x: node.x + NODE_WIDTH, y: centerY }
  if (portIndex === 2) return { x: centerX, y: node.y + NODE_HEIGHT }
  if (portIndex === 3) return { x: node.x, y: centerY }
  // 回退到节点中心（当端口索引不合法时）
  return { x: centerX, y: centerY }
}

const linePosition = (line) => {
  const from = line.fromPort !== undefined ? portCenter(line.from, line.fromPort) : nodeCenter(line.from)
  const to = line.toPort !== undefined ? portCenter(line.to, line.toPort) : nodeCenter(line.to)
  return { x1: from.x, y1: from.y, x2: to.x, y2: to.y }
}

const hasConnection = (fromId, fromPort, toId, toPort) => connections.value.some((line) => line.from === fromId && (line.fromPort === fromPort || (line.fromPort === undefined && fromPort === undefined)) && line.to === toId && (line.toPort === toPort || (line.toPort === undefined && toPort === undefined)))

const selectNode = (node) => {
  selectedConnectionId.value = ''
  // 仅用于选择节点并打开属性面板
  selectedNodeId.value = node.id
  editForm.name = node.name
  editForm.desc = node.desc
  editForm.time = node.time
  editForm.status = node.status
  panelOpen.value = true
}

// 端口拖拽/放置处理器
const onPortDragStart = (event, nodeId, portIndex) => {
  // 标记为端口拖拽：尝试使用自定义 MIME 类型存储端口信息，若浏览器限制则降级到 text/plain
  try {
    event.dataTransfer.setData('text/port', JSON.stringify({ fromId: nodeId, fromPort: portIndex }))
  } catch (e) {
    // 某些浏览器可能限制，仍兼容降级处理
    event.dataTransfer.setData('text/plain', `port:${nodeId}:${portIndex}`)
  }

  // 初始化临时连线位置（起点为端口中心）
  const p = portCenter(nodeId, portIndex)
  draggingConnection.active = true
  draggingConnection.fromId = nodeId
  draggingConnection.fromPort = portIndex
  draggingConnection.x1 = p.x
  draggingConnection.y1 = p.y
  draggingConnection.x2 = p.x
  draggingConnection.y2 = p.y
}

const onPortDragEnd = () => {
  draggingConnection.active = false
  draggingConnection.fromId = ''
  draggingConnection.fromPort = null
}

const onCanvasDragOver = (event) => {
  if (!draggingConnection.active) return
  const rect = canvasRef.value.getBoundingClientRect()
  // 计算视口内坐标（考虑当前缩放），用于更新临时连线终点
  draggingConnection.x2 = (event.clientX - (rect?.left || 0)) / scale.value
  draggingConnection.y2 = (event.clientY - (rect?.top || 0)) / scale.value
}

const onPortDrop = (event, toNodeId, toPortIndex) => {
  // 读取拖拽来源（优先读取自定义 MIME text/port，降级兼容 text/plain）
  let payload = null
  try {
    const raw = event.dataTransfer.getData('text/port')
    if (raw) payload = JSON.parse(raw)
  } catch (e) {
    const raw = event.dataTransfer.getData('text/plain')
    if (raw && raw.startsWith('port:')) {
      const parts = raw.split(':')
      payload = { fromId: parts[1], fromPort: Number(parts[2]) }
    }
  }
  onPortDragEnd()
  if (!payload) return
  const { fromId, fromPort } = payload
  if (fromId === toNodeId && fromPort === toPortIndex) return

  if (hasConnection(fromId, fromPort, toNodeId, toPortIndex)) {
    ElMessage.info('连线已存在')
    return
  }

  // 创建新的连线对象并加入集合
  connections.value.push({ id: `line-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`, from: fromId, fromPort, to: toNodeId, toPort: toPortIndex })
  ElMessage.success('已建立连线')
}

const selectConnection = (lineId) => {
  selectedConnectionId.value = lineId
  selectedNodeId.value = ''
  panelOpen.value = false
}

const clearSelection = () => {
  selectedNodeId.value = ''
  selectedConnectionId.value = ''
  panelOpen.value = false
}

const closePanel = () => {
  panelOpen.value = false
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
  // 如果按下来自端口，启用端口拖拽而不是节点拖拽
  if (event.target && event.target.classList && event.target.classList.contains('node-port')) {
    // 根据 class 判断具体端口索引
    const cls = event.target.classList
    let portIndex = 0
    if (cls.contains('node-port--top')) portIndex = 0
    else if (cls.contains('node-port--right')) portIndex = 1
    else if (cls.contains('node-port--bottom')) portIndex = 2
    else if (cls.contains('node-port--left')) portIndex = 3
    startPortMouseDrag(node.id, portIndex, event)
    return
  }

  // 如果按下位置靠近端口区域，使用鼠标开启端口拖拽（便于精确拖拽）
  const maybePort = hitTestPort(node, event.clientX, event.clientY)
  if (maybePort !== -1) {
    startPortMouseDrag(node.id, maybePort, event)
    return
  }

  dragState.nodeId = node.id
  dragState.startX = event.clientX
  dragState.startY = event.clientY
  dragState.originX = node.x
  dragState.originY = node.y
  dragState.moving = true
}

// 查找给定 client 坐标下的端口；返回 { nodeId, portIndex, portPos } 或 null
// 说明：函数会考虑当前视口缩放与画布位置，用于鼠标拖拽时的吸附判断
const findPortUnderClient = (clientX, clientY) => {
  if (!viewportRef.value && !canvasRef.value) return null
  const rect = (viewportRef.value && viewportRef.value.getBoundingClientRect && viewportRef.value.getBoundingClientRect()) || (canvasRef.value && canvasRef.value.getBoundingClientRect && canvasRef.value.getBoundingClientRect())
  const thresholdBase = 12
  const thr = thresholdBase * Math.max(1, scale.value)

  for (const node of nodes.value) {
    for (let i = 0; i < 4; i++) {
      const p = portCenter(node.id, i)
      const px = (rect?.left || 0) + p.x * scale.value
      const py = (rect?.top || 0) + p.y * scale.value
      const dx = clientX - px
      const dy = clientY - py
      if (dx * dx + dy * dy <= thr * thr) {
        return { nodeId: node.id, portIndex: i, portPos: p }
      }
    }
  }
  return null
}

// 兼容适配：检测指定节点上的端口（返回端口索引或 -1）
const hitTestPort = (node, clientX, clientY) => {
  const found = findPortUnderClient(clientX, clientY)
  if (!found) return -1
  return found.nodeId === node.id ? found.portIndex : -1
}

const startPortMouseDrag = (nodeId, portIndex, event) => {
  const p = portCenter(nodeId, portIndex)
  draggingConnection.active = true
  draggingConnection.isMouse = true
  draggingConnection.fromId = nodeId
  draggingConnection.fromPort = portIndex
  draggingConnection.x1 = p.x
  draggingConnection.y1 = p.y
  // 初始鼠标位置（用于基于鼠标的端口拖拽）
  const rect = (viewportRef.value && viewportRef.value.getBoundingClientRect && viewportRef.value.getBoundingClientRect()) || (canvasRef.value && canvasRef.value.getBoundingClientRect && canvasRef.value.getBoundingClientRect())
  draggingConnection.x2 = (event.clientX - (rect?.left || 0)) / scale.value
  draggingConnection.y2 = (event.clientY - (rect?.top || 0)) / scale.value
}

const onMouseMove = (event) => {
  // 如果正在拖动节点，则移动节点（支持缩放后的坐标转换和边界限制）
  if (dragState.moving) {
    const node = nodes.value.find((item) => item.id === dragState.nodeId)
    if (!node) return

    const dx = (event.clientX - dragState.startX) / scale.value
    const dy = (event.clientY - dragState.startY) / scale.value
    const maxX = Math.max(0, (canvasRef.value?.clientWidth || 1200) - NODE_WIDTH)
    const maxY = Math.max(0, (canvasRef.value?.clientHeight || 700) - NODE_HEIGHT)

    node.x = clampPos(dragState.originX + dx, maxX)
    node.y = clampPos(dragState.originY + dy, maxY)
    return
  }

  // 如果在拖拽端口，更新临时连线位置（并尝试吸附到端口）
  if (draggingConnection.active && draggingConnection.isMouse) {
    const rect = (viewportRef.value && viewportRef.value.getBoundingClientRect && viewportRef.value.getBoundingClientRect()) || (canvasRef.value && canvasRef.value.getBoundingClientRect && canvasRef.value.getBoundingClientRect())
    // 检测鼠标是否在端口上；若在则把临时连线吸附到该端口位置，提升交互体验
    const found = findPortUnderClient(event.clientX, event.clientY)
    if (found) {
      draggingConnection.x2 = found.portPos.x
      draggingConnection.y2 = found.portPos.y
    } else {
      draggingConnection.x2 = (event.clientX - (rect?.left || 0)) / scale.value
      draggingConnection.y2 = (event.clientY - (rect?.top || 0)) / scale.value
    }
  }
}

const onMouseUp = () => {
  // 完成节点拖动
  if (dragState.moving) {
    dragState.moving = false
    dragState.nodeId = ''
    lastDragAt.value = Date.now()
    return
  }

  // 完成基于鼠标的端口拖拽：检测鼠标抬起是否位于端口上
  if (draggingConnection.active && draggingConnection.isMouse) {
    // 查找候选目标节点/端口
    const mx = draggingConnection.x2
    const my = draggingConnection.y2

    let dropped = false
    for (const node of nodes.value) {
      for (let pi = 0; pi < 4; pi++) {
        const p = portCenter(node.id, pi)
        const dx = (p.x - mx)
        const dy = (p.y - my)
        const thr = 14
        if (dx * dx + dy * dy <= thr * thr) {
          // 尝试创建连线
          const fromId = draggingConnection.fromId
          const fromPort = draggingConnection.fromPort
          const toNodeId = node.id
          const toPortIndex = pi
          if (!(fromId === toNodeId && fromPort === toPortIndex)) {
            if (!hasConnection(fromId, fromPort, toNodeId, toPortIndex)) {
              connections.value.push({ id: `line-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`, from: fromId, fromPort, to: toNodeId, toPort: toPortIndex })
              ElMessage.success('已建立连线')
            } else {
              ElMessage.info('连线已存在')
            }
          }
          dropped = true
          break
        }
      }
      if (dropped) break
    }

    // 清理拖拽状态
    draggingConnection.active = false
    draggingConnection.isMouse = false
    draggingConnection.fromId = ''
    draggingConnection.fromPort = null
  }
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

// 发布对话框状态与处理器
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
  // 尝试从后端获取下一个可用编码用于预览
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

  // 为发布请求构建节点 DTO
  // 将可视化节点映射为后端期望的最小 NodeDefDTO 结构
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

      // 将设计保存到后端返回的新 definition id
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

      // 导航到带有新 id 的设计器页面
      router.replace({ name: 'WorkflowDesigner', params: { id: newId }, query: { code: publishForm.code, name: publishForm.name, versionNo: 1 } })
      return
    }
    ElMessage.error(result?.msg || '发布失败')
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '发布失败')
  }
}

// 运行流程按钮已移除（功能已删除）。

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
  // 新建模式：保持画布为空，用户可手动拖入节点
  if (isNewDefinition.value) {
    nodes.value = []
    connections.value = []
    scale.value = 1
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
  z-index: 2; /* above canvas ports */
}

.node-ports {
  position: absolute;
  inset: 0;
  pointer-events: none; /* allow inner ports to control events */
  z-index: 0; /* beneath node content visually */
}

.node-port {
  position: absolute;
  width: 14px;
  height: 14px;
  background: rgba(22, 93, 255, 0.12);
  border: 2px solid rgba(22, 93, 255, 0.25);
  border-radius: 50%;
  pointer-events: auto;
  z-index: 0;
}

.node-port.node-port--top { left: 50%; transform: translateX(-50%); top: -7px }
.node-port.node-port--right { right: -7px; top: 50%; transform: translateY(-50%) }
.node-port.node-port--bottom { left: 50%; transform: translateX(-50%); bottom: -7px }
.node-port.node-port--left { left: -7px; top: 50%; transform: translateY(-50%) }

/* Ports remain visually beneath node content; port drag begins via mousedown hit-testing. */

.flow-node__title {
  font-weight: 600;
  color: #2d3648;
}

.flow-node > .flow-node__title,
.flow-node > .flow-node__meta {
  position: relative;
  z-index: 1; /* ensure node text/content is above ports visually */
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

