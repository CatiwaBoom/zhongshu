<template>
  <div class="admin-layout">
    <el-aside width="220px" class="sidebar">
      <el-menu
        :default-active="route.path"
        class="sidebar-menu"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><House /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <el-menu-item index="/datasource">
          <el-icon><Connection /></el-icon>
          <template #title>数据源管理</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <el-button type="text" @click="logout">退出登录</el-button>
      </el-header>

      <el-main class="main-content">
        <div class="content-panel">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { House, Connection } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const logout = () => {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<style scoped lang="scss">
.admin-layout {
  height: 100vh;
  display: flex;
  background: #f5f7fa;
  overflow: hidden;

  .sidebar {
    background-color: #fff;
    color: #fff;
    border-right: 1px solid rgba(255, 255, 255, 0.08);
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);

    .sidebar-menu {
      height: 100%;
      border-right: none;
      background-color: transparent;
    }
  }

  .header {
    height: 56px;
    padding: 0 20px;
    background: #fff;
    border-bottom: 1px solid #e8edf3;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.04);

    display: flex;
    align-items: center;
    justify-content: flex-end;
  }

  .main-content {
    padding: 16px;
    background-color: #f5f7fa;
    min-height: 0;
  }

  .content-panel {
    height: 100%;
    min-height: calc(100vh - 92px);
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 12px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    padding: 20px;
    box-sizing: border-box;
    overflow: auto;
  }
}
</style>