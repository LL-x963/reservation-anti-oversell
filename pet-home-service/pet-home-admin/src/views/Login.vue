<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' }
  ]
}

/**
 * 登录：调用 POST /api/admin/login，成功后写入 token 并跳转
 */
async function handleLogin() {
  if (!formRef.value) {
    return
  }

  try {
    await formRef.value.validate()
  } catch {
    // 表单校验未通过
    return
  }

  loading.value = true
  try {
    await userStore.login({ username: form.username.trim(), password: form.password })
    ElMessage.success('登录成功')

    // 预取管理员信息，进入布局后顶部即可显示姓名
    userStore.loadProfile().catch(() => {})

    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect ? redirect : '/')
  } catch (error) {
    // 具体提示已由 request 拦截器统一弹出，这里只需结束 loading
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="login-header">
        <img src="/favicon.svg" alt="logo" class="login-logo" />
        <div class="login-title">宠享到家管理后台</div>
        <div class="login-subtitle">宠物上门服务平台 · 运营管理系统</div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="请输入管理员账号" clearable>
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            clearable
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="login-button" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="默认账号：admin / 123456"
        description="需先启动后端 java-backend（http://localhost:8080）"
      />
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #e0f2ff 0%, #f5f7fa 60%, #eef6ff 100%);
}

.login-card {
  width: 400px;
  padding: 8px 12px 0;
  border-radius: 10px;
}

.login-header {
  text-align: center;
  margin-bottom: 18px;
}

.login-logo {
  width: 46px;
  height: 46px;
}

.login-title {
  margin-top: 8px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.login-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.login-button {
  width: 100%;
}
</style>
