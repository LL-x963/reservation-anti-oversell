<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ChatDialog from '@/components/ChatDialog.vue'

/** 底部导航配置：首页、服务、预约车、订单、我的 */
const TABS = [
  { path: '/home', title: '首页', icon: 'HomeFilled' },
  { path: '/service', title: '服务', icon: 'Grid' },
  { path: '/cart', title: '预约车', icon: 'ShoppingCart' },
  { path: '/order', title: '订单', icon: 'Tickets' },
  { path: '/mine', title: '我的', icon: 'User' }
]

const route = useRoute()
const router = useRouter()

/** 顶部标题：优先取路由 meta.title */
const title = computed(() => route.meta.title || '宠享到家')

/** 是否显示底部导航（只有 5 个主 Tab 页显示） */
const showTabBar = computed(() => Boolean(route.meta.tab))

/** 当前激活的 Tab */
const activePath = computed(() => route.path)

/** 非 Tab 页显示返回按钮 */
const showBack = computed(() => !showTabBar.value)

function switchTab(path) {
  if (route.path !== path) {
    router.push(path)
  }
}

function goBack() {
  // 直接进入详情页时没有上一页，兜底回首页
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/home')
  }
}
</script>

<template>
  <div class="layout">
    <!-- 顶部标题栏：padding-top 吃下状态栏安全区域 -->
    <header class="layout__navbar">
      <div class="layout__navbar-side" @click="showBack && goBack()">
        <el-icon v-if="showBack" class="layout__back h5-tap"><ArrowLeft /></el-icon>
      </div>
      <h1 class="layout__title">{{ title }}</h1>
      <div class="layout__navbar-side"></div>
    </header>

    <!-- 内容区：底部预留 TabBar 与安全区域高度，避免内容被遮挡 -->
    <main class="layout__body" :class="{ 'layout__body--with-tabbar': showTabBar }">
      <router-view />
    </main>

    <!-- 底部导航 -->
    <nav v-if="showTabBar" class="layout__tabbar">
      <div
        v-for="tab in TABS"
        :key="tab.path"
        class="layout__tab h5-tap"
        :class="{ 'is-active': activePath === tab.path }"
        @click="switchTab(tab.path)"
      >
        <el-icon :size="21"><component :is="tab.icon" /></el-icon>
        <span class="layout__tab-text">{{ tab.title }}</span>
      </div>
    </nav>

    <!-- 全局悬浮 AI 问答组件（养宠百科） -->
    <ChatDialog />
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: #f5f6f8;
}

/* ---------------- 顶部标题栏 ---------------- */
.layout__navbar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  height: calc(var(--h5-navbar-height) + var(--safe-area-top));
  padding: var(--safe-area-top) var(--h5-page-padding) 0;
  background-color: #fff;
  border-bottom: 1px solid #f0f0f0;
}

.layout__navbar-side {
  display: flex;
  align-items: center;
  width: 32px;
}

.layout__back {
  font-size: 20px;
  color: #303133;
}

.layout__title {
  flex: 1;
  overflow: hidden;
  font-size: 17px;
  font-weight: 600;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------------- 内容区 ---------------- */
.layout__body {
  flex: 1;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.layout__body--with-tabbar {
  padding-bottom: calc(var(--h5-tabbar-height) + var(--safe-area-bottom));
}

/* ---------------- 底部导航 ---------------- */
.layout__tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 100;
  display: flex;
  height: calc(var(--h5-tabbar-height) + var(--safe-area-bottom));
  padding-bottom: var(--safe-area-bottom);
  background-color: #fff;
  border-top: 1px solid #f0f0f0;
}

.layout__tab {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 11px;
  transition: color 0.2s;
}

.layout__tab.is-active {
  color: var(--el-color-primary);
}

.layout__tab-text {
  margin-top: 2px;
}
</style>
