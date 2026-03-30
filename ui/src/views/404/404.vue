vue
<template>
  <div class="not-found">
    <el-result
        icon="warning"
        title="404"
        sub-title="抱歉，页面不存在或已被移除"
    >
      <template #extra>
        <el-button type="primary" @click="goDashboard">返回首页</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goDashboard = () => {
  router.push('/dashboard')
}

const goBack = () => {
  router.back()
}
</script>

<style scoped lang="scss">
.not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  padding: 24px;
}
</style>
