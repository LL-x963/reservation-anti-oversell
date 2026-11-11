import request from '@/utils/request'

/**
 * 手机号 + 密码登录（免登录接口）
 *
 * <p>手机号必须先通过 /user/register 注册，未注册账号会返回「手机号或密码错误」，
 * 不会再自动创建账号。连续失败次数过多会触发账号临时锁定。</p>
 *
 * @param {string} phone 手机号
 * @param {string} password 密码（明文，走 https/本地代理）
 * @returns {Promise<{token: string, userId: number, userType: number, expireSeconds: number, name: string}>}
 */
export function login(phone, password) {
  return request({
    url: '/user/login',
    method: 'post',
    data: { phone, password }
  })
}

/**
 * 手机号 + 密码注册
 *
 * <p>密码需 8~32 位且同时包含字母和数字，后端会再次校验。</p>
 *
 * @param {{phone: string, password: string, name?: string}} payload
 */
export function register(payload) {
  return request({
    url: '/user/register',
    method: 'post',
    data: payload
  })
}

/**
 * 获取当前登录用户信息（昵称、头像、手机号等）
 */
export function getProfile() {
  return request({
    url: '/user/profile',
    method: 'get'
  })
}

/**
 * 退出登录（后端会吊销 Redis 中的 token）
 */
export function logout() {
  return request({
    url: '/user/logout',
    method: 'post'
  })
}
