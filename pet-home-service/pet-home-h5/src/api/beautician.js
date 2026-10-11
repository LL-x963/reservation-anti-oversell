import request from '@/utils/request'

/**
 * 在职美容师列表（下单页选择美容师）
 *
 * <p>后端契约：GET /api/user/beautician/list，返回数组（按评分从高到低）：</p>
 * <pre>[{ id, name, avatar, score }]</pre>
 */
export function listUserBeauticians() {
  return request({
    url: '/user/beautician/list',
    method: 'get'
  })
}
