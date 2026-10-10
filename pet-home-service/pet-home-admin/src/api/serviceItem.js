/**
 * 服务项目管理接口（后端 com.pethome.controller.AdminServiceController）
 */
import request from '@/utils/request'

/**
 * 分页查询服务项目
 *
 * 注意：后端分页参数名为 page / pageSize（非 pageNum），pageSize 上限 100。
 * 可选筛选：categoryId、name（模糊）、status（1 上架 / 0 下架）
 *
 * @param {{ page: number, pageSize: number, categoryId?: number, name?: string, status?: number }} params
 * @returns {Promise<{ records: Array<object>, total: number, current: number, size: number }>}
 */
export function pageServiceItems(params) {
  return request.get('/admin/service/page', { params })
}

/**
 * 新增服务项目（不传 status 时后端默认 1 上架）
 *
 * @returns {Promise<number>} 新增服务项目 id
 */
export function addServiceItem(data) {
  return request.post('/admin/service/add', data)
}

/**
 * 修改服务项目（后端为全量更新，不接收 status）
 */
export function updateServiceItem(data) {
  return request.put('/admin/service/update', data)
}

/**
 * 服务项目上下架
 *
 * @param {number} id
 * @param {number} status 1 上架 / 0 下架
 */
export function updateServiceItemStatus(id, status) {
  return request.put(`/admin/service/status/${id}`, { status })
}

/**
 * 删除服务项目（存在未完成订单时后端会拒绝）
 */
export function deleteServiceItem(id) {
  return request.delete(`/admin/service/${id}`)
}
