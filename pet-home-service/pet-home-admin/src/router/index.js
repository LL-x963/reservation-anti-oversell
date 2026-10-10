import { createRouter, createWebHashHistory } from 'vue-router'

import Layout from '@/layout/Layout.vue'
import { getToken } from '@/utils/auth'

/**
 * 侧边栏菜单路由
 *
 * 作为单一数据源：Layout.vue 直接遍历该数组渲染菜单（meta.title / meta.icon），
 * 新增页面只要在这里加一项即可。
 */
export const menuRoutes = [
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '首页', icon: 'HomeFilled' }
  },
  {
    path: '/category',
    name: 'Category',
    component: () => import('@/views/Category.vue'),
    meta: { title: '服务分类', icon: 'Menu' }
  },
  {
    path: '/service',
    name: 'Service',
    component: () => import('@/views/Service.vue'),
    meta: { title: '服务项目', icon: 'Goods' }
  },
  {
    path: '/beautician',
    name: 'Beautician',
    component: () => import('@/views/Beautician.vue'),
    meta: { title: '美容师管理', icon: 'UserFilled' }
  },
  {
    path: '/schedule',
    name: 'Schedule',
    component: () => import('@/views/Schedule.vue'),
    meta: { title: '排班管理', icon: 'Calendar' }
  },
  {
    path: '/order',
    name: 'Order',
    component: () => import('@/views/Order.vue'),
    meta: { title: '订单管理', icon: 'List' }
  },
  {
    path: '/stat',
    name: 'Stat',
    component: () => import('@/views/Stat.vue'),
    meta: { title: '数据统计', icon: 'DataLine' }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/Knowledge.vue'),
    meta: { title: '知识库管理', icon: 'Reading' }
  }
]

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: menuRoutes
  },
  {
    // 兜底：未匹配到的地址回到首页
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  // hash 模式：部署时不需要额外配置服务端 rewrite
  history: createWebHashHistory(),
  routes
})

/**
 * 全局前置守卫：未登录且访问的不是登录页 -> 跳转登录页（并记录回跳地址）
 */
router.beforeEach((to) => {
  const loggedIn = Boolean(getToken())

  if (!loggedIn && !to.meta.public) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 已登录时访问登录页，直接回首页
  if (loggedIn && to.path === '/login') {
    return { path: '/' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} - 宠享到家管理后台` : '宠享到家管理后台'
})

export default router
