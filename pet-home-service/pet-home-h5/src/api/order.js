import request from '@/utils/request'

/**
 * 提交预约订单
 *
 * <p>后端契约：POST /api/user/order/submit</p>
 * <ul>
 *   <li>请求：{ addressId, petId, beauticianId, appointmentDate（yyyy-MM-dd）,
 *       timeSlot, items: [{ serviceId, quantity }], remark（选填，≤200 字） }</li>
 *   <li>返回：{ orderId, orderNumber, totalAmount, status }（status 1 待支付）</li>
 *   <li>常见失败：该时段不可约（未排班/已关闭）、该时段已被预约（并发抢占）</li>
 * </ul>
 *
 * <p>注意：下单成功后后端会自动清空该用户的预约车。</p>
 *
 * @param {{ addressId: number, petId: number, beauticianId: number, appointmentDate: string,
 *           timeSlot: string, items: Array<{serviceId: number, quantity: number}>, remark?: string }} data
 */
export function submitOrder(data) {
  return request({
    url: '/user/order/submit',
    method: 'post',
    data
  })
}

/**
 * 我的订单分页
 *
 * 注意：后端分页参数是 page（不是 pageNum），pageSize 上限 100；
 * status 可选：1待支付 2待确认 3待上门 4服务中 5已完成 6已取消 7待评价。
 * 列表项已带服务明细 items（下单快照：serviceId/name/price/quantity/amount）。
 *
 * @param {{ status?: number, page?: number, pageSize?: number }} params
 */
export function pageUserOrders(params) {
  return request({
    url: '/user/order/page',
    method: 'get',
    params
  })
}

/**
 * 订单详情（含地址、服务明细、备注、评价等）
 *
 * @param {number|string} id 订单 id
 */
export function getUserOrderDetail(id) {
  return request({
    url: `/user/order/${id}`,
    method: 'get'
  })
}

/**
 * 模拟支付（后端直接把订单置为待确认）
 *
 * @param {number|string} orderId 订单 id
 */
export function payOrder(orderId) {
  return request({
    url: '/user/order/pay',
    method: 'post',
    data: { orderId: Number(orderId) }
  })
}

/**
 * 取消订单（已完成/已取消之外的状态均可取消）
 *
 * @param {number|string} id 订单 id
 */
export function cancelOrder(id) {
  return request({
    url: `/user/order/cancel/${id}`,
    method: 'put'
  })
}

/**
 * 催单（后端有频率限制，超频返回 bizCode 603）
 *
 * @param {number|string} id 订单 id
 */
export function remindOrder(id) {
  return request({
    url: `/user/order/reminder/${id}`,
    method: 'post'
  })
}

/**
 * 评价订单
 *
 * @param {{ orderId: number, score: number, content?: string }} data score 1~5，content ≤500 字
 */
export function commentOrder(data) {
  return request({
    url: '/user/order/comment',
    method: 'post',
    data
  })
}

/**
 * 删除已取消订单（仅本人、仅限已取消状态）
 *
 * @param {number|string} id 订单 id
 */
export function deleteOrder(id) {
  return request({
    url: `/user/order/${id}`,
    method: 'delete'
  })
}
