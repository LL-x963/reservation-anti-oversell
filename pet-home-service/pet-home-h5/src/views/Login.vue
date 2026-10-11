<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { login } from '@/api/user'
import { setToken, setUserName } from '@/utils/auth'

const route = useRoute()
const router = useRouter()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  phone: '',
  password: ''
})

/** 演示账号（后端 pet_home.sql 初始化的种子用户，点击提示可直接填入） */
const DEMO = {
  phone: '13800138000',
  password: '123456'
}

/** 表单校验：手机号格式 + 密码必填（与后端 UserLoginRequest 的校验规则保持一致） */
const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6~20 位', trigger: 'blur' }
  ]
}

/** 一键填入演示账号 */
function fillDemo() {
  form.phone = DEMO.phone
  form.password = DEMO.password
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch (e) {
    // 校验未通过，错误提示由表单展示
    return
  }

  loading.value = true
  try {
    const data = await login(form.phone.trim(), form.password)
    // 按约定存储 token 与用户名
    setToken(data.token)
    setUserName(data.name)
    ElMessage.success('登录成功')
    // 默认进首页；若被路由守卫拦截过则回到原页面
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.replace(redirect)
  } catch (e) {
    // 错误提示已由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <!-- 顶部 logo 区 -->
    <div class="login__header">
      <div class="login__logo">
        <el-icon :size="40"><HomeFilled /></el-icon>
      </div>
      <h1 class="login__title">宠享到家</h1>
      <p class="login__subtitle">专业宠物上门美容洗护</p>
    </div>

    <!-- 登录表单 -->
    <el-form ref="formRef" :model="form" :rules="rules" class="login__form" @submit.prevent>
      <el-form-item prop="phone">
        <el-input
          v-model="form.phone"
          type="tel"
          size="large"
          maxlength="11"
          placeholder="请输入手机号"
          clearable
        >
          <template #prefix>
            <el-icon><Iphone /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          size="large"
          maxlength="20"
          show-password
          placeholder="请输入密码"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-button
        class="login__button"
        type="primary"
        size="large"
        :loading="loading"
        @click="handleLogin"
      >
        登录
      </el-button>
    </el-form>

    <!-- 演示账号提示 -->
    <div class="login__demo h5-tap" @click="fillDemo">
      <p class="login__demo-account">演示账号：{{ DEMO.phone }} / {{ DEMO.password }}</p>
      <p class="login__demo-tip">点击自动填入</p>
    </div>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: calc(var(--safe-area-top) + 48px) 24px calc(var(--safe-area-bottom) + 24px);
  background: linear-gradient(180deg, #eaf4ff 0%, #f5f6f8 55%);
}

/* ---------------- 顶部 logo 区 ---------------- */
.login__header {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.login__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #79bbff);
  border-radius: 20px;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.35);
}

.login__title {
  margin-top: 14px;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 2px;
  color: #303133;
}

.login__subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

/* ---------------- 登录表单 ---------------- */
.login__form {
  margin-top: 40px;
}

/* 移动端点击区域：输入框与按钮统一为 48px 高度 */
.login__form :deep(.el-input__wrapper) {
  padding: 4px 12px;
  border-radius: 10px;
}

.login__form :deep(.el-input__inner) {
  height: 40px;
  font-size: 16px;
}

.login__form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login__button {
  width: 100%;
  height: 48px;
  margin-top: 6px;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 4px;
  border-radius: 24px;
}

/* ---------------- 演示账号 ---------------- */
.login__demo {
  margin-top: auto;
  padding-top: 28px;
  text-align: center;
}

.login__demo-account {
  display: inline-block;
  padding: 6px 14px;
  font-size: 13px;
  color: #409eff;
  background-color: rgba(64, 158, 255, 0.1);
  border-radius: 16px;
}

.login__demo-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
