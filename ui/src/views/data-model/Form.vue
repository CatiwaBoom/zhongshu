<template>
  <div class="data-model-form">
    <el-card>
      <h2>{{ isEdit ? '编辑数据模型' : '创建数据模型' }}</h2>

      <el-form ref="formRef" :model="form" label-width="120px">
        <el-form-item label="模型名称" prop="name" :rules="[{ required: true, message: '请输入模型名称', trigger: 'blur' }]">
          <el-input v-model="form.name" placeholder="模型显示名称，如：用户" />
        </el-form-item>

        <el-form-item label="物理表名" prop="tableName" :rules="[{ required: true, message: '请输入物理表名', trigger: 'blur' }]">
          <el-input v-model="form.tableName" placeholder="物理表名，如：users（建议小写+下划线）" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="模型描述" />
        </el-form-item>

        <el-form-item label="字段定义">
          <FieldEditor v-model="form.fields" />
        </el-form-item>

        <el-form-item>
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" @click="submit">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FieldEditor from '@/components/FieldEditor.vue'
import { getModel, createModel, updateModel } from '@/api/dataModel'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const isEdit = ref(false)

const emptyForm = () => ({ id: '', name: '', tableName: '', description: '', fields: [] })
const form = ref(emptyForm())

onMounted(async () => {
  const id = route.params.id
  if (id) {
    isEdit.value = true
    try {
      const res = await getModel(id)
      const result = res.data
      if (result?.code === 200) {
        Object.assign(form.value, result.data)
      } else {
        ElMessage.error(result?.msg || '加载失败')
      }
    } catch (e) {
      ElMessage.error('加载失败')
    }
  }
})

// 注意：页面滚动条由 Layout 统一控制（全屏页面时隐藏 body 滚动），此处不再重复设置

const goBack = () => router.push({ path: '/models' })

const submit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // ===== 前端校验与自动列名生成 =====
  // 表名规则：以字母（大写或小写）开头，且仅包含字母/数字/下划线，不能以数字开头，长度限制 1..100
  const tableName = (form.value.tableName || '').trim()
  const tableNameRegex = /^[A-Za-z][A-Za-z0-9_]{0,99}$/
  if (!tableNameRegex.test(tableName)) {
    ElMessage.error('物理表名不合法，请以字母开头，仅包含字母/数字/下划线，长度<=100（首字符可为大写或小写字母）')
    return
  }

  // 字段列名自动生成和校验
  const seenCols = new Set()
  for (let i = 0; i < (form.value.fields || []).length; i++) {
    const f = form.value.fields[i]
    // 若 columnName 为空，则基于 fieldName 生成（中文空格替换为下划线，移除非法字符）
    if (!f.columnName || !String(f.columnName).trim()) {
      let candidate = String(f.fieldName || '').trim().toLowerCase()
      // 将空格与中文标点替换为下划线，去掉非字母数字下划线字符
      candidate = candidate.replace(/\s+/g, '_').replace(/[^a-z0-9_]/gi, '_')
      candidate = candidate.replace(/_+/g, '_').replace(/^_+|_+$/g, '')
      if (!candidate) candidate = 'col' + i
      if (!/^[a-z]/.test(candidate)) candidate = 'c_' + candidate
      f.columnName = candidate
    }
    const col = String(f.columnName).trim().toLowerCase()
    // 列名合法性校验
    if (!/^[a-z][a-z0-9_]{0,99}$/.test(col)) {
      ElMessage.error('字段列名不合法：' + f.columnName + '，请使用小写字母开头，仅包含小写字母/数字/下划线')
      return
    }
    if (seenCols.has(col)) {
      ElMessage.error('存在重复的列名：' + col)
      return
    }
    seenCols.add(col)
  }

  try {
    if (isEdit.value) {
      const res = await updateModel(form.value.id, form.value)
      const result = res.data
      if (result?.code === 200) {
        ElMessage.success('更新成功')
        goBack()
      } else {
        ElMessage.error(result?.msg || '更新失败')
      }
    } else {
      const res = await createModel(form.value)
      const result = res.data
      if (result?.code === 200) {
        ElMessage.success('创建成功')
        goBack()
      } else {
        ElMessage.error(result?.msg || '创建失败')
      }
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}
</script>

<style scoped>
/*
  全屏表单样式：在全屏布局下（侧栏与头部隐藏）让表单占据整个视口，
  并把表单区域设为内部可滚动，从而避免出现页面底部全局滚动条。
*/
.data-model-form {
  /* 在父级全屏容器内绝对填充，避免影响 body 或其他布局 */
  position: absolute !important;
  inset: 0 !important; /* top:0; right:0; bottom:0; left:0 */
  box-sizing: border-box !important;
  padding: 12px !important; /* 页面内边距，避免贴边 */
  background: transparent !important;
  z-index: 9999 !important;
}

.data-model-form > .el-card {
  height: 100% !important; /* 卡片占满容器高度 */
  display: flex !important;
  flex-direction: column !important;
  box-sizing: border-box !important;
  padding: 16px !important;
  overflow: hidden !important; /* 卡片内部滚动由 el-form 控制 */
}

.data-model-form h2 {
  margin: 0 0 12px 0;
}

/* 表单主体可滚动，避免页面滚动 */
.data-model-form .el-form {
  flex: 1 1 auto !important;
  min-height: 0 !important; /* 允许子元素收缩，避免 overflow 导致外部滚动 */
  overflow: auto !important; /* 表单内部滚动，避免 body 出现滚动条 */
  padding-right: 8px !important; /* 避免右侧遮挡滚动条 */
}

.data-model-form .el-form-item {
  margin-bottom: 12px;
}
</style>

