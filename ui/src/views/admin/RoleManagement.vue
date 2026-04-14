<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h3>角色管理</h3>
      <el-button type="primary" @click="openCreateDialog">新建角色</el-button>
    </div>

    <el-table :data="roles" style="width:100%">
      <!-- ID 列不再展示 -->
      <el-table-column prop="name" label="角色名称" />
      <el-table-column prop="description" label="描述" />
              <el-table-column label="操作" width="300">
                <template #default="{ row }">
                  <el-button type="text" @click="openEditDialog(row)">编辑</el-button>
                  <el-button type="text" @click="remove(row)">删除</el-button>
                  <el-button type="text" @click="assignMenus(row)">分配菜单</el-button>
                  <el-button type="text" @click="assignUsers(row)">分配人员</el-button>
                </template>
              </el-table-column>
    </el-table>

    <el-dialog title="角色" v-model="dialogVisible">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="用于权限判断的唯一编码，例如 ROLE_ADMIN" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="分配菜单" v-model="assignDialogVisible" width="520px">
      <div style="max-height:420px;overflow:auto">
        <el-tree
          :data="menuTree"
          node-key="id"
          :props="treeProps"
          show-checkbox
          default-expand-all
          ref="menuTreeRef"
          :check-strictly="false"
          :highlight-current="false"
        />
      </div>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAssign">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="分配人员" v-model="assignUsersDialogVisible" width="720px">
      <div style="min-height:200px;padding:12px 0">
        <el-transfer
          v-model="transferTargetKeys"
          :data="transferData"
          :props="transferProps"
          filterable
          :titles="['可选人员','已选人员']"
        />
      </div>
      <template #footer>
        <el-button @click="assignUsersDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAssignUsers">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoleList, createRole, updateRole, deleteRole, getRoleMenus, assignRoleMenus, getRoleUsers, assignRoleUsers } from '@/api/role'
import { getMenuTree } from '@/api/systemMenu'
import { getUserList } from '@/api/user'

// state
const roles = ref([])
const dialogVisible = ref(false)
const dialogMode = ref('create')
const form = reactive({ id: '', name: '', code: '', description: '' })
const assignDialogVisible = ref(false)
const menuTree = ref([])
const menuTreeRef = ref(null)
const currentRole = ref(null)
const treeProps = { children: 'children', label: 'title' }
const assignUsersDialogVisible = ref(false)
const users = ref([])
const transferData = ref([])
const transferTargetKeys = ref([])
const transferProps = { key: 'key', label: 'label' }

// load roles
const load = async () => {
  try {
    const res = await getRoleList()
    const result = res && res.data
    if (result?.code === 200) {
      roles.value = Array.isArray(result.data) ? result.data : (result.data || [])
    } else {
      roles.value = Array.isArray(result) ? result : (result?.data || [])
    }
  } catch (e) {
    roles.value = []
  }
}

const loadMenuTree = async () => {
  try {
    const res = await getMenuTree()
    const result = res && res.data
    if (result?.code === 200) menuTree.value = result.data || []
    else menuTree.value = Array.isArray(result) ? result : (result?.data || [])
  } catch (e) {
    menuTree.value = []
  }
}

// open dialogs
const openCreateDialog = () => {
  dialogMode.value = 'create'
  Object.assign(form, { id: '', name: '', description: '' })
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  dialogMode.value = 'edit'
  Object.assign(form, { id: row.id || '', name: row.name || '', code: row.code || '', description: row.description || '' })
  dialogVisible.value = true
}

const assignMenus = async (row) => {
  currentRole.value = row
  await loadMenuTree()
  try {
    const res = await getRoleMenus(row.id)
    const result = res && res.data
    const ids = (result?.code === 200) ? (Array.isArray(result.data) ? result.data : []) : (Array.isArray(result) ? result : (result?.data || []))
    // set checked keys on tree (delay to ensure tree rendered)
    setTimeout(() => {
      try {
        const tree = menuTreeRef.value
        if (tree && tree.setCheckedKeys) tree.setCheckedKeys(ids)
      } catch (e) {}
    }, 50)
  } catch (e) {}
  assignDialogVisible.value = true
}

// 分配人员：打开 transfer 弹窗，加载所有用户并设置已选
const assignUsers = async (row) => {
  currentRole.value = row

    try {
      // 获取全部用户（简单分页参数以保证返回列表较大）
      const res = await getUserList({ page: 1, pageSize: 10000 })
      const result = res && res.data
      // 支持分页响应：result.data.records
      let list = []
      if (result?.code === 200) {
        if (Array.isArray(result.data)) list = result.data
        else if (result.data && Array.isArray(result.data.records)) list = result.data.records
        else list = []
      } else {
        if (Array.isArray(result)) list = result
        else if (result && Array.isArray(result.data)) list = result.data
        else list = []
      }
      users.value = list
      transferData.value = list.map(u => ({ key: String(u.id), label: u.displayName || u.username || u.name || String(u.id) }))

    // 获取当前角色已分配用户
    try {
      const r = await getRoleUsers(row.id)
      const rd = r && r.data
      const assigned = (rd?.code === 200) ? (Array.isArray(rd.data) ? rd.data : []) : (Array.isArray(rd) ? rd : (rd?.data || []))
      // assigned 可能是 id 列表或用户对象数组
      const ids = assigned.map(a => (a && (a.id || a))) .filter(Boolean).map(String)
      transferTargetKeys.value = ids
    } catch (e) {
      transferTargetKeys.value = []
    }

    assignUsersDialogVisible.value = true
  } catch (e) {
    ElMessage.error('加载用户失败')
  }
}

// save create/update
const save = async () => {
  if (!form.name || !form.name.trim()) {
    ElMessage.error('请输入角色名称')
    return
  }
  if (!form.code || !form.code.trim()) {
    ElMessage.error('请输入角色编码')
    return
  }
  try {
    const payload = { name: form.name, code: form.code, description: form.description }
    let res
    if (dialogMode.value === 'edit' && form.id) {
      res = await updateRole(form.id, payload)
    } else {
      res = await createRole(payload)
    }
    const result = res && res.data
    if (result?.code === 200) {
      ElMessage.success(result.msg || '保存成功')
      dialogVisible.value = false
      await load()
    } else {
      ElMessage.error(result?.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '保存失败')
  }
}

// delete with confirmation
const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.name || row.id}」吗？`, '删除确认', { type: 'warning' })
    const res = await deleteRole(row.id)
    const result = res && res.data
    if (result?.code === 200) {
      ElMessage.success('删除成功')
      await load()
    } else {
      ElMessage.error(result?.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('删除失败')
  }
}

const saveAssign = async () => {
  try {
    const tree = menuTreeRef.value
    let checked = []
    if (tree && tree.getCheckedKeys) checked = tree.getCheckedKeys()
    const res = await assignRoleMenus(currentRole.value.id, checked)
    const result = res && res.data
    if (result?.code === 200) {
      ElMessage.success(result.msg || '分配成功')
      assignDialogVisible.value = false
    } else {
      ElMessage.error(result?.msg || '分配失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '分配失败')
  }
}

const saveAssignUsers = async () => {
  try {
    const res = await assignRoleUsers(currentRole.value.id, transferTargetKeys.value)
    const result = res && res.data
    if (result?.code === 200) {
      ElMessage.success(result.msg || '分配成功')
      assignUsersDialogVisible.value = false
    } else {
      ElMessage.error(result?.msg || '分配失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '分配失败')
  }
}

onMounted(() => { load() })
</script>

<style scoped>
h3 { margin: 0 0 8px 0 }
</style>

