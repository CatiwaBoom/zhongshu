<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h3>系统菜单管理</h3>
      <el-button type="primary" @click="showCreate">新建菜单</el-button>
    </div>

    <el-tree
      :data="treeData"
      node-key="id"
      :props="treeProps"
      default-expand-all
      style="background:#fff;padding:12px;border:1px solid #ebeef5;border-radius:6px"
    >
      <template #default="{ node, data }">
        <span>{{ data.title }}</span>
        <span style="float:right">
          <el-button type="text" size="small" @click.stop="edit(data)">编辑</el-button>
          <el-button type="text" size="small" @click.stop="remove(data)">删除</el-button>
        </span>
      </template>
    </el-tree>

    <el-dialog title="菜单" v-model="dialogVisible">
      <el-form :model="form">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="form.path" placeholder="如 /datasource 或空（若仅作为分组）" />
        </el-form-item>
        <el-form-item label="父菜单">
          <el-select v-model="form.parentId" clearable placeholder="顶级">
            <el-option v-for="n in flatOptions" :key="n.id" :label="n.title" :value="n.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="图标">
          <el-popover v-model:visible="iconPanelVisible" placement="bottom" width="420">
            <div style="padding:8px">
              <el-input v-model="iconSearch" size="small" placeholder="搜索图标名称（支持英文）" clearable style="margin-bottom:8px" />
              <div class="icon-grid">
                <div v-for="name in visibleIcons" :key="name" class="icon-cell" @click="selectIcon(name)">
                  <component v-if="iconsMap[name]" :is="iconsMap[name]" class="icon-sample" />
                  <div class="icon-name">{{ name }}</div>
                </div>
              </div>
              <div style="text-align:center;margin-top:8px">
                <el-button v-if="filteredIconNames.length > showCount" type="text" @click="loadMoreIcons">加载更多</el-button>
                <span v-else style="color:#909399;font-size:12px">共 {{ filteredIconNames.length }} 个图标</span>
              </div>
            </div>
            <template #reference>
              <el-button type="primary" plain style="display:inline-flex;align-items:center;gap:8px">
                <component v-if="form.icon" :is="iconsMap[form.icon]" />
                <span v-if="form.icon">{{ form.icon }}</span>
                <span v-else>选择图标</span>
              </el-button>
            </template>
          </el-popover>
          <div style="margin-top:8px;display:flex;align-items:center;gap:8px">
            <span>预览：</span>
            <component :is="selectedPreview" v-if="selectedPreview" />
            <span v-else style="color:#909399">未选择</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import { getMenuTree, createMenu, updateMenu, deleteMenu } from '@/api/systemMenu'
import { usePermissionStore } from '@/stores/permission'

const treeData = ref([])
const dialogVisible = ref(false)
const form = ref({ id: null, title: '', path: '', parentId: null, icon: '' })
const permissionStore = usePermissionStore()

const treeProps = { children: 'children', label: 'title' }

// icon map & list for selector — 使用整个 icons 包，方便扩展
const iconsMap = Icons
// build a curated list to avoid rendering too many icons at once
const allIconNames = Object.keys(iconsMap).filter(n => typeof iconsMap[n] === 'object' || typeof iconsMap[n] === 'function').sort()
// take a moderate number as curated set (adjustable)
const curatedIconNames = allIconNames.slice(0, 80)

const iconPanelVisible = ref(false)
const iconSearch = ref('')
const showCount = ref(24)

const filteredIconNames = computed(() => {
  const q = (iconSearch.value || '').trim().toLowerCase()
  if (!q) return curatedIconNames
  return curatedIconNames.filter(n => n.toLowerCase().includes(q))
})

const visibleIcons = computed(() => filteredIconNames.value.slice(0, showCount.value))

const selectIcon = (name) => {
  form.value.icon = name
  iconPanelVisible.value = false
}

const loadMoreIcons = () => { showCount.value = Math.min(filteredIconNames.value.length, showCount.value + 24) }

watch(iconSearch, () => { showCount.value = 24 })

const selectedPreview = computed(() => {
  return iconsMap[form.value.icon] || null
})

const load = async () => {
  try {
    const res = await getMenuTree()
    const data = res && res.data
    const menus = Array.isArray(data) ? data : (data?.data || data?.records || [])
    treeData.value = menus
    // 同步到 permission store，其他地方可以使用
    try { permissionStore.menuTree = menus } catch (e) {}
  } catch (e) {
    treeData.value = []
  }
}

const flatOptions = computed(() => {
  const out = []
  const walk = (nodes) => {
    if (!Array.isArray(nodes)) return
    nodes.forEach(n => { out.push({ id: n.id, title: n.title }); walk(n.children) })
  }
  walk(treeData.value)
  return out
})

const showCreate = () => { form.value = { id: null, title: '', path: '', parentId: null, icon: '' }; dialogVisible.value = true }
const edit = (row) => { form.value = { ...row, parentId: row.parentId || null }; dialogVisible.value = true }

const save = async () => {
  try {
    if (form.value.id) {
      await updateMenu(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await createMenu(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const remove = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除此菜单及其子节点？', '确认', { type: 'warning' })
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => { load() })
</script>

<style scoped>
h3 { margin: 0 0 8px 0 }

.icon-grid{
  display:grid;
  grid-template-columns: repeat(6, 1fr);
  gap:8px;
  max-height:320px;
  overflow:auto;
  padding:8px;
}
.icon-cell{
  display:flex;
  flex-direction:column;
  align-items:center;
  justify-content:center;
  padding:8px;
  border-radius:6px;
  cursor:pointer;
  transition:all .12s ease;
  background:#fff;
  border:1px solid transparent;
}
.icon-cell:hover{ border-color:#e6f0ff; transform:translateY(-4px); box-shadow:0 6px 10px rgba(16,66,145,0.06)}
.icon-sample{ font-size:20px }
.icon-name{ margin-top:6px; font-size:12px; color:#606266 }
</style>


