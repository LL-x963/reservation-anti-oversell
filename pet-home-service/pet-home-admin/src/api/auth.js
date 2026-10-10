/**
 * 管理端登录相关接口（后端 com.pethome.controller.AdminController）
 */
import request from '@/utils/request'

/**
 * 员工账号密码登录
 *
 * 路径 /api/admin/employee/login（后端同时提供 /api/admin/login 别名）
 *
 * @param {{ username: string, password: string }} data
 * @returns {Promise<{ token: string, name: string, userId: number, userType: number, expireSeconds: number }>}
 */
export function login(data) {
  return request.post('/admin/employee/login', data)
}

/**
 * 获取当前登录员工信息（id/username/name/phone/sex/avatar/status）
 */
export function getProfile() {
  return request.get('/admin/profile')
}

/**
 * 退出登录（后端会把 token 从 Redis 白名单移除）
 */
export function logout() {
  return request.post('/admin/logout')
}
