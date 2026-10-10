<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { menuRoutes } from '@/router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 侧边栏折叠状态 */
const collapsed = ref(false)

/** 当前激活菜单：直接用当前路由路径 */
const activeMenu = computed(() => route.path)

/** 顶部显示的当前页面标题 */
const currentTitle = computed(() => route.meta?.title || '')

onMounted(() => {
  // 顶部要展示管理员姓名；失败（如 token 失效）由 request 拦截器统一处理，这里不阻断页面渲染
  userStore.loadProfile().catch(() => {})
})

/**
 * 退出登录
 */
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出登录',
      cancelButtonText: '取消'
    })
  } catch {
    // 用户取消
    return
  }

  await userStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

/**
 * 右上角下拉菜单命令
 */
function handleCommand(command) {
  if (command === 'logout') {
    handleLogout()
  }
}
</script>

<template>
  <el-container class="layout">
    <!-- 左侧侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '210px'" class="layout-aside">
      <div class="layout-logo">
        <img src="/favicon.svg" alt="logo" class="layout-logo-icon" />
        <span v-show="!collapsed" class="layout-logo-text">宠享到家</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="layout-menu"
      >
        <el-menu-item v-for="item in menuRoutes" :key="item.path" :index="item.path">
          <el-icon>
            <component :is="item.meta.icon" />
          </el-icon>
          <template #title>{{ item.meta.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部导航栏 -->
      <el-header class="layout-header">
        <div class="layout-header-left">
          <el-icon class="layout-collapse-icon" @click="collapsed = !collapsed">
            <component :is="collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <span class="layout-title">{{ currentTitle }}</span>
        </div>

        <div class="layout-header-right">
          <el-dropdown @command="handleCommand">
            <span class="layout-user">
              <el-avatar :size="30" :src="userStore.adminAvatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span class="layout-user-name">{{ userStore.adminName }}</span>
              <el-icon class="layout-user-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  账号：{{ userStore.profile?.username || '-' }}
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
  transition: width 0.2s;
  overflow-x: hidden;
}

.layout-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 60px;
  padding: 0 16px;
  border-bottom: 1px solid #e4e7ed;
}

.layout-logo-icon {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
}

.layout-logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.layout-menu {
  border-right: none;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 16px;
  background-color: #ffffff;
  border-bottom: 1px solid #e4e7ed;
}

.layout-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout-collapse-icon {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
}

.layout-collapse-icon:hover {
  color: #409eff;
}

.layout-title {
  font-size: 15px;
  font-weight: 500;
}

.layout-header-right {
  display: flex;
  align-items: center;
}

.layout-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.layout-user-name {
  font-size: 14px;
  color: #303133;
}

.layout-user-arrow {
  color: #909399;
}

.layout-main {
  padding: 16px;
  overflow-y: auto;
  background-color: #f5f7fa;
}

/* 路由切换动画 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.2s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-8px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(8px);
}
</style>
