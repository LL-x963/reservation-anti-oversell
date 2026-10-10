/**
 * 排班管理接口（后端 com.pethome.controller.AdminScheduleController）
 */
import request from '@/utils/request'

/**
 * 按美容师与日期查询排班
 *
 * 注意：beauticianId 与 date 均为必填；返回数组（不分页）。
 *
 * @param {{ beauticianId: number, date: string }} params date 格式 yyyy-MM-dd
 * @returns {Promise<Array<{ id: number, beauticianId: number, date: string, timeSlot: string,
 *   available: number, orderId: number|null, occupied: boolean, createTime: string }>>}
 */
export function listSchedules(params) {
  return request.get('/admin/schedule/list', { params })
}

/**
 * 批量设置排班（已存在时段置为可接单、不存在的新增、被订单占用的跳过）
 *
 * @param {{ beauticianId: number, date: string, timeSlots: string[] }} data 时段最多 5 个
 * @returns {Promise<{ total: number, addedCount: number, updatedCount: number, unchangedCount: number,
 *   skippedCount: number, addedSlots: string[], updatedSlots: string[], unchangedSlots: string[],
 *   skippedSlots: Array<{ timeSlot: string, orderId: number, reason: string }> }>}
 */
export function batchSetSchedule(data) {
  return request.post('/admin/schedule/batchSet', data)
}

/**
 * 修改单条排班可用状态
 *
 * 注意：id 在路径中，请求体只传 available（1 可接单 / 0 不可接单）。
 *
 * @param {number} id 排班 id
 * @param {number} available
 */
export function updateScheduleStatus(id, available) {
  return request.put(`/admin/schedule/update/${id}`, { available })
}

/**
 * 删除排班（已被订单占用时后端会拒绝）
 */
export function deleteSchedule(id) {
  return request.delete(`/admin/schedule/${id}`)
}
