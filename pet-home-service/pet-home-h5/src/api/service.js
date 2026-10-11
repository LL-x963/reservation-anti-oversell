import request from '@/utils/request'

/**
 * 用户端服务分页查询（只返回上架服务）
 *
 * <p>后端契约：GET /api/user/service/page</p>
 *
 * @param {{ page?: number, pageSize?: number, categoryId?: number, petType?: string, minPrice?: number, maxPrice?: number }} params
 *        注意后端分页参数名为 page / pageSize（不是 pageNum），pageSize 上限 100
 * @returns {Promise<{ records: Array<object>, total: number, current: number, size: number }>}
 */
export function pageUserServices(params) {
  return request({
    url: '/user/service/page',
    method: 'get',
    params
  })
}

/**
 * 用户端服务详情（已下架返回 code=0「服务不存在或已下架」）
 *
 * <p>返回字段：id / categoryId / name / price / serviceDuration / petType / suitWeight / description / image</p>
 *
 * @param {number|string} id 服务项目 id
 */
export function getUserServiceDetail(id) {
  return request({
    url: `/user/service/${id}`,
    method: 'get'
  })
}
