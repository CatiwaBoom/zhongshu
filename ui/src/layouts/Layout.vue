<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside width="200px" class="sidebar">
      <el-menu
          default-active="/dashboard"
          class="sidebar-menu"
          router
      >
        <el-menu-item index="/dashboard">
          <el-icon><House /></el-icon>
          <template #title>首页</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="header">
        <el-button
            type="text"
            @click="logout"
        >
          退出登录
        </el-button>
      </el-header>
      <!-- 页面内容 -->
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { House } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// 退出登录
const logout = () => {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<style scoped lang="scss">
.admin-layout {
  height: 100vh;
  display: flex;

  .sidebar {
    background-color: #2e3b4e;
    color: #fff;

    .sidebar-menu {
      height: 100%;
      border-right: none;
    }
  }

  .header {
    text-align: right;
    font-size: 12px;
    line-height: 60px;
    border-bottom: 1px solid #e6e6e6;
  }

  .main-content {
    padding: 20px;
    background-color: #f5f5f5;
  }
}
</style>