import request from '@/utils/request'

/**
 * 用户端服务分类列表
 *
 * <p>后端契约：GET /api/user/category/list，返回**数组**（非分页），
 * 元素为 { id, name, sort }，已按 sort 升序、只含启用分类。</p>
 *
 * @returns {Promise<Array<{id: number, name: string, sort: number}>>}
 */
export function listUserCategories() {
  return request({
    url: '/user/category/list',
    method: 'get'
  })
}
