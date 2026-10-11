import { createRouter, createWebHashHistory } from 'vue-router'

import { getToken } from '@/utils/auth'

/**
 * 底部 TabBar 对应的路由（Layout 中据此判断是否显示返回按钮）
 */
export const TAB_ROUTES = ['/home', '/service', '/cart', '/order', '/mine']

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '宠享到家', tab: true }
      },
      {
        path: 'service',
        name: 'Service',
        component: () => import('@/views/Service.vue'),
        meta: { title: '服务列表', tab: true }
      },
      {
        path: 'cart',
        name: 'Cart',
        component: () => import('@/views/Cart.vue'),
        meta: { title: '预约车', tab: true }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/Order.vue'),
        meta: { title: '我的订单', tab: true }
      },
      {
        path: 'mine',
        name: 'Mine',
        component: () => import('@/views/Mine.vue'),
        meta: { title: '个人中心', tab: true }
      }
    ]
  },
  // 以下页面不带底部导航，但仍走 Layout（顶部有返回按钮）
  {
    path: '/service/:id',
    name: 'ServiceDetail',
    component: () => import('@/views/ServiceDetail.vue'),
    meta: { title: '服务详情' }
  },
  {
    path: '/submit-order',
    name: 'SubmitOrder',
    component: () => import('@/views/SubmitOrder.vue'),
    meta: { title: '提交预约' }
  },
  {
    path: '/pet',
    name: 'Pet',
    component: () => import('@/views/Pet.vue'),
    meta: { title: '宠物档案' }
  },
  // 兜底：未知路径回首页
  {
    path: '/:pathMatch(.*)*',
    redirect: '/home'
  }
]

const router = createRouter({
  // H5 使用 hash 模式：无需服务端配置 history 回退，静态托管即可直接打开
  history: createWebHashHistory(),
  scrollBehavior: () => ({ left: 0, top: 0 }),
  routes
})

// ---------------- 全局前置守卫：未登录跳登录页 ----------------
router.beforeEach((to) => {
  // 登录页等公开页面直接放行
  if (to.meta.public) {
    return true
  }

  if (!getToken()) {
    // 带上回跳地址，登录成功后回到原页面
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }

  return true
})

// 切换路由时同步浏览器标题（H5 顶部标题栏亦读取 meta.title）
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 宠享到家` : '宠享到家'
})

export default router
