/**
 * 登录态本地存储工具
 *
 * localStorage key 约定：
 *   admin_token —— 登录 token（请求拦截器从这里取）
 *   admin_name  —— 登录人姓名（顶部导航栏直接展示）
 *
 * 单独抽出来是为了避免循环依赖：request.js（取 token）与 stores/user.js（写 token）
 * 都只依赖本文件，而不互相依赖。
 */

const TOKEN_KEY = 'admin_token'
const NAME_KEY = 'admin_name'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function getAdminName() {
  return localStorage.getItem(NAME_KEY) || ''
}

export function setAdminName(name) {
  localStorage.setItem(NAME_KEY, name)
}

export function clearAdminName() {
  localStorage.removeItem(NAME_KEY)
}

/**
 * 清空全部登录态（退出登录、token 失效时使用）
 */
export function clearAuth() {
  clearToken()
  clearAdminName()
}

/**
 * 跳转到登录页（hash 模式，直接改 hash 即可；已在登录页时不重复跳转）
 */
export function redirectToLogin() {
  if (window.location.hash.startsWith('#/login')) {
    return
  }
  window.location.hash = '#/login'
}
