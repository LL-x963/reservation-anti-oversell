import request from '@/utils/request'

/**
 * 手机号 + 密码登录（免登录接口）
 *
 * <p>后端同一接口支持两种方式：传 {openid} 走微信登录、传 {phone, password} 走密码登录；
 * H5 只使用手机号 + 密码。手机号未注册时后端自动注册（密码即注册密码）。</p>
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
