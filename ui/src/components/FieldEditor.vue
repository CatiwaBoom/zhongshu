<template>
  <div class="field-editor">
    <div class="field-actions">
      <el-button size="small" type="primary" @click="addField">新增字段</el-button>
    </div>
    <el-table :data="fieldsLocal" style="width: 100%" row-key="_tmpId">
      <el-table-column label="#" width="40">
        <template #default="{ $index }">{{$index + 1}}</template>
      </el-table-column>
      <el-table-column prop="fieldName" label="字段显示名" width="200">
        <template #default="{ row }">
          <el-input v-model="row.fieldName" placeholder="请输入字段显示名" @input="onFieldChanged" />
        </template>
      </el-table-column>
      <el-table-column prop="columnName" label="列名" width="200">
        <template #default="{ row }">
          <el-input v-model="row.columnName" placeholder="请输入列名（如 username）" @input="onFieldChanged" />
        </template>
      </el-table-column>
      <el-table-column prop="dataType" label="类型" width="140">
        <template #default="{ row }">
          <el-select v-model="row.dataType" placeholder="类型" @change="onFieldChanged">
            <el-option label="VARCHAR" value="VARCHAR" />
            <el-option label="INT" value="INT" />
            <el-option label="BIGINT" value="BIGINT" />
            <el-option label="DECIMAL" value="DECIMAL" />
            <el-option label="DATE" value="DATE" />
            <el-option label="DATETIME" value="DATETIME" />
            <el-option label="BOOLEAN" value="BOOLEAN" />
            <el-option label="TEXT" value="TEXT" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="length" label="长度/精度" width="180">
        <template #default="{ row }">
          <el-input-number v-model="row.length" :min="1" @change="onFieldChanged" />
        </template>
      </el-table-column>
      <el-table-column prop="defaultValue" label="默认值" width="160">
        <template #default="{ row }">
          <el-input v-model="row.defaultValue" placeholder="默认值（文本）" @input="onFieldChanged" />
        </template>
      </el-table-column>
      <el-table-column prop="isPrimary" label="主键" width="80">
        <template #default="{ row }">
          <!--
            说明：勾选主键时，必须保证与数据库建表逻辑一致：
            1) 主键不能为可空 => 自动取消 isNullable
            2) 主键必然唯一 => 自动勾选 isUnique，并在 UI 中禁止取消
          -->
          <el-checkbox v-model="row.isPrimary" @change="() => { onPrimaryChange(row); onFieldChanged() }" />
        </template>
      </el-table-column>
      <el-table-column prop="isNullable" label="可空" width="80">
        <template #default="{ row }">
          <!-- 如果该字段已经是主键，则不能将其标记为可空 -->
          <el-checkbox v-model="row.isNullable" @change="() => { onNullableChange(row); onFieldChanged() }" />
        </template>
      </el-table-column>
      <el-table-column prop="fieldComment" label="注释" width="160">
        <template #default="{ row }">
          <el-input v-model="row.fieldComment" placeholder="字段注释" @input="onFieldChanged" />
        </template>
      </el-table-column>
      <el-table-column prop="isUnique" label="唯一" width="80">
        <template #default="{ row }">
          <!-- 主键字段必然唯一，主键状态下禁止取消唯一标记 -->
          <el-checkbox v-model="row.isUnique" :disabled="row.isPrimary" @change="onFieldChanged" />
        </template>
      </el-table-column>
      <el-table-column prop="isIndexed" label="索引" width="80">
        <template #default="{ row }">
          <el-checkbox v-model="row.isIndexed" @change="onFieldChanged" />
        </template>
      </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row, $index }">
                      <div class="action-buttons">
                        <el-tooltip content="上移" placement="top">
                          <el-button size="small" circle @click="moveUp($index)" :disabled="$index === 0" aria-label="上移">
                            <ArrowUp />
                          </el-button>
                        </el-tooltip>
                        <el-tooltip content="下移" placement="top">
                          <el-button size="small" circle @click="moveDown($index)" :disabled="$index >= fieldsLocal.length - 1" aria-label="下移">
                            <ArrowDown />
                          </el-button>
                        </el-tooltip>
                        <el-tooltip content="删除" placement="top">
                          <el-button size="small" circle type="danger" @click="remove($index)" aria-label="删除">
                            <DeleteIcon />
                          </el-button>
                        </el-tooltip>
                      </div>
            </template>
          </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, watch, toRaw, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
// 导入 Element Plus 图标组件，避免使用旧的 icon css 类（在新版 Element Plus 中图标需要单独引入）
import { ArrowUp, ArrowDown, Delete as DeleteIcon } from '@element-plus/icons-vue'

// props: fields 通过 v-model 传入（父组件维护字段数组）
const props = defineProps({
  modelValue: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])

const fieldsLocal = ref([])
// 标志：表示当前正在根据 props 更新 fieldsLocal，避免在此期间触发回传导致递归
const isSettingFromProps = ref(false)

// 将 props 同步到本地时进行就地合并（保留已有对象的 _tmpId），避免替换 DOM 节点导致输入框失焦
watch(() => toRaw(props.modelValue), (v) => {
  isSettingFromProps.value = true
  const incoming = v || []
  const newList = []
  // 调试：记录复用数量
  let reuseCount = 0

  // 对每个 incoming 条目，尝试重用 fieldsLocal 中已有对象：优先按 id，然后按 columnName，再按 fieldName，最后按索引位置
  for (let i = 0; i < incoming.length; i++) {
    const item = incoming[i] || {}
    let reused = null
    // 按 id 匹配（最可靠）
    if (item.id) {
      reused = fieldsLocal.value.find(x => x.id && x.id === item.id)
    }
    // 按 columnName 匹配（常用于前端临时生成列名的场景）
    if (!reused && item.columnName) {
      reused = fieldsLocal.value.find(x => x.columnName && x.columnName === item.columnName)
    }
    // 按 fieldName 匹配（次优）
    if (!reused && item.fieldName) {
      reused = fieldsLocal.value.find(x => x.fieldName && x.fieldName === item.fieldName)
    }
    // 按索引位置复用（兜底，避免整体替换）
    if (!reused && i < fieldsLocal.value.length) {
      reused = fieldsLocal.value[i]
    }

    if (reused) {
      // 就地更新已有对象的字段（保留 _tmpId）
      Object.assign(reused, item)
      newList.push(reused)
      reuseCount++
    } else {
      const obj = { _tmpId: item.id || String(Math.random()).slice(2), ...item }
      newList.push(obj)
    }
  }

  // 对新列表中的每一行强制执行字段约束（例如：主键必然唯一且不可空）
  const enforceRowConstraints = (row) => {
    if (!row) return
    // 主键字段必须不可空且唯一
    if (row.isPrimary) {
      row.isNullable = false
      row.isUnique = true
    }
  }
  newList.forEach(enforceRowConstraints)

  // 将 fieldsLocal 替换为新数组（其中已有对象被重用，DOM 节点可被复用）
  // 调试：打印复用信息，帮助排查输入框失焦问题
  try {
    // eslint-disable-next-line no-console
    const beforeKeys = fieldsLocal.value.map(x => x._tmpId)
    const afterKeys = newList.map(x => x._tmpId)
    // eslint-disable-next-line no-console
    console.debug('[FieldEditor] sync props -> local: incoming=', incoming.length, 'beforeKeys=', beforeKeys, 'afterKeys=', afterKeys, 'reuseCount=', reuseCount)
  } catch (e) {}
  fieldsLocal.value = newList
  nextTick(() => { isSettingFromProps.value = false })
}, { immediate: true })

// NOTE: 为了性能（避免深度 watcher 在大量行时频繁遍历），
// 不再使用对 fieldsLocal 的 deep watcher，而是通过用户交互事件触发更新回传（scheduleEmit）。
// props -> local 的同步仍然保留（上面那段 watch），以保证父组件数据更新能正确反映到本地。

// 防抖定时器：用于合并频繁的本地修改，避免每次输入都触发 emit 导致父组件同步回写（进而可能触发子组件重渲染）
let emitTimer = null
const EMIT_DEBOUNCE_MS = 200

// 调度并防抖 emit 更新（由输入事件或显式动作触发）
const scheduleEmit = () => {
  if (isSettingFromProps.value) return
  try {
    if (emitTimer) clearTimeout(emitTimer)
    emitTimer = setTimeout(() => {
      try {
        const out = (fieldsLocal.value || []).map(({ _tmpId, ...rest }) => rest)
        emit('update:modelValue', out)
      } catch (e) {}
      emitTimer = null
    }, EMIT_DEBOUNCE_MS)
  } catch (e) {}
}

// 将用户操作标记为变更（供模板事件调用）
const onFieldChanged = () => {
  // 修改后触发防抖 emit
  scheduleEmit()
}

// 在组件卸载时清理定时器（防止内存泄漏）
try {
  // Vue 插件环境下无法直接使用 onBeforeUnmount 这里以 window unload 兜底
  window.addEventListener('beforeunload', () => { if (emitTimer) clearTimeout(emitTimer) })
} catch (e) {}

const addField = () => {
  const tmp = { _tmpId: String(Math.random()).slice(2), fieldName: '', columnName: '', dataType: 'VARCHAR', length: 255, defaultValue: '', isPrimary: false, isNullable: true, fieldComment: '', isUnique: false, isIndexed: false }
  // 新增时也执行约束检查（虽然初始值不会违反约束，但保持一致性）
  const enforceRowConstraints = (row) => {
    if (!row) return
    if (row.isPrimary) {
      row.isNullable = false
      row.isUnique = true
    }
  }
  enforceRowConstraints(tmp)
  // 使用不可变赋值以确保视图层强制更新
  fieldsLocal.value = fieldsLocal.value.concat(tmp)
  // 触发一次调度回传，通知父组件已有变更
  scheduleEmit()
  // 等待 DOM 更新后打印调试信息，便于在浏览器控制台排查
  nextTick(() => {
    // eslint-disable-next-line no-console
    console.debug('FieldEditor.addField (after tick):', tmp, 'fieldsLocal now:', fieldsLocal.value)
    // 给用户一个可视化提示，帮助确认点击已触发
    try { ElMessage.success('已新增字段（若未显示请检查控制台或刷新）') } catch (e) {}
  })
}

const remove = (index) => {
  fieldsLocal.value.splice(index, 1)
  scheduleEmit()
}

const moveUp = (index) => {
  if (index <= 0) return
  const arr = fieldsLocal.value
  const tmp = arr[index - 1]
  arr[index - 1] = arr[index]
  arr[index] = tmp
  scheduleEmit()
}

const moveDown = (index) => {
  const arr = fieldsLocal.value
  if (index >= arr.length - 1) return
  const tmp = arr[index + 1]
  arr[index + 1] = arr[index]
  arr[index] = tmp
  scheduleEmit()
}

// 当用户勾选/取消主键时的联动处理
const onPrimaryChange = (row) => {
  try {
    // 勾选主键：确保不可空且唯一
    if (row.isPrimary) {
      if (row.isNullable) {
        row.isNullable = false
      }
      if (!row.isUnique) {
        row.isUnique = true
      }
      // 给用户一个提示，方便理解为何其它选项被改变
      try { ElMessage.info('已将该字段设置为主键：自动取消“可空”并勾选“唯一”。') } catch (e) {}
    }
  } catch (e) {
    // 忽略
  }
}

// 当用户尝试将字段设置为可空时的校验：主键字段不能为可空
const onNullableChange = (row) => {
  try {
    if (row.isPrimary && row.isNullable) {
      // 不允许可空为 true，同时给出提示并回退
      try { ElMessage.warning('主键字段不能设置为可空，已恢复为不可空。') } catch (e) {}
      row.isNullable = false
    }
  } catch (e) {}
}
</script>

<style scoped>
.field-actions { margin-bottom: 8px }

.action-buttons { display: flex; gap: 8px; align-items: center; justify-content: center }
</style>

