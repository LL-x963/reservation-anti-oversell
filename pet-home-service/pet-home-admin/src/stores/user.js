import { defineStore } from 'pinia'

import { getProfile, login as loginApi, logout as logoutApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/auth'

/**
 * 登录态 store：token + 当前管理员信息
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    /** 登录 token（初始化时从 localStorage 恢复，刷新页面不丢登录态） */
    token: getToken(),
    /** 当前登录员工信息（来自 /api/admin/profile） */
    profile: null
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    /** 顶部展示用：优先姓名，其次账号 */
    adminName: (state) => state.profile?.name || state.profile?.username || '管理员',
    adminAvatar: (state) => state.profile?.avatar || ''
  },

  actions: {
    /**
     * 账号密码登录
     */
    async login(form) {
      const data = await loginApi(form)
      if (!data?.token) {
        throw new Error('登录响应缺少 token')
      }
      this.token = data.token
      setToken(data.token)
      return data
    },

    /**
     * 拉取当前管理员信息（已加载过则直接复用）
     */
    async loadProfile(force = false) {
      if (this.profile && !force) {
        return this.profile
      }
      this.profile = await getProfile()
      return this.profile
    },

    /**
     * 退出登录：先通知后端使 token 失效，再清空本地登录态
     */
    async logout() {
      try {
        await logoutApi()
      } catch (error) {
        // token 已失效时后端也会报错，这里无需阻塞退出流程
      } finally {
        this.reset()
      }
    },

    /**
     * 清空登录态
     */
    reset() {
      this.token = ''
      this.profile = null
      clearToken()
    }
  }
})
