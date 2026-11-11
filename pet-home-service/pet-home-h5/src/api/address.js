import request from '@/utils/request'

/**
 * 当前登录用户的地址列表（下单页选择服务地址）
 *
 * <p>后端契约：GET /api/user/address/list，返回数组（默认地址排最前）：</p>
 * <pre>[{ id, consignee, phone, fullAddress, label, isDefault }]</pre>
 */
export function listUserAddresses() {
  return request({
    url: '/user/address/list',
    method: 'get'
  })
}

/**
 * 新增地址
 *
 * @param {{consignee: string, phone: string, sex?: number, provinceName?: string,
 *          cityName?: string, districtName?: string, detail: string, label?: string,
 *          isDefault?: boolean}} payload
 */
export function createUserAddress(payload) {
  return request({
    url: '/user/address',
    method: 'post',
    data: payload
  })
}

/**
 * 修改地址
 *
 * @param {number} id 地址 id
 * @param {object} payload 与新增一致
 */
export function updateUserAddress(id, payload) {
  return request({
    url: `/user/address/${id}`,
    method: 'put',
    data: payload
  })
}

/**
 * 删除地址
 */
export function deleteUserAddress(id) {
  return request({
    url: `/user/address/${id}`,
    method: 'delete'
  })
}

/**
 * 设为默认地址
 */
export function setDefaultAddress(id) {
  return request({
    url: `/user/address/${id}/default`,
    method: 'put'
  })
}
