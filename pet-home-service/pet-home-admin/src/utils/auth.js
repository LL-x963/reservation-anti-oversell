/**
 * 登录态本地存储工具
 *
 * 单独抽出来是为了避免循环依赖：request.js（需要取 token）与 stores/user.js（需要写 token）
 * 都只依赖本文件，而不互相依赖。
 */

const TOKEN_KEY = 'pethome_admin_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
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
