/**
 * 服务分类管理接口（后端 com.pethome.controller.AdminCategoryController）
 */
import request from '@/utils/request'

/**
 * 查询全部分类（含禁用），后端已按 sort 升序、id 升序返回，无需分页
 *
 * @returns {Promise<Array<{ id: number, name: string, sort: number, status: number,
 *   createTime: string, updateTime: string }>>}
 */
export function listCategories() {
  return request.get('/admin/category/list')
}

/**
 * 新增分类（status 不传时后端默认 1 启用）
 *
 * @param {{ name: string, sort: number }} data
 * @returns {Promise<number>} 新增分类 id
 */
export function addCategory(data) {
  return request.post('/admin/category/add', data)
}

/**
 * 修改分类
 *
 * 注意：后端为全量更新，name、sort、status 三个字段均必填。
 *
 * @param {{ id: number, name: string, sort: number, status: number }} data
 */
export function updateCategory(data) {
  return request.put('/admin/category/update', data)
}

/**
 * 删除分类（分类下存在服务项目时后端会拒绝删除并返回提示）
 *
 * @param {number} id
 */
export function deleteCategory(id) {
  return request.delete(`/admin/category/${id}`)
}
