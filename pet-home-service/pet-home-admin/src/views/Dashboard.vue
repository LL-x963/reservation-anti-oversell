<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

import { menuRoutes } from '@/router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/** 功能导航：排除首页自身 */
const shortcuts = computed(() => menuRoutes.filter((item) => item.path !== '/dashboard'))

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const today = computed(() =>
  new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })
)

onMounted(() => {
  userStore.loadProfile().catch(() => {})
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="welcome">
        <el-avatar :size="56" :src="userStore.adminAvatar">
          <el-icon :size="28"><UserFilled /></el-icon>
        </el-avatar>
        <div>
          <div class="welcome-title">{{ greeting }}，{{ userStore.adminName }}</div>
          <div class="welcome-subtitle">{{ today }} · 欢迎使用宠享到家管理后台</div>
        </div>
      </div>
    </el-card>

    <el-alert
      class="tip"
      type="success"
      :closable="false"
      show-icon
      title="当前为项目骨架：登录闭环、路由守卫、请求封装、布局菜单均已可用"
      description="除首页外的业务页面为占位页，对应后端接口已全部就绪，可按模块逐个实现。"
    />

    <el-card shadow="never">
      <template #header>
        <span class="block-title">功能导航</span>
      </template>

      <el-row :gutter="16">
        <el-col v-for="item in shortcuts" :key="item.path" :xs="12" :sm="8" :md="6">
          <div class="shortcut" @click="router.push(item.path)">
            <el-icon :size="26" class="shortcut-icon">
              <component :is="item.meta.icon" />
            </el-icon>
            <span class="shortcut-title">{{ item.meta.title }}</span>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.welcome {
  display: flex;
  align-items: center;
  gap: 16px;
}

.welcome-title {
  font-size: 17px;
  font-weight: 600;
}

.welcome-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.tip {
  margin: 16px 0;
}

.block-title {
  font-size: 15px;
  font-weight: 600;
}

.shortcut {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 18px 0;
  margin-bottom: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.shortcut:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.shortcut-icon {
  color: #409eff;
}

.shortcut-title {
  font-size: 14px;
  color: #303133;
}
</style>
