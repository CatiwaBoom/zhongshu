<template>
            <div class="data-source-page">
              <!-- 功能栏 -->
              <section class="toolbar-card">
                <div class="toolbar-row">
                  <el-input
                    v-model="query.keyword"
                    class="toolbar-search"
                    placeholder="搜索数据源名称/类型/地址..."
                    clearable
                  >
                    <template #prefix>
                      <el-icon><Search /></el-icon>
                    </template>
                  </el-input>

                  <div class="toolbar-actions">
                    <el-select v-model="query.type" placeholder="全部类型" clearable>
                      <el-option
                        v-for="item in typeOptions"
                        :key="item"
                        :label="item"
                        :value="item"
                      />
                    </el-select>

                    <el-select v-model="query.connectivity" placeholder="全部状态" clearable>
                      <el-option label="已连接" value="connected" />
                      <el-option label="异常" value="abnormal" />
                      <el-option label="未连接" value="disconnected" />
                    </el-select>

                    <el-button type="primary" :icon="Plus" @click="openCreateDialog">
                      新增数据源
                    </el-button>
                  </div>
                </div>
              </section>

              <!-- 统计卡片 -->
              <section class="stats-grid">
                <div class="stat-card">
                  <div class="stat-head">
                    <div>
                      <div class="stat-label">总数据源</div>
                      <div class="stat-value">{{ stats.total }}</div>
                    </div>
                    <div class="stat-icon is-primary">DS</div>
                  </div>
                  <div class="stat-tip">当前筛选结果总数</div>
                </div>

                <div class="stat-card">
                  <div class="stat-head">
                    <div>
                      <div class="stat-label">正常连接</div>
                      <div class="stat-value">{{ stats.connected }}</div>
                    </div>
                    <div class="stat-icon is-success">OK</div>
                  </div>
                  <div class="stat-tip">连接状态正常</div>
                </div>

                <div class="stat-card">
                  <div class="stat-head">
                    <div>
                      <div class="stat-label">异常连接</div>
                      <div class="stat-value">{{ stats.abnormal }}</div>
                    </div>
                    <div class="stat-icon is-warning">ER</div>
                  </div>
                  <div class="stat-tip">启用但连接失败</div>
                </div>

                <div class="stat-card">
                  <div class="stat-head">
                    <div>
                      <div class="stat-label">未连接</div>
                      <div class="stat-value">{{ stats.disconnected }}</div>
                    </div>
                    <div class="stat-icon is-danger">NO</div>
                  </div>
                  <div class="stat-tip">通常为禁用或未检测</div>
                </div>
              </section>

              <!-- 数据源列表 -->
              <section class="list-card">
                <div class="list-header">
                  <div>
                    <h2>数据源列表</h2>
                  </div>
                  <div class="list-total">共 {{ displayTotal }} 条数据</div>
                </div>

                <div v-loading="loading" class="list-body">
                  <template v-if="pagedList.length">
                    <div class="source-list">
                      <article
                        v-for="item in pagedList"
                        :key="item.id"
                        class="source-item"
                      >
                        <div class="source-item__top">
                          <div class="source-basic">
                            <div
                              class="source-icon"
                              :class="`source-icon--${getTypeMeta(item).className}`"
                            >
                              {{ getTypeMeta(item).short }}
                            </div>

                            <div class="source-title-wrap">
                              <div class="source-title">{{ item.name || '--' }}</div>
                              <div class="source-subtitle">
                                {{ resolveDbType(item) }} · {{ item.url || '--' }}
                              </div>
                            </div>
                          </div>

                          <div class="source-ops">
                            <el-tag :type="getConnectivityInfo(item).tagType" effect="light">
                              <span class="status-dot" :class="getConnectivityInfo(item).dotClass" />
                              {{ getConnectivityInfo(item).label }}
                            </el-tag>

                            <el-tag :type="item.status === 1 ? 'success' : 'info'" effect="plain">
                              {{ item.status === 1 ? '启用' : '禁用' }}
                            </el-tag>

                            <div class="op-buttons">
                              <el-button
                                circle
                                :icon="Edit"
                                @click="openEditDialog(item)"
                              />
                              <el-button
                                circle
                                :icon="RefreshRight"
                                @click="handleTestConnect(item)"
                              />
                              <el-button
                                circle
                                :icon="Delete"
                                type="danger"
                                plain
                                @click="handleDelete(item)"
                              />
                            </div>
                          </div>
                        </div>

                        <div class="source-meta">
                          <div><span>驱动类名：</span>{{ item.driverClassName || '--' }}</div>
                          <div><span>用户名：</span>{{ item.username || '--' }}</div>
                          <div><span>主键ID：</span>{{ item.id || '--' }}</div>
                          <div><span>更新时间：</span>{{ formatTime(item.updatedAt) }}</div>
                        </div>
                      </article>
                    </div>
                  </template>

                  <el-empty v-else description="暂无数据源数据" />
                </div>

                <div class="list-footer" v-if="total !== null || (list && list.length > 0)">
                  <div class="footer-text">
                    显示 {{ pageStart }}-{{ pageEnd }} 条，共 {{ displayTotal }} 条
                  </div>

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
              </section>

              <!-- 新增 / 编辑弹窗 -->
              <el-dialog
                v-model="dialogVisible"
                :title="dialogMode === 'create' ? '新增数据源' : '编辑数据源'"
                width="720px"
                destroy-on-close
              >
                <el-form
                  ref="formRef"
                  :model="form"
                  :rules="rules"
                  label-width="96px"
                >
                  <el-row :gutter="16">
                    <el-col :span="12">
                      <el-form-item label="数据源名称" prop="name">
                        <el-input v-model="form.name" placeholder="请输入数据源名称" />
                      </el-form-item>
                    </el-col>

                    <el-col :span="12">
                      <el-form-item label="启用状态" prop="status">
                        <el-radio-group v-model="form.status">
                          <el-radio :label="1">启用</el-radio>
                          <el-radio :label="0">禁用</el-radio>
                        </el-radio-group>
                      </el-form-item>
                    </el-col>
                  </el-row>

                  <el-form-item label="驱动类名" prop="driverClassName">
                    <el-input
                      v-model="form.driverClassName"
                      placeholder="如：com.mysql.cj.jdbc.Driver"
                    />
                  </el-form-item>

                  <el-form-item label="连接地址" prop="url">
                    <el-input
                      v-model="form.url"
                      placeholder="如：jdbc:mysql://127.0.0.1:3306/demo"
                    />
                  </el-form-item>

                  <el-row :gutter="16">
                    <el-col :span="12">
                      <el-form-item label="用户名" prop="username">
                        <el-input v-model="form.username" placeholder="请输入用户名" />
                      </el-form-item>
                    </el-col>

                    <el-col :span="12">
                      <el-form-item label="密码" prop="password">
                        <el-input
                          v-model="form.password"
                          type="password"
                          show-password
                          placeholder="请输入密码"
                        />
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form>

                <template #footer>
                  <el-button @click="dialogVisible = false">取消</el-button>
                  <el-button type="primary" @click="submitForm">
                    {{ dialogMode === 'create' ? '确认新增' : '保存修改' }}
                  </el-button>
                </template>
              </el-dialog>
            </div>
          </template>

          <script setup>
          import { computed, onMounted, reactive, ref, watch } from 'vue'
          import { ElMessage, ElMessageBox } from 'element-plus'
          import { Search, Plus, Edit, RefreshRight, Delete } from '@element-plus/icons-vue'
          import {
            getDataSourceList,
            createDataSource,
            updateDataSource,
            deleteDataSource,
            testDataSourceConnect
          } from '@/api/dataSource'

          const loading = ref(false)
          const dialogVisible = ref(false)
          const dialogMode = ref('create')
          const formRef = ref()

          const query = reactive({
            keyword: '',
            type: '',
            connectivity: '',
            current: 1,
            size: 10
          })

          const list = ref([])
          const total = ref(null)

          const typeOptions = ['MySQL', 'PostgreSQL', 'Redis', 'MongoDB', 'DM', 'Other']

          const emptyForm = () => ({
            id: '',
            name: '',
            driverClassName: '',
            url: '',
            username: '',
            password: '',
            status: 1
          })

          const form = reactive(emptyForm())

          const rules = {
            name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
            driverClassName: [{ required: true, message: '请输入驱动类名', trigger: 'blur' }],
            url: [{ required: true, message: '请输入连接地址', trigger: 'blur' }]
          }

          const typeMetaMap = {
            MySQL: { short: 'MY', className: 'mysql' },
            PostgreSQL: { short: 'PG', className: 'postgresql' },
            Redis: { short: 'RD', className: 'redis' },
            MongoDB: { short: 'MG', className: 'mongodb' },
            DM: { short: 'DM', className: 'dm' },
            Other: { short: 'DB', className: 'other' }
          }

          const resolveDbType = (row) => {
            const text = `${row?.driverClassName || ''} ${row?.url || ''}`.toLowerCase()

            if (text.includes('mysql')) return 'MySQL'
            if (text.includes('postgresql') || text.includes('postgres')) return 'PostgreSQL'
            if (text.includes('redis')) return 'Redis'
            if (text.includes('mongodb') || text.includes('mongo')) return 'MongoDB'
            if (text.includes('dm')) return 'DM'

            return 'Other'
          }

          const getTypeMeta = (row) => {
            return typeMetaMap[resolveDbType(row)] || typeMetaMap.Other
          }

          const getConnectivityInfo = (row) => {
            if (row?.connectivity === 1) {
              return { label: '已连接', tagType: 'success', dotClass: 'is-success' }
            }

            if (row?.status === 1) {
              return { label: '异常', tagType: 'warning', dotClass: 'is-warning' }
            }

            return { label: '未连接', tagType: 'danger', dotClass: 'is-danger' }
          }

          const formatTime = (value) => {
            if (!value) return '--'
            return String(value).replace('T', ' ')
          }

          const filteredList = computed(() => {
            const keyword = query.keyword.trim().toLowerCase()

            return list.value.filter((item) => {
              const dbType = resolveDbType(item)
              const stateLabel = getConnectivityInfo(item).label
              const stateKey =
                stateLabel === '已连接'
                  ? 'connected'
                  : stateLabel === '异常'
                    ? 'abnormal'
                    : 'disconnected'

              const searchText = [
                item.name,
                dbType,
                item.url,
                item.driverClassName,
                item.username
              ]
                .filter(Boolean)
                .join(' ')
                .toLowerCase()

              const matchKeyword = !keyword || searchText.includes(keyword)
              const matchType = !query.type || dbType === query.type
              const matchConnectivity = !query.connectivity || query.connectivity === stateKey

              return matchKeyword && matchType && matchConnectivity
            })
          })

          // when backend provides pagination, `list` will be the current page records
          const pagedList = computed(() => list.value)

          const stats = computed(() => ({
            total: filteredList.value.length,
            connected: filteredList.value.filter((item) => item.connectivity === 1).length,
            abnormal: filteredList.value.filter((item) => item.connectivity !== 1 && item.status === 1).length,
            disconnected: filteredList.value.filter((item) => item.connectivity !== 1 && item.status === 0).length
          }))


          watch(
            () => [query.keyword, query.type, query.connectivity],
            () => {
              query.current = 1
              loadList()
            }
          )

          // ensure current page is valid if total changes
          watch(total, (val) => {
            const t = val || 0
            const maxPage = Math.max(1, Math.ceil(t / query.size))
            if (query.current > maxPage) query.current = maxPage
          })

          const handlePageChange = (page) => {
            query.current = page
            loadList()
          }

          const handleSizeChange = (size) => {
            query.size = size
            query.current = 1
            loadList()
          }

          const resetForm = () => {
            Object.assign(form, emptyForm())
          }

          const openCreateDialog = () => {
            dialogMode.value = 'create'
            resetForm()
            dialogVisible.value = true
          }

          const openEditDialog = (row) => {
            dialogMode.value = 'edit'
            Object.assign(form, emptyForm(), row)
            dialogVisible.value = true
          }

          const loadList = async () => {
            loading.value = true
            try {
              const params = {
                keyword: query.keyword || undefined,
                type: query.type || undefined,
                connectivity: query.connectivity || undefined,
                page: query.current,
                size: query.size
              }
              const res = await getDataSourceList(params)
              const result = res.data

              if (result?.code === 200) {
                const data = result.data
                if (data && Array.isArray(data.records)) {
                  list.value = data.records || []
                  let t = undefined
                  if (data.total !== undefined && data.total !== null) t = data.total
                  else if (data.totalCount !== undefined && data.totalCount !== null) t = data.totalCount
                  else if (data.totalElements !== undefined && data.totalElements !== null) t = data.totalElements
                  const tn = Number(t)
                  const reported = (t !== undefined && t !== null && Number.isFinite(tn)) ? tn : 0
                  total.value = Math.max(reported, list.value ? list.value.length : 0)
                } else if (Array.isArray(data)) {
                  // backend returned raw array
                  list.value = data
                  total.value = data.length
                } else if (data && Array.isArray(data.data)) {
                  list.value = data.data
                  total.value = (data.total !== undefined && data.total !== null) ? Number(data.total) : list.value.length
                } else {
                  list.value = []
                  total.value = 0
                }
              } else {
                ElMessage.error(result?.msg || '获取数据源列表失败')
              }
            } catch (error) {
              ElMessage.error(error?.response?.data?.msg || '获取数据源列表失败')
            } finally {
              loading.value = false
            }
          }

          const submitForm = async () => {
            if (!formRef.value) return

            const valid = await formRef.value.validate().catch(() => false)
            if (!valid) return

            try {
              const res =
                dialogMode.value === 'create'
                  ? await createDataSource({ ...form })
                  : await updateDataSource(form.id, { ...form })

              const result = res.data
              if (result?.code === 200) {
                ElMessage.success(result?.msg || '操作成功')
                dialogVisible.value = false
                await loadList()
              } else {
                ElMessage.error(result?.msg || '操作失败')
              }
            } catch (error) {
              ElMessage.error(error?.response?.data?.msg || '操作失败')
            }
          }

          const handleDelete = async (row) => {
            try {
              await ElMessageBox.confirm(`确认删除数据源“${row.name}”吗？`, '删除确认', {
                type: 'warning'
              })

              const res = await deleteDataSource(row.id)
              const result = res.data

              if (result?.code === 200) {
                ElMessage.success(result?.msg || '删除成功')
                await loadList()
              } else {
                ElMessage.error(result?.msg || '删除失败')
              }
            } catch (error) {
              if (error !== 'cancel' && error !== 'close') {
                ElMessage.error(error?.response?.data?.msg || '删除失败')
              }
            }
          }

          const handleTestConnect = async (row) => {
            try {
              const res = await testDataSourceConnect(row.id)
              const result = res.data

              if (result?.code === 200) {
                ElMessage.success(result?.msg || '连接测试成功')
              } else {
                ElMessage.error(result?.msg || '连接测试失败')
              }

              await loadList()
            } catch (error) {
              ElMessage.error(error?.response?.data?.msg || '连接测试失败')
            }
          }

          onMounted(() => {
            loadList()
          })

          const pageStart = computed(() => {
            const t = total.value
            if (t && t > 0) return (query.current - 1) * query.size + 1
            if (list.value && list.value.length > 0) return (query.current - 1) * query.size + 1
            return 0
          })

          const pageEnd = computed(() => {
            const t = total.value
            if (t && t > 0) return Math.min(query.current * query.size, t)
            if (list.value && list.value.length > 0) return pageStart.value + list.value.length - 1
            return 0
          })

          const displayTotal = computed(() => {
            if (total.value === null) return list.value ? list.value.length : '--'
            return total.value
          })
          </script>

          <style scoped lang="scss">
.data-source-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
  color: #303133;
}

.toolbar-card,
.toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar-card {
  flex-wrap: wrap;
}

.toolbar-search {
  width: 360px;
  min-width: 180px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  background: #fff;
  border: 1px solid #eef2f6;
  border-radius: 8px;
  padding: 14px;
}

.stat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.stat-label {
  color: #909399;
  font-size: 13px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
  color: #303133;
}

.stat-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
}

.stat-icon.is-primary {
  background: #ecf5ff;
  color: #409eff;
}

.stat-icon.is-success {
  background: #f0f9eb;
  color: #67c23a;
}

.stat-icon.is-warning {
  background: #fdf6ec;
  color: #e6a23c;
}

.stat-icon.is-danger {
  background: #fef0f0;
  color: #f56c6c;
}

.list-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #eef2f6;
  padding: 12px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 4px;
}

.list-header h2 {
  margin: 0;
  font-size: 16px;
}

.list-total,
.footer-text {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}

.list-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px 4px 12px;
}

.source-list {
  display: grid;
  gap: 12px;
}

.source-item {
  background: #fff;
  border: 1px solid #f0f3f7;
  border-radius: 8px;
  padding: 12px;
  transition: border-color 0.2s ease;
}

.source-item:hover {
  border-color: #d9ecff;
}

.source-item__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.source-basic {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.source-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}

.source-icon--mysql {
  background: #ecf5ff;
  color: #409eff;
}

.source-icon--postgresql {
  background: #f0f9eb;
  color: #67c23a;
}

.source-icon--redis {
  background: #fef0f0;
  color: #f56c6c;
}

.source-icon--mongodb {
  background: #fdf6ec;
  color: #e6a23c;
}

.source-icon--dm {
  background: #f4f4f5;
  color: #606266;
}

.source-icon--other {
  background: #f5f7fa;
  color: #303133;
}

.source-title-wrap {
  min-width: 0;
}

.source-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  word-break: break-all;
}

.source-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
  word-break: break-all;
}

.source-ops {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.op-buttons {
  display: flex;
  gap: 8px;
}

.source-meta {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #eef2f6;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  font-size: 13px;
  color: #606266;
}

.source-meta span {
  color: #909399;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.status-dot.is-success {
  background: #67c23a;
}

.status-dot.is-warning {
  background: #e6a23c;
}

.status-dot.is-danger {
  background: #f56c6c;
}

.list-footer {
  padding: 10px 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper) {
  border-radius: 8px;
}

:deep(.el-dialog) {
  border-radius: 8px;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .source-item__top {
    flex-direction: column;
  }

  .source-ops {
    width: 100%;
    justify-content: space-between;
  }

  .source-meta {
    grid-template-columns: 1fr;
  }
}
</style>
