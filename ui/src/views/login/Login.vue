<template>
  <div class="login-page">
    <div class="logo">
      <span>企业数据中台</span>
    </div>

    <el-card class="login-card" shadow="never">
      <div class="title">登录您的账号</div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username" class="form-item">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password" class="form-item">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            clearable
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button
          type="success"
          class="btn"
          size="large"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <div class="tip">内部系统 · 仅限授权人员访问</div>
    </el-card>

    <div class="footer">
      © 2026 DataCenter · <a href="javascript:void(0)">隐私政策</a> ·
      <a href="javascript:void(0)">服务协议</a>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '123456'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
    try {
      // Use /auth/login which returns accessToken + refresh info
      const res = await request.post('/auth/login', {
        username: form.username,
        password: form.password,
        deviceId: 'web'
      })

      const data = res.data
      // AuthController returns LoginResponse (not wrapped), but when proxied it may be inside data
      const accessToken = data?.accessToken || data?.data?.accessToken
      if (accessToken) {
        localStorage.setItem('token', accessToken)
        localStorage.setItem('username', form.username)
        ElMessage.success('登录成功')
        router.push('/dashboard')
      } else {
        // fallback: handle older /user/login wrapped response
        const wrapped = data
        if (wrapped && (wrapped.code === 200 || wrapped.code === 0)) {
          const token = wrapped.data?.token || wrapped.data?.accessToken
          if (token) {
            localStorage.setItem('token', token)
            if (wrapped.data?.username) localStorage.setItem('username', wrapped.data.username)
            ElMessage.success('登录成功')
            router.push('/dashboard')
            return
          }
        }
        ElMessage.error('登录失败：未返回访问令牌')
      }
    } catch (error) {
      ElMessage.error(error?.response?.data?.message || error?.response?.data?.msg || '登录请求失败')
    } finally {
      loading.value = false
    }
}
</script>

<style scoped lang="scss">
* {
  box-sizing: border-box;
}

.login-page {
  --bg: #ffffff;
  --card: #f6f8fa;
  --border: #d1d9e0;
  --text: #24292f;
  --link: #0969da;
  --btn: #2da44e;
  --btn-hover: #2c974b;
  --shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  --muted: #656d76;

  min-height: 100vh;
  padding: 20px;
  background: var(--bg);
  color: var(--text);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

@media (prefers-color-scheme: dark) {
  .login-page {
    --bg: #1a1d23;
    --card: #24292f;
    --border: #393f48;
    --text: #f0f6fc;
    --link: #4493f8;
    --btn: #238636;
    --btn-hover: #2ea043;
    --shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
    --muted: #8b949e;
  }
}

.logo {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 24px;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 10px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  background-color: var(--card) !important;
  border: 1px solid var(--border) !important;
  border-radius: 12px !important;
  box-shadow: var(--shadow) !important;
  overflow: hidden;

  :deep(.el-card__body) {
    padding: 24px;
    background-color: var(--card);
  }
}

.title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 20px;
  color: var(--text);
}

.form-item {
  margin-bottom: 16px;
}

.btn {
  width: 100%;
  margin-top: 8px;
  border: none;
  background: var(--btn) !important;

  &:hover {
    background: var(--btn-hover) !important;
  }
}

.tip {
  text-align: center;
  font-size: 13px;
  color: var(--muted);
  margin-top: 16px;
}

.footer {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: var(--muted);

  a {
    color: var(--link);
    text-decoration: none;
  }
}

@media (max-width: 480px) {
  .login-card {
    :deep(.el-card__body) {
      padding: 20px;
    }
  }
}
</style>