import axios from 'axios'
import { ElMessage } from 'element-plus'

import { clearToken, getToken, redirectToLogin } from './auth'

/** 后端业务成功码（Result.code：1 成功、0 失败） */
const CODE_SUCCESS = 1

/** 未登录状态码：后端未登录时返回 HTTP 401 */
const CODE_UNAUTHORIZED = 401

const service = axios.create({
  // 开发环境由 vite 代理到 http://localhost:8080
  baseURL: '/api',
  timeout: 30000
})

// ---------------- 请求拦截器：自动携带 token ----------------
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      // 后端约定：Authorization: Bearer {token}
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ---------------- 响应拦截器 ----------------
service.interceptors.response.use(
  (response) => {
    const res = response.data

    // 非统一返回结构（如文件流）直接透传
    if (!res || typeof res !== 'object' || !('code' in res)) {
      return res
    }

    // code=1：成功，只把 data 交给业务层
    if (res.code === CODE_SUCCESS) {
      return res.data
    }

    // code=401：登录态失效，清除 token 并跳登录
    if (res.code === CODE_UNAUTHORIZED) {
      handleUnauthorized(res.msg)
      return Promise.reject(new Error(res.msg || '登录已失效，请重新登录'))
    }

    // code=0：业务失败，弹出后端提示
    ElMessage.error(res.msg || '操作失败')
    return Promise.reject(new Error(res.msg || '操作失败'))
  },
  (error) => {
    const status = error?.response?.status
    const message = error?.response?.data?.msg

    // 后端未登录时返回 HTTP 401
    if (status === CODE_UNAUTHORIZED) {
      handleUnauthorized(message)
      return Promise.reject(error)
    }

    // 统一网络/服务端错误提示
    if (message) {
      ElMessage.error(message)
    } else if (error?.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (status) {
      ElMessage.error(`请求失败（${status}）`)
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

/**
 * 处理登录态失效：清除 token + 提示 + 跳转登录页
 */
function handleUnauthorized(message) {
  clearToken()
  ElMessage.error(message || '登录已失效，请重新登录')
  redirectToLogin()
}

export default service
