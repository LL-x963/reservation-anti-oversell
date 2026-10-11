/**
 * 登录态存储工具
 *
 * <p>存储 key 约定：token → user_token，用户名 → user_name（H5 端与管理端、小程序端互不干扰）。</p>
 */

/** token 存储 key */
const TOKEN_KEY = 'user_token'

/** 用户名存储 key */
const USER_NAME_KEY = 'user_name'

/**
 * 读取 token
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

/**
 * 写入 token（传空值等价于清除）
 */
export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

/**
 * 读取用户名（登录接口返回的 name）
 */
export function getUserName() {
  return localStorage.getItem(USER_NAME_KEY) || ''
}

/**
 * 写入用户名
 */
export function setUserName(name) {
  if (name) {
    localStorage.setItem(USER_NAME_KEY, name)
  } else {
    localStorage.removeItem(USER_NAME_KEY)
  }
}

/**
 * 清除登录态（token + 用户名）
 */
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_NAME_KEY)
}

/**
 * 跳转登录页并带上回跳地址
 *
 * <p>路由为 hash 模式，这里直接改 location.hash，避免与 router 循环依赖。</p>
 */
export function redirectToLogin() {
  const current = window.location.hash.replace(/^#/, '') || '/home'
  const target = current.startsWith('/login') ? '/login' : `/login?redirect=${encodeURIComponent(current)}`
  window.location.hash = target
}
