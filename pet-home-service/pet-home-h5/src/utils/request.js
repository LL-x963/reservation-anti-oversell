import axios from 'axios'
import { ElMessage } from 'element-plus'

import { clearAuth, getToken, redirectToLogin } from './auth'

/** 后端业务成功码（Result.code：1 成功、0 失败） */
const CODE_SUCCESS = 1

/**
 * 未登录状态码
 *
 * <p>注意：后端未登录时返回的是 HTTP 401（响应体为 {code:0,msg:"未登录，请先登录"}），
 * 因此这里同时兼容「HTTP 状态码 401」与「响应体 code=401」两种写法。</p>
 */
const CODE_UNAUTHORIZED = 401

/** 登录失效只提示/跳转一次，避免并发请求弹多个提示 */
let redirecting = false

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

    // 响应体 code=401：登录态失效
    if (res.code === CODE_UNAUTHORIZED) {
      handleUnauthorized(res.msg)
      return Promise.reject(new Error(res.msg || '登录已失效，请重新登录'))
    }

    // code=0：业务失败，弹出后端提示（如「该时段不可约」）
    // 传 silent: true 的请求（如 AI 问答）由调用方在界面上自行展示失败态，避免重复提示
    if (!response.config.silent) {
      ElMessage.error(res.msg || '操作失败')
    }
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

    // 统一网络/服务端异常提示（silent: true 的请求由调用方自行展示，不弹全局提示）
    if (!error?.config?.silent) {
      if (message) {
        ElMessage.error(message)
      } else if (error?.code === 'ECONNABORTED') {
        ElMessage.error('请求超时，请稍后重试')
      } else if (status) {
        ElMessage.error(`请求失败（${status}）`)
      } else {
        ElMessage.error('网络异常，请检查网络连接')
      }
    }
    return Promise.reject(error)
  }
)

/**
 * 处理登录态失效：清除 token + 提示 + 跳转登录页
 */
function handleUnauthorized(message) {
  clearAuth()
  if (redirecting) {
    return
  }
  redirecting = true
  ElMessage.error(message || '登录已失效，请重新登录')
  redirectToLogin()
  // 留出提示展示时间后复位，避免后续请求再次触发提示
  setTimeout(() => {
    redirecting = false
  }, 1500)
}

export default service
