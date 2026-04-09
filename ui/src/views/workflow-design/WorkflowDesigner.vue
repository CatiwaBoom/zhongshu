<template>
  <div class="designer-page" v-loading="loading">
    <header class="designer-header">
      <div class="header-left">
        <el-button @click="goBack">返回列表</el-button>
        <span class="title">流程设计器 - {{ definitionTitle }}</span>
      </div>
      <div class="header-actions">
        <el-button type="warning" plain :disabled="!selectedEdgeIds.length" @click="removeSelectedEdges">删除连线</el-button>
        <el-button type="danger" plain :disabled="!selectedNodeIds.length" @click="removeSelectedNodes">删除节点</el-button>
        <el-button type="primary" @click="saveDesign">保存流程</el-button>
      </div>
    </header>

    <div class="designer-body">
      <aside class="palette">
        <h4>拖拽节点</h4>
        <div class="palette-list">
          <div v-for="item in palette" :key="item.type" class="palette-item" draggable="true" @dragstart="onDragStart($event, item)">
            {{ item.label }}
          </div>
        </div>
      </aside>

      <main class="canvas" @drop="onDrop" @dragover="onDragOver">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :fit-view-on-init="true"
          :default-viewport="viewport"
          @connect="onConnect"
          @selection-change="onSelectionChange"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @pane-click="clearSelection"
        />
      </main>
    </div>

    <el-dialog v-model="approverDialogVisible" title="配置审批人员" width="560px">
      <el-form label-width="88px">
        <el-form-item label="审批人员">
          <el-select
            v-model="selectedApproverIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择审批人员（可多选）"
            style="width: 100%"
            :loading="approverLoading"
          >
            <el-option
              v-for="item in userOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approverDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveApproversToNode">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import {
  deleteProcessNode,
  getProcessDefinitionDesign,
  saveProcessDefinitionDesign
} from '@/api/workflowDefinition'
import { getUserList } from '@/api/user'

const route = useRoute()
const router = useRouter()
const definitionId = computed(() => String(route.params.id || ''))
const definitionTitle = computed(() => definitionId.value === 'new' ? '未命名流程' : definitionId.value)

const loading = ref(false)
const nodes = ref([])
const edges = ref([])
const viewport = ref({ x: 0, y: 0, zoom: 1 })
const selectedNodeIds = ref([])
const selectedEdgeIds = ref([])
const approverDialogVisible = ref(false)
const approverLoading = ref(false)
const currentPersonNodeId = ref('')
const selectedApproverIds = ref([])
const userOptions = ref([])

const { addEdges, screenToFlowCoordinate, getViewport } = useVueFlow()

const palette = [
  { type: 'start', label: '开始节点' },
  { type: 'person', label: '人员审批节点' },
  { type: 'task', label: '任务节点' },
  { type: 'condition', label: '条件节点' },
  { type: 'end', label: '结束节点' }
]

const newNodeLabel = (type) => {
  if (type === 'start') return '开始'
  if (type === 'end') return '结束'
  if (type === 'condition') return '条件'
  if (type === 'person') return '人员审批'
  return '任务'
}

const formatPersonNodeLabelByData = (data) => {
  const names = Array.isArray(data?.approverNames) ? data.approverNames.filter(Boolean) : []
  const ids = Array.isArray(data?.approverIds) ? data.approverIds.filter(Boolean) : []
  const count = names.length || ids.length
  if (!count) {
    return '人员审批(未配置)'
  }
  if (names.length) {
    const preview = names.slice(0, 2).join('、')
    return names.length <= 2 ? `人员审批(${preview})` : `人员审批(${preview}等${names.length}人)`
  }
  return `人员审批(${count}人)`
}

const onDragStart = (event, item) => {
  event.dataTransfer?.setData('application/workflow-node-type', item.type)
  event.dataTransfer.effectAllowed = 'move'
}

const onDragOver = (event) => {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
}

const onDrop = (event) => {
  event.preventDefault()
  const nodeType = event.dataTransfer?.getData('application/workflow-node-type')
  if (!nodeType) return

  const position = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  const id = `node_${Date.now()}_${Math.floor(Math.random() * 10000)}`
  const data = { nodeType }
  const label = nodeType === 'person' ? formatPersonNodeLabelByData(data) : `${newNodeLabel(nodeType)}-${nodes.value.length + 1}`
  nodes.value = nodes.value.concat({
    id,
    type: 'default',
    label,
    position,
    data
  })
}

const onConnect = (connection) => {
  addEdges([{ ...connection, id: `edge_${Date.now()}_${Math.floor(Math.random() * 10000)}` }])
}

const onSelectionChange = (selection) => {
  const arr = Array.isArray(selection?.nodes) ? selection.nodes : []
  selectedNodeIds.value = arr.map(item => item.id)

  const edgeArr = Array.isArray(selection?.edges) ? selection.edges : []
  selectedEdgeIds.value = edgeArr.map(item => item.id)
}

// 兼容 selection-change 在部分场景不触发，点击元素时直接同步选中状态
const onNodeClick = ({ node }) => {
  if (!node?.id) return
  selectedNodeIds.value = [node.id]
  selectedEdgeIds.value = []

  if (String(node?.data?.nodeType || '') === 'person') {
    openApproverDialog(node)
  }
}

const onEdgeClick = ({ edge }) => {
  if (!edge?.id) return
  selectedEdgeIds.value = [edge.id]
  selectedNodeIds.value = []
}

const clearSelection = () => {
  selectedNodeIds.value = []
  selectedEdgeIds.value = []
}

const fetchUsers = async () => {
  approverLoading.value = true
  try {
    const res = await getUserList({ page: 1, size: 200, status: 1 })
    const result = res.data
    if (result?.code !== 200) {
      ElMessage.error(result?.msg || '加载用户失败')
      return
    }
    const records = Array.isArray(result?.data?.records)
      ? result.data.records
      : (Array.isArray(result?.data) ? result.data : [])
    userOptions.value = records.map(item => ({
      value: item.id,
      label: item.displayName || item.username || item.id
    }))
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载用户失败')
  } finally {
    approverLoading.value = false
  }
}

const openApproverDialog = async (node) => {
  currentPersonNodeId.value = node.id
  selectedApproverIds.value = Array.isArray(node?.data?.approverIds) ? [...node.data.approverIds] : []
  approverDialogVisible.value = true
  if (!userOptions.value.length) {
    await fetchUsers()
  }
}

const saveApproversToNode = () => {
  const nodeId = currentPersonNodeId.value
  if (!nodeId) {
    approverDialogVisible.value = false
    return
  }
  const idSet = new Set(selectedApproverIds.value)
  const names = userOptions.value.filter(item => idSet.has(item.value)).map(item => item.label)
  nodes.value = nodes.value.map(node => {
    if (node.id !== nodeId) return node
    const nextData = {
      ...(node.data || {}),
      approverIds: [...selectedApproverIds.value],
      approverNames: names
    }
    return {
      ...node,
      label: formatPersonNodeLabelByData(nextData),
      data: nextData
    }
  })
  approverDialogVisible.value = false
  ElMessage.success('审批人员已配置，请点击保存流程')
}

const loadDesign = async () => {
  if (!definitionId.value || definitionId.value === 'new') return
  loading.value = true
  try {
    const res = await getProcessDefinitionDesign(definitionId.value)
    const result = res.data
    if (result?.code !== 200) {
      ElMessage.error(result?.msg || '加载流程设计失败')
      return
    }
    const design = result?.data?.designJson || {}
    nodes.value = (Array.isArray(design.nodes) ? design.nodes : []).map(node => {
      if (String(node?.data?.nodeType || '') !== 'person') {
        return node
      }
      return {
        ...node,
        label: formatPersonNodeLabelByData(node.data)
      }
    })
    edges.value = Array.isArray(design.edges) ? design.edges : []
    viewport.value = design.viewport || { x: 0, y: 0, zoom: 1 }
    selectedNodeIds.value = []
    selectedEdgeIds.value = []
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载流程设计失败')
  } finally {
    loading.value = false
  }
}

const removeSelectedEdges = () => {
  const ids = [...selectedEdgeIds.value]
  if (!ids.length) return

  edges.value = edges.value.filter(edge => !ids.includes(edge.id))
  selectedEdgeIds.value = []
  ElMessage.success('已删除连线，请点击保存流程')
}

const removeSelectedNodes = async () => {
  const ids = [...selectedNodeIds.value]
  if (!ids.length) return

  if (!definitionId.value || definitionId.value === 'new') {
    nodes.value = nodes.value.filter(node => !ids.includes(node.id))
    edges.value = edges.value.filter(edge => !ids.includes(edge.source) && !ids.includes(edge.target))
    selectedNodeIds.value = []
    selectedEdgeIds.value = []
    ElMessage.success('已删除节点')
    return
  }

  try {
    for (const id of ids) {
      await deleteProcessNode(definitionId.value, id)
    }
    await loadDesign()
    selectedNodeIds.value = []
    selectedEdgeIds.value = []
    ElMessage.success('已删除节点')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '删除节点失败')
  }
}

const saveDesign = async () => {
  if (!definitionId.value || definitionId.value === 'new') {
    ElMessage.warning('请先在流程定义页面创建流程，再进入设计器保存')
    return
  }
  const payload = {
    nodes: nodes.value,
    edges: edges.value,
    viewport: getViewport()
  }
  try {
    const res = await saveProcessDefinitionDesign(definitionId.value, payload)
    const result = res.data
    if (result?.code === 200) {
      ElMessage.success(result?.msg || '保存成功')
    } else {
      ElMessage.error(result?.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '保存失败')
  }
}

const goBack = () => {
  router.push('/workflow/definition')
}

onMounted(loadDesign)
</script>

<style scoped>
.designer-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.designer-header {
  height: 56px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title {
  font-size: 14px;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.designer-body {
  flex: 1;
  min-height: 0;
  display: flex;
}

.palette {
  width: 220px;
  border-right: 1px solid #ebeef5;
  padding: 12px;
  box-sizing: border-box;
  background: #fafafa;
}

.palette h4 {
  margin: 0 0 12px;
}

.palette-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.palette-item {
  border: 1px dashed #bfcbd9;
  border-radius: 6px;
  padding: 10px;
  background: #fff;
  cursor: grab;
  user-select: none;
}

.canvas {
  flex: 1;
  min-width: 0;
}
</style>
