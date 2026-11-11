<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { login as loginApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { setAdminName, setToken } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

// 表单校验：用户名、密码必填
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/**
 * 登录：POST /api/admin/employee/login
 */
async function handleLogin() {
  if (!formRef.value) {
    return
  }

  // 校验未通过则中断（validate 失败会 reject）
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    const data = await loginApi({
      username: form.username.trim(),
      password: form.password
    })

    // token 存 localStorage（key = admin_token），姓名存 localStorage（key = admin_name）
    setToken(data.token)
    if (data.name) {
      setAdminName(data.name)
    }
    // 同步 Pinia，顶部导航栏可立即显示姓名
    userStore.setAuth(data.token, data.name)

    ElMessage.success('登录成功')

    // 有 redirect 参数时优先回跳原页面，否则进入首页
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' && redirect ? redirect : '/dashboard')
  } catch (error) {
    // 失败提示由 utils/request.js 统一弹出（code=0 弹 msg、网络异常弹网络错误）
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
        <h2 class="login-title">宠享到家管理后台</h2>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable>
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

      <div class="login-footer">请使用管理员账号登录，忘记密码请联系系统管理员</div>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #e0f2ff 0%, #f5f7fa 55%, #eef6ff 100%);
}

.login-card {
  width: 400px;
  padding: 8px 12px 0;
  border-radius: 10px;
}

.login-header {
  margin-bottom: 20px;
  text-align: center;
}

.login-logo {
  width: 46px;
  height: 46px;
}

.login-title {
  margin: 10px 0 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.login-button {
  width: 100%;
}

.login-footer {
  margin-bottom: 12px;
  font-size: 12px;
  color: #a8abb2;
  text-align: center;
}
</style>
