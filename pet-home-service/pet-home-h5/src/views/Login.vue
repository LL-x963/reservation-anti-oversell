<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { login, register } from '@/api/user'
import { setToken, setUserName } from '@/utils/auth'

const route = useRoute()
const router = useRouter()

const formRef = ref()
const loading = ref(false)
/** 当前模式：login 登录 / register 注册 */
const mode = ref('login')

const form = reactive({
  phone: '',
  password: '',
  name: ''
})

/** 注册密码强度校验：8~32 位且同时包含字母和数字（与后端 PasswordPolicy 保持一致） */
function validateRegisterPassword(rule, value, callback) {
  if (!value) {
    callback(new Error('请输入密码'))
    return
  }
  if (value.length < 8 || value.length > 32) {
    callback(new Error('密码长度需为 8~32 位'))
    return
  }
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    callback(new Error('密码需同时包含字母和数字'))
    return
  }
  callback()
}

/** 校验规则随模式切换 */
const rules = computed(() => ({
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password:
    mode.value === 'login'
      ? [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, max: 32, message: '密码长度需为 6~32 位', trigger: 'blur' }
        ]
      : [{ required: true, validator: validateRegisterPassword, trigger: 'blur' }],
  name: [{ max: 64, message: '昵称长度不能超过 64', trigger: 'blur' }]
}))

function switchMode(target) {
  if (mode.value === target) {
    return
  }
  mode.value = target
  form.password = ''
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    // 校验未通过，错误提示由表单展示
    return
  }

  loading.value = true
  try {
    if (mode.value === 'register') {
      await register({
        phone: form.phone.trim(),
        password: form.password,
        name: form.name.trim() || undefined
      })
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
      form.password = ''
      form.name = ''
      formRef.value?.clearValidate()
      return
    }

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

    <!-- 登录 / 注册切换 -->
    <div class="login__tabs">
      <span
        class="login__tab h5-tap"
        :class="{ 'is-active': mode === 'login' }"
        @click="switchMode('login')"
      >
        登录
      </span>
      <span
        class="login__tab h5-tap"
        :class="{ 'is-active': mode === 'register' }"
        @click="switchMode('register')"
      >
        注册
      </span>
    </div>

    <!-- 表单 -->
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

      <el-form-item v-if="mode === 'register'" prop="name">
        <el-input
          v-model="form.name"
          size="large"
          maxlength="64"
          placeholder="昵称（选填）"
          clearable
        >
          <template #prefix>
            <el-icon><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          size="large"
          :maxlength="mode === 'login' ? 32 : 32"
          show-password
          :placeholder="mode === 'login' ? '请输入密码' : '请设置密码（8~32 位，含字母和数字）'"
          @keyup.enter="handleSubmit"
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
        @click="handleSubmit"
      >
        {{ mode === 'login' ? '登录' : '注册' }}
      </el-button>
    </el-form>

    <div class="login__footer">
      <p v-if="mode === 'login'" class="login__tip">
        还没有账号？<span class="login__link h5-tap" @click="switchMode('register')">立即注册</span>
      </p>
      <p v-else class="login__tip">
        已有账号？<span class="login__link h5-tap" @click="switchMode('login')">返回登录</span>
      </p>
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

/* ---------------- 登录 / 注册切换 ---------------- */
.login__tabs {
  display: flex;
  justify-content: center;
  gap: 36px;
  margin-top: 32px;
}

.login__tab {
  position: relative;
  padding-bottom: 6px;
  font-size: 16px;
  color: #909399;
}

.login__tab.is-active {
  font-weight: 600;
  color: #303133;
}

.login__tab.is-active::after {
  position: absolute;
  right: 8px;
  bottom: 0;
  left: 8px;
  height: 3px;
  content: '';
  background-color: #409eff;
  border-radius: 2px;
}

/* ---------------- 表单 ---------------- */
.login__form {
  margin-top: 24px;
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

/* ---------------- 底部提示 ---------------- */
.login__footer {
  margin-top: auto;
  padding-top: 28px;
  text-align: center;
}

.login__tip {
  font-size: 13px;
  color: #909399;
}

.login__link {
  color: #409eff;
}
</style>
