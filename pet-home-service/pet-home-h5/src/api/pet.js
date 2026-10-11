import request from '@/utils/request'

/**
 * 宠物档案接口（归属用户由后端根据登录态绑定，客户端不能指定）
 *
 * <p>字段：id / name / breed / gender（0未知 1公 2母）/ birthday（yyyy-MM-dd）/
 * weight（kg，0.01~999.99，最多 2 位小数）/ vaccine / remark / createTime / updateTime</p>
 */

/**
 * 当前用户的宠物列表（按 id 升序）
 */
export function listUserPets() {
  return request({
    url: '/user/pet/list',
    method: 'get'
  })
}

/**
 * 宠物详情
 *
 * @param {number|string} id 宠物 id
 */
export function getUserPetDetail(id) {
  return request({
    url: `/user/pet/${id}`,
    method: 'get'
  })
}

/**
 * 新增宠物
 *
 * @param {{ name: string, breed?: string, gender?: number, birthday?: string,
 *           weight?: number, vaccine?: string, remark?: string }} data
 * @returns {Promise<number>} 新增宠物 id
 */
export function addPet(data) {
  return request({
    url: '/user/pet/add',
    method: 'post',
    data
  })
}

/**
 * 修改宠物（全量更新，必须带 id）
 */
export function updatePet(data) {
  return request({
    url: '/user/pet/update',
    method: 'put',
    data
  })
}

/**
 * 删除宠物（存在未完成订单时后端会拒绝）
 *
 * @param {number|string} id 宠物 id
 */
export function deletePet(id) {
  return request({
    url: `/user/pet/${id}`,
    method: 'delete'
  })
}
