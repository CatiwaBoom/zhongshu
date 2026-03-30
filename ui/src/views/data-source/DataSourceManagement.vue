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
                    <p>字段基于 DataSourceEntity / BaseEntity</p>
                  </div>
                  <div class="list-total">共 {{ filteredList.length }} 条数据</div>
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

                <div class="list-footer" v-if="filteredList.length">
                  <div class="footer-text">
                    显示 {{ pageStart }}-{{ pageEnd }} 条，共 {{ filteredList.length }} 条
                  </div>

                  <el-pagination
                    background
                    small
                    layout="prev, pager, next"
                    :current-page="query.current"
                    :page-size="query.size"
                    :total="filteredList.length"
                    @current-change="handlePageChange"
                  />
                </div>
              </section>

              <div class="page-footer">© 2026 数据源管理平台</div>

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
            size: 4
          })

          const list = ref([])

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

          const pagedList = computed(() => {
            const start = (query.current - 1) * query.size
            return filteredList.value.slice(start, start + query.size)
          })

          const stats = computed(() => ({
            total: filteredList.value.length,
            connected: filteredList.value.filter((item) => item.connectivity === 1).length,
            abnormal: filteredList.value.filter((item) => item.connectivity !== 1 && item.status === 1).length,
            disconnected: filteredList.value.filter((item) => item.connectivity !== 1 && item.status === 0).length
          }))

          const pageStart = computed(() => {
            return filteredList.value.length ? (query.current - 1) * query.size + 1 : 0
          })

          const pageEnd = computed(() => {
            return Math.min(query.current * query.size, filteredList.value.length)
          })

          watch(
            () => [query.keyword, query.type, query.connectivity],
            () => {
              query.current = 1
            }
          )

          watch(filteredList, (value) => {
            const maxPage = Math.max(1, Math.ceil(value.length / query.size))
            if (query.current > maxPage) {
              query.current = maxPage
            }
          })

          const handlePageChange = (page) => {
            query.current = page
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
              const res = await getDataSourceList()
              const result = res.data

              if (result?.code === 200 && Array.isArray(result.data)) {
                list.value = result.data
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
          </script>

          <style scoped lang="scss">
          .data-source-page {
            --primary: #409eff;
            --success: #67c23a;
            --warning: #e6a23c;
            --danger: #f56c6c;
            --text-main: #303133;
            --text-sub: #909399;
            --line: rgba(64, 158, 255, 0.14);
            --bg-card: linear-gradient(135deg, #ffffff 0%, #f6fbff 100%);
            --shadow: 0 12px 30px rgba(31, 45, 61, 0.08);

            color: var(--text-main);
          }

          .toolbar-card,
          .stat-card,
          .list-card,
          .source-item {
            background: var(--bg-card);
            border: 1px solid var(--line);
            border-radius: 18px;
            box-shadow: var(--shadow);
          }

          .toolbar-card {
            padding: 18px;
            margin-bottom: 18px;
          }

          .toolbar-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            flex-wrap: wrap;
          }

          .toolbar-search {
            flex: 1;
            min-width: 260px;
            max-width: 420px;
          }

          .toolbar-actions {
            display: flex;
            gap: 12px;
            flex-wrap: wrap;
          }

          .stats-grid {
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 16px;
            margin-bottom: 18px;
          }

          .stat-card {
            padding: 20px;
          }

          .stat-head {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 12px;
          }

          .stat-label {
            color: #909399;
            font-size: 14px;
          }

          .stat-value {
            font-size: 28px;
            font-weight: 700;
            margin-top: 8px;
            color: #1f2d3d;
          }

          .stat-tip {
            margin-top: 14px;
            font-size: 12px;
            color: var(--text-sub);
          }

          .stat-icon {
            width: 46px;
            height: 46px;
            border-radius: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
          }

          .stat-icon.is-primary {
            background: rgba(64, 158, 255, 0.14);
            color: var(--primary);
          }

          .stat-icon.is-success {
            background: rgba(103, 194, 58, 0.14);
            color: var(--success);
          }

          .stat-icon.is-warning {
            background: rgba(230, 162, 60, 0.14);
            color: var(--warning);
          }

          .stat-icon.is-danger {
            background: rgba(245, 108, 108, 0.14);
            color: var(--danger);
          }

          .list-card {
            overflow: hidden;
          }

          .list-header {
            padding: 18px 20px;
            border-bottom: 1px solid var(--line);
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            flex-wrap: wrap;
          }

          .list-header h2 {
            margin: 0;
            font-size: 18px;
          }

          .list-header p,
          .list-total,
          .footer-text,
          .page-footer {
            margin: 4px 0 0;
            color: var(--text-sub);
            font-size: 13px;
          }

          .list-body {
            min-height: 240px;
          }

          .source-list {
            display: grid;
            gap: 16px;
            padding: 18px;
          }

          .source-item {
            padding: 18px;
            transition: all 0.2s ease;
          }

          .source-item:hover {
            transform: translateY(-2px);
            border-color: rgba(64, 158, 255, 0.28);
          }

          .source-item__top {
            display: flex;
            justify-content: space-between;
            gap: 16px;
            align-items: flex-start;
          }

          .source-basic {
            display: flex;
            gap: 14px;
            align-items: center;
            min-width: 0;
          }

          .source-icon {
            width: 44px;
            height: 44px;
            border-radius: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            flex-shrink: 0;
          }

          .source-icon--mysql {
            background: rgba(64, 158, 255, 0.14);
            color: #409eff;
          }

          .source-icon--postgresql {
            background: rgba(103, 194, 58, 0.14);
            color: #67c23a;
          }

          .source-icon--redis {
            background: rgba(245, 108, 108, 0.14);
            color: #f56c6c;
          }

          .source-icon--mongodb {
            background: rgba(230, 162, 60, 0.14);
            color: #e6a23c;
          }

          .source-icon--dm {
            background: rgba(144, 147, 153, 0.16);
            color: #606266;
          }

          .source-icon--other {
            background: rgba(64, 158, 255, 0.08);
            color: #1f2d3d;
          }

          .source-title-wrap {
            min-width: 0;
          }

          .source-title {
            font-size: 17px;
            font-weight: 600;
            color: #1f2d3d;
            word-break: break-all;
          }

          .source-subtitle {
            margin-top: 6px;
            font-size: 13px;
            color: var(--text-sub);
            word-break: break-all;
          }

          .source-ops {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;
            justify-content: flex-end;
          }

          .op-buttons {
            display: flex;
            gap: 8px;
          }

          .source-meta {
            margin-top: 16px;
            padding-top: 14px;
            border-top: 1px dashed rgba(64, 158, 255, 0.18);
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 10px 16px;
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
            background: var(--success);
          }

          .status-dot.is-warning {
            background: var(--warning);
          }

          .status-dot.is-danger {
            background: var(--danger);
          }

          .list-footer {
            padding: 16px 20px;
            border-top: 1px solid var(--line);
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            flex-wrap: wrap;
          }

          .page-footer {
            text-align: center;
            padding: 18px 0 6px;
          }

          :deep(.el-input__wrapper),
          :deep(.el-select__wrapper) {
            border-radius: 12px;
            box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.14) inset !important;
          }

          :deep(.el-dialog) {
            border-radius: 18px;
            overflow: hidden;
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