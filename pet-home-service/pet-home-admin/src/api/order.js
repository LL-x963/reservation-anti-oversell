/**
 * 预约订单管理接口（后端 com.pethome.controller.AdminOrderController）
 *
 * 状态流转：1待支付 →(支付)→ 2待确认 →(确认)→ 3待上门 →(开始服务)→ 4服务中
 * →(完成)→ 7待评价 →(评价)→ 5已完成；除「已完成 / 已取消」外均可取消。
 */
import request from '@/utils/request'

/**
 * 分页查询订单
 *
 * 注意：后端分页参数名为 page / pageSize（非 pageNum），pageSize 上限 100；
 * 日期筛选参数名为 appointmentDate（yyyy-MM-dd，按预约日期精确匹配，非下单时间）。
 *
 * @param {{ page: number, pageSize: number, status?: number, appointmentDate?: string,
 *   beauticianId?: number }} params
 * @returns {Promise<{ records: Array<object>, total: number, current: number, size: number }>}
 */
export function pageOrders(params) {
  return request.get('/admin/order/page', { params })
}

/**
 * 订单详情（含收货信息、服务明细、评价；未分配美容师时 beauticianName 为 null）
 *
 * @param {number} id 订单 id
 */
export function getOrderDetail(id) {
  return request.get(`/admin/order/${id}`)
}

/**
 * 确认订单：仅「待确认」可确认，状态流转为「待上门」
 *
 * @param {number} id 订单 id
 */
export function confirmOrder(id) {
  return request.put(`/admin/order/confirm/${id}`)
}

/**
 * 分配 / 改派美容师：「待确认」「待上门」可操作
 *
 * 注意：后端只调整订单归属，不释放原排班、也不占用新排班。
 *
 * @param {number} orderId 订单 id
 * @param {number} beauticianId 美容师 id
 */
export function assignOrder(orderId, beauticianId) {
  return request.put('/admin/order/assign', { orderId, beauticianId })
}

/**
 * 取消订单：「已完成」「已取消」不可取消（后端会同时释放已占用排班）
 *
 * @param {number} id 订单 id
 */
export function cancelOrder(id) {
  return request.put(`/admin/order/cancel/${id}`)
}

/**
 * 完成订单：「待上门」「服务中」可完成，状态流转为「待评价」
 *
 * @param {number} id 订单 id
 */
export function completeOrder(id) {
  return request.put(`/admin/order/complete/${id}`)
}
