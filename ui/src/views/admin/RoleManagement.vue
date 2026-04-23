<template>
  <div>
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="circle"></div>
      <div class="circle"></div>
    </div>

    <!-- 头部 -->
    <div class="header">
      <div class="header-container">
        <div class="logo">
          <i class="fas fa-user-shield"></i>
          权限管理系统
        </div>
        <div style="display:flex;gap:12px">
          <button class="btn btn-outline" @click="load">
            <i class="fas fa-sync-alt"></i>
            刷新
          </button>
          <button class="btn btn-primary" @click="openCreateDialog">
            <i class="fas fa-plus"></i>
            新建角色
          </button>
        </div>
      </div>
    </div>

    <div class="container">
      <h1 class="page-title"><i class="fas fa-user-tag"></i> 角色管理</h1>
      <p class="page-desc">管理系统角色、权限分配及人员授权</p>

      <div class="toolbar">
        <div class="search-box">
          <i class="fas fa-search"></i>
          <input v-model="searchTerm" placeholder="搜索角色名称、权限..." />
        </div>
        <div>
          <button class="btn btn-outline" @click="() => {}"><i class="fas fa-filter"></i> 筛选</button>
        </div>
      </div>

      <div class="roles-grid">
        <div class="role-card" v-for="role in displayedRoles" :key="role.id">
          <div class="role-header">
            <div>
              <h3 class="role-name">{{ role.name }}</h3>
              <p class="role-desc">{{ role.description }}</p>
            </div>
            <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px">
              <span class="role-status status-active">启用</span>
              <div style="display:flex;gap:6px">
                <button class="btn btn-outline" @click="openEditDialog(role)">编辑</button>
                <button class="btn btn-outline" @click="remove(role)">删除</button>
              </div>
            </div>
          </div>

          <div class="role-perms">
            <!-- display first three assigned menu titles; show ellipsis if more than 3 -->
            <span class="perm-tag" v-for="(p, idx) in (role.menuTitles || []).slice(0,3)" :key="idx">{{ p }}</span>
            <span v-if="(role.menuTitles || []).length === 0" class="perm-tag">-</span>
            <span v-if="(role.menuTitles || []).length > 3" class="perm-tag">+{{ (role.menuTitles || []).length - 3 }}</span>
          </div>

          <div class="role-footer">
            <div class="user-count">已分配：<span>{{ role.userCount ?? role.usersCount ?? 0 }}</span> 人</div>
            <div>
              <button class="btn btn-primary" @click="assignMenus(role)"><i class="fas fa-sitemap"></i> 分配菜单</button>
              <button class="btn btn-primary"  @click="assignUsers(role)"><i class="fas fa-user-plus"></i> 分配人员</button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="displayedRoles.length===0" class="empty-state">
        <i class="fas fa-box-open"></i>
        <div>暂无角色</div>
      </div>
    </div>

    <!-- 原来的对话框仍保留以复用现有逻辑 -->
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
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoleList, createRole, updateRole, deleteRole, getRoleMenus, assignRoleMenus, getRoleUsers, assignRoleUsers } from '@/api/role'
import { getMenuTree } from '@/api/systemMenu'
import { getUserList } from '@/api/user'

// state
const roles = ref([])
const searchTerm = ref('')
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

// computed / derived
const displayedRoles = computed(() => {
  const q = (searchTerm.value || '').toString().trim().toLowerCase()
  if (!q) return roles.value || []
  return (roles.value || []).filter(r => {
    const name = (r.name || '').toString().toLowerCase()
    const code = (r.code || '').toString().toLowerCase()
    const desc = (r.description || '').toString().toLowerCase()
    const permsArray = (r.menuTitles || (r.perms || r.permissions) || [])
    const perms = (Array.isArray(permsArray) ? permsArray.join(',') : permsArray).toString().toLowerCase() || ''
    return name.includes(q) || code.includes(q) || desc.includes(q) || perms.includes(q)
  })
})

// load roles
const load = async () => {
  try {
    // load menu tree and role list in parallel for faster initial render
    const [, roleRes] = await Promise.all([loadMenuTree(), getRoleList()])

    // loadMenuTree already sets menuTree.value; now handle roles
    const result = roleRes && roleRes.data
    if (result?.code === 200) {
      roles.value = Array.isArray(result.data) ? result.data : (result.data || [])
    } else {
      roles.value = Array.isArray(roleRes) ? roleRes : (roleRes?.data || [])
    }
    // kick off background prefetch of per-role details (limited concurrency)
    prefetchAllRoleDetails(4)
  } catch (e) {
    roles.value = []
  }
}

    // when component mounted, load roles (load will kick off background prefetch)
    onMounted(() => { load() })

// menu id -> title map (computed from menuTree)
const menuTitleMap = computed(() => {
  const map = {}
  const dfs = (node) => {
    if (!node) return
    if (node.id && node.title) map[node.id] = node.title
    if (node.children && node.children.length) node.children.forEach(dfs)
  }
  (menuTree.value || []).forEach(dfs)
  return map
})

// fetch per-role menus and users (cached on role object). This runs in background with limited concurrency.
const prefetchRoleDetails = async (role) => {
  if (!role || role._detailsLoaded) return
  role._detailsLoaded = 'loading'
  try {
    const [menusRes, usersRes] = await Promise.allSettled([getRoleMenus(role.id), getRoleUsers(role.id)])
    // menus
    if (menusRes.status === 'fulfilled') {
      const r = menusRes.value && menusRes.value.data
      const assigned = (r?.code === 200) ? (Array.isArray(r.data) ? r.data : []) : (Array.isArray(r) ? r : (r?.data || []))
      role.menuIds = Array.isArray(assigned) ? assigned : []
      role.menuTitles = (role.menuIds || []).map(id => menuTitleMap.value[id]).filter(Boolean)
    } else {
      role.menuIds = []
      role.menuTitles = []
    }
    // users
    if (usersRes.status === 'fulfilled') {
      const u = usersRes.value && usersRes.value.data
      const assignedUsers = (u?.code === 200) ? (Array.isArray(u.data) ? u.data : []) : (Array.isArray(u) ? u : (u?.data || []))
      role.userCount = Array.isArray(assignedUsers) ? assignedUsers.length : 0
    } else {
      role.userCount = 0
    }
  } catch (e) {
    role.menuIds = []
    role.menuTitles = []
    role.userCount = 0
  }
  role._detailsLoaded = true
}

const prefetchAllRoleDetails = async (concurrency = 4) => {
  const list = roles.value || []
  if (!list.length) return
  // eager fetch first few visible roles to improve perceived responsiveness
  const eagerCount = Math.min(10, list.length)
  await Promise.all(list.slice(0, eagerCount).map(r => prefetchRoleDetails(r)))

  // fetch remaining with limited concurrency
  let idx = eagerCount
  const worker = async () => {
    while (true) {
      const i = idx++
      if (i >= list.length) break
      const role = list[i]
      if (role._detailsLoaded) continue
      // eslint-disable-next-line no-await-in-loop
      await prefetchRoleDetails(role)
    }
  }
  const workers = []
  for (let i = 0; i < concurrency; i++) workers.push(worker())
  await Promise.all(workers)
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
      await prefetchRoleDetails(currentRole.value)
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
      await prefetchRoleDetails(currentRole.value)
    } else {
      ElMessage.error(result?.msg || '分配失败')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '分配失败')
  }
}

// (onMounted handled above)
</script>

<style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
      font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    }

    :root {
      --primary: #165DFF;
      --primary-light: #4080FF;
      --secondary: #4080FF;
      --accent: #FF7D00;
      --success: #00B42A;
      --warning: #FF7D00;
      --dark: #ffffff;
      --dark-light: #f5f7fa;
      --card: #ffffff;
      --text: #1D2129;
      --text-muted: #4E5969;
      --border: #e5e6eb;
      --shadow: 0 10px 30px rgba(0,0,0,0.08);
      --glass: rgba(255, 255, 255, 0.85);
      --radius: 16px;
      --transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .bg-decoration {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 0;
      opacity: 0.1;
    }

    .circle {
      position: absolute;
      border-radius: 50%;
      background: linear-gradient(90deg, var(--primary), var(--secondary));
      filter: blur(80px);
    }

    .circle:nth-child(1) {
      width: 400px;
      height: 400px;
      top: 10%;
      left: 80%;
    }

    .circle:nth-child(2) {
      width: 300px;
      height: 300px;
      top: 60%;
      left: 10%;
    }

    .header {
      background: var(--glass);
      backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--border);
      padding: 18px 30px;
      position: sticky;
      top: 0;
      z-index: 100;
      box-shadow: var(--shadow);
    }

    .header-container {
      max-width: 1400px;
      margin: 0 auto;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .logo {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 22px;
      font-weight: 700;
      color: var(--primary);
    }

    .logo i {
      font-size: 26px;
      color: var(--primary);
    }

    .container {
      max-width: 1400px;
      margin: 40px auto;
      padding: 0 20px;
      position: relative;
      z-index: 1;
    }

    .page-title {
      font-size: 28px;
      font-weight: 700;
      margin-bottom: 10px;
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .page-title i {
      color: var(--primary);
    }

    .page-desc {
      color: var(--text-muted);
      margin-bottom: 30px;
      font-size: 15px;
    }

    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 30px;
      flex-wrap: wrap;
      gap: 15px;
    }

    .search-box {
      background: var(--dark);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 12px 16px;
      display: flex;
      align-items: center;
      gap: 10px;
      width: 320px;
      transition: var(--transition);
    }

    .search-box:focus-within {
      border-color: var(--primary);
      box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.1);
    }

    .search-box i {
      color: var(--text-muted);
    }

    .search-box input {
      background: transparent;
      border: none;
      outline: none;
      color: var(--text);
      width: 100%;
      font-size: 14px;
    }

    .search-box input::placeholder {
      color: var(--text-muted);
    }

    .btn {
      padding: 12px 20px;
      border-radius: 12px;
      border: none;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: var(--transition);
      font-size: 14px;
    }

    .btn-primary {
      background: linear-gradient(90deg, var(--primary), var(--secondary));
      color: white;
      box-shadow: 0 4px 15px rgba(22, 93, 255, 0.3);
    }

    .btn-primary:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(22, 93, 255, 0.4);
    }

    .btn-outline {
      background: transparent;
      border: 1px solid var(--border);
      color: var(--text);
    }

    .btn-outline:hover {
      border-color: var(--primary);
      color: var(--primary);
    }

    .roles-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 25px;
    }

    .role-card {
      background: var(--card);
      border-radius: var(--radius);
      padding: 25px;
      border: 1px solid var(--border);
      transition: var(--transition);
      position: relative;
      overflow: hidden;
      box-shadow: var(--shadow);
    }

    .role-card::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 4px;
      background: linear-gradient(90deg, var(--primary), var(--secondary));
      transform: scaleX(0);
      transform-origin: left;
      transition: var(--transition);
    }

    .role-card:hover {
      transform: translateY(-6px);
      border-color: var(--primary);
      box-shadow: 0 15px 35px rgba(22, 93, 255, 0.1);
    }

    .role-card:hover::before {
      transform: scaleX(1);
    }

    .role-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 18px;
    }

    .role-name {
      font-size: 18px;
      font-weight: 600;
      margin-bottom: 6px;
    }

    .role-desc {
      color: var(--text-muted);
      font-size: 13px;
    }

    .role-status {
      padding: 5px 10px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 600;
    }

    .status-active {
      background: rgba(0, 180, 42, 0.1);
      color: var(--success);
    }

    .role-perms {
      margin: 20px 0;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .perm-tag {
      padding: 5px 10px;
      background: #f5f7fa;
      border-radius: 6px;
      font-size: 12px;
      color: var(--text-muted);
      border: 1px solid var(--border);
    }

    .role-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 15px;
      border-top: 1px solid var(--border);
    }

    .user-count {
      font-size: 13px;
      color: var(--text-muted);
    }

    .user-count span {
      color: var(--primary);
      font-weight: 600;
    }

    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.4);
      backdrop-filter: blur(6px);
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      visibility: hidden;
      transition: var(--transition);
    }

    .modal-overlay.active {
      opacity: 1;
      visibility: visible;
    }

    .modal {
      background: var(--dark);
      border-radius: var(--radius);
      width: 90%;
      max-width: 700px;
      max-height: 80vh;
      overflow: hidden;
      border: 1px solid var(--border);
      box-shadow: 0 25px 50px rgba(0,0,0,0.1);
      transform: translateY(30px) scale(0.95);
      transition: var(--transition);
    }

    .modal-overlay.active .modal {
      transform: translateY(0) scale(1);
    }

    .modal-header {
      padding: 22px 25px;
      border-bottom: 1px solid var(--border);
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .modal-title {
      font-size: 18px;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .modal-title i {
      color: var(--primary);
    }

    .close-btn {
      background: transparent;
      border: none;
      color: var(--text-muted);
      font-size: 20px;
      cursor: pointer;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: var(--transition);
    }

    .close-btn:hover {
      background: #f5f7fa;
      color: var(--text);
    }

    .modal-body {
      padding: 25px;
      max-height: 60vh;
      overflow-y: auto;
    }

    .selected-role {
      background: #f5f7fa;
      padding: 15px 20px;
      border-radius: 12px;
      margin-bottom: 20px;
      border: 1px solid var(--border);
    }

    .selected-role-name {
      font-weight: 600;
      color: var(--primary);
    }

    .user-list {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 15px;
      margin-top: 20px;
    }

    .user-item {
      background: var(--dark);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 15px;
      display: flex;
      align-items: center;
      gap: 12px;
      transition: var(--transition);
      cursor: pointer;
    }

    .user-item:hover {
      border-color: var(--primary);
      background: rgba(22, 93, 255, 0.05);
    }

    .user-item.selected {
      border-color: var(--primary);
      background: rgba(22, 93, 255, 0.08);
    }

    .user-avatar {
      width: 42px;
      height: 42px;
      border-radius: 50%;
      background: linear-gradient(90deg, var(--primary), var(--secondary));
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      flex-shrink: 0;
      color: white;
    }

    .user-info {
      flex: 1;
    }

    .username {
      font-weight: 600;
      font-size: 14px;
      margin-bottom: 3px;
    }

    .user-dept {
      font-size: 12px;
      color: var(--text-muted);
    }

    .user-check {
      width: 20px;
      height: 20px;
      accent-color: var(--primary);
    }

    .modal-footer {
      padding: 20px 25px;
      border-top: 1px solid var(--border);
      display: flex;
      justify-content: flex-end;
      gap: 15px;
    }

    ::-webkit-scrollbar {
      width: 8px;
      height: 8px;
    }

    ::-webkit-scrollbar-track {
      background: #f5f7fa;
    }

    ::-webkit-scrollbar-thumb {
      background: var(--border);
      border-radius: 4px;
    }

    ::-webkit-scrollbar-thumb:hover {
      background: var(--primary);
    }

    .empty-state {
      text-align: center;
      padding: 60px 20px;
      color: var(--text-muted);
    }

    .empty-state i {
      font-size: 60px;
      margin-bottom: 20px;
      opacity: 0.5;
    }

    @media (max-width: 768px) {
      .roles-grid {
        grid-template-columns: 1fr;
      }

      .toolbar {
        flex-direction: column;
        align-items: stretch;
      }

      .search-box {
        width: 100%;
      }
    }

</style>

