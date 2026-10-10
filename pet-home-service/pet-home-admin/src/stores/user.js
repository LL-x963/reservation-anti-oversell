import { defineStore } from 'pinia'

import { getProfile, login as loginApi, logout as logoutApi } from '@/api/auth'
import { clearAuth, getAdminName, getToken, setAdminName, setToken } from '@/utils/auth'

/**
 * 登录态 store：token + 姓名 + 管理员完整信息
 *
 * 本地存储由 utils/auth.js 统一读写（admin_token / admin_name），
 * 保证刷新页面后登录态与顶部导航栏显示都不丢。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    /** 登录 token（初始化时从 localStorage 恢复） */
    token: getToken(),
    /** 登录人姓名（优先来自登录响应） */
    name: getAdminName(),
    /** /api/admin/profile 返回的完整信息 */
    profile: null
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    /** 顶部展示用：profile.name -> 登录响应 name -> 账号 -> 兜底 */
    adminName: (state) => state.profile?.name || state.name || state.profile?.username || '管理员',
    adminAvatar: (state) => state.profile?.avatar || ''
  },

  actions: {
    /**
     * 账号密码登录（POST /api/admin/employee/login）
     */
    async login(form) {
      const data = await loginApi(form)
      if (!data?.token) {
        throw new Error('登录响应缺少 token')
      }
      this.setAuth(data.token, data.name)
      return data
    },

    /**
     * 写入登录态（token + 姓名），同时落 localStorage
     */
    setAuth(token, name) {
      this.token = token || ''
      setToken(this.token)
      if (name) {
        this.name = name
        setAdminName(name)
      }
    },

    /**
     * 拉取管理员完整信息（已加载过则复用）
     */
    async loadProfile(force = false) {
      if (this.profile && !force) {
        return this.profile
      }
      this.profile = await getProfile()
      // 以服务端返回的姓名为准
      if (this.profile?.name) {
        this.setAuth(this.token, this.profile.name)
      }
      return this.profile
    },

    /**
     * 退出登录：先通知后端使 token 失效，再清空本地登录态
     */
    async logout() {
      try {
        await logoutApi()
      } catch (error) {
        // token 已失效时后端也会报错，无需阻塞退出流程
      } finally {
        this.reset()
      }
    },

    /**
     * 清空登录态
     */
    reset() {
      this.token = ''
      this.name = ''
      this.profile = null
      clearAuth()
    }
  }
})
