import request from '@/utils/request'

/**
 * 添加服务到预约车
 *
 * <p>后端契约：POST /api/user/cart/add，请求体 { serviceId, quantity }；
 * serviceId 必填且 ≥1，quantity 必填且 1~99（已存在该服务时数量累加）。</p>
 *
 * @param {number|string} serviceId 服务项目 id
 * @param {number} [quantity] 数量，默认 1
 */
export function addToCart(serviceId, quantity = 1) {
  return request({
    url: '/user/cart/add',
    method: 'post',
    data: { serviceId: Number(serviceId), quantity }
  })
}

/**
 * 预约车列表（服务名称、价格、图片、数量）
 */
export function listCart() {
  return request({
    url: '/user/cart/list',
    method: 'get'
  })
}

/**
 * 修改预约车中某项的数量
 *
 * @param {number|string} serviceId 服务项目 id
 * @param {number} quantity 目标数量
 */
export function updateCartQuantity(serviceId, quantity) {
  return request({
    url: '/user/cart/update',
    method: 'put',
    data: { serviceId: Number(serviceId), quantity }
  })
}

/**
 * 从预约车移除某项
 *
 * @param {number|string} serviceId 服务项目 id
 */
export function removeCartItem(serviceId) {
  return request({
    url: '/user/cart/delete',
    method: 'delete',
    params: { serviceId }
  })
}

/**
 * 清空预约车
 */
export function cleanCart() {
  return request({
    url: '/user/cart/clean',
    method: 'delete'
  })
}
