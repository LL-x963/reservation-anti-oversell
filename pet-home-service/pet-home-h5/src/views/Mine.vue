<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

import { getProfile, logout } from '@/api/user'
import { clearAuth, getUserName, setUserName } from '@/utils/auth'

/**
 * 个人中心
 *
 * <p>已接通的接口：GET /api/user/profile（用户信息）、POST /api/user/logout（退出登录）。</p>
 */
const router = useRouter()

/** 用户名：优先取本地缓存（登录接口返回的 name），进页面后再用 profile 刷新一次 */
const userName = ref(getUserName())
const phone = ref('')

const menus = [
  { key: 'order', title: '我的订单', icon: 'Tickets', path: '/order' },
  { key: 'pet', title: '宠物档案', icon: 'Document', path: '/pet' },
  { key: 'cart', title: '预约车', icon: 'ShoppingCart', path: '/cart' }
]

onMounted(loadProfile)

async function loadProfile() {
  try {
    const data = await getProfile()
    userName.value = data?.name || userName.value
    phone.value = data?.phone || ''
    setUserName(data?.name)
  } catch (e) {
    // 错误提示已由 request 拦截器统一弹出
  }
}

function handleMenu(item) {
  router.push(item.path)
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  try {
    await logout()
  } catch (e) {
    // 即使后端吊销失败（如 token 已过期），本地也要清理登录态
  }
  clearAuth()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="mine h5-page">
    <!-- 用户信息卡 -->
    <div class="mine__profile">
      <div class="mine__avatar">
        <el-icon :size="26"><UserFilled /></el-icon>
      </div>
      <div class="mine__info">
        <p class="mine__name">{{ userName || '宠物家长' }}</p>
        <p class="mine__sub">{{ phone || '未绑定手机号' }}</p>
      </div>
    </div>

    <!-- 功能菜单 -->
    <div class="h5-card mine__menus">
      <div v-for="item in menus" :key="item.key" class="h5-row h5-tap" @click="handleMenu(item)">
        <span class="mine__menu-title">
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          {{ item.title }}
        </span>
        <el-icon color="#c0c4cc"><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- 退出登录 -->
    <el-button class="mine__logout" size="large" @click="handleLogout">退出登录</el-button>
  </div>
</template>

<style scoped>
.mine__profile {
  display: flex;
  align-items: center;
  padding: 18px 16px;
  margin-bottom: 12px;
  background: linear-gradient(135deg, #409eff, #79bbff);
  border-radius: 10px;
}

.mine__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  margin-right: 12px;
  color: #409eff;
  background-color: #fff;
  border-radius: 50%;
}

.mine__info {
  color: #fff;
}

.mine__name {
  font-size: 17px;
  font-weight: 600;
}

.mine__sub {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.9;
}

.mine__menus {
  padding: 0 12px;
}

.mine__menu-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mine__logout {
  width: 100%;
  height: 46px;
  margin-top: 12px;
}
</style>
