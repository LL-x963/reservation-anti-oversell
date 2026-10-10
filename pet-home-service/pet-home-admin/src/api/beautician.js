/**
 * 美容师管理接口（后端 com.pethome.controller.AdminBeauticianController）
 */
import request from '@/utils/request'

/**
 * 分页查询美容师
 *
 * 注意：后端分页参数名为 page / pageSize（非 pageNum），pageSize 上限 100。
 * 可选筛选：name（模糊）、skillTag（精确匹配某一项标签）、status（1 在职 / 0 离职）
 *
 * @param {{ page: number, pageSize: number, name?: string, skillTag?: string, status?: number }} params
 * @returns {Promise<{ records: Array<object>, total: number, current: number, size: number }>}
 */
export function pageBeauticians(params) {
  return request.get('/admin/beautician/page', { params })
}

/**
 * 查询全部在职美容师（status = 1，按 id 升序）
 *
 * 本页面用不到，供排班等页面复用。
 *
 * @returns {Promise<Array<object>>}
 */
export function listOnDutyBeauticians() {
  return request.get('/admin/beautician/list')
}

/**
 * 新增美容师
 *
 * 说明：skillTags 传数组；score 由服务端固定为 5.00，不接受客户端传入；
 * status 不传时默认 1 在职。
 *
 * @param {{ name: string, avatar?: string, skillTags?: string[], serviceRange?: string,
 *   entryTime?: string, status?: number }} data
 * @returns {Promise<number>} 新增美容师 id
 */
export function addBeautician(data) {
  return request.post('/admin/beautician/add', data)
}

/**
 * 修改美容师（后端为全量更新，不接收 score，但 status 必填）
 */
export function updateBeautician(data) {
  return request.put('/admin/beautician/update', data)
}

/**
 * 删除美容师（存在未完成订单时后端会拒绝）
 */
export function deleteBeautician(id) {
  return request.delete(`/admin/beautician/${id}`)
}
