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
