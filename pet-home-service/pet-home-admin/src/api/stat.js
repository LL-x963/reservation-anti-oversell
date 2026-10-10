/**
 * 数据统计接口（后端 com.pethome.controller.AdminStatController）
 *
 * 统计口径：仅计入「已完成」订单（5已完成 + 7待评价），营业额按支付时间归入自然日。
 * 注意：除营业额统计外，三个排行 / 复购率接口均为**全量口径**，不接受时间参数。
 */
import request from '@/utils/request'

/**
 * 营业额统计
 *
 * @param {{ date?: string, type?: 'day'|'week'|'month' }} params
 *   date 基准日期（yyyy-MM-dd，不传默认今天）；
 *   type 统计粒度：day 当天 / week 所在自然周（周一至周日）/ month 所在自然月
 * @returns {Promise<{ dates: string[], revenues: number[], totalAmount: number }>}
 *   注意：任何粒度都返回**区间内逐日**数据（day 为 1 个点、week 为 7 个点、month 为 28~31 个点），
 *   dates 与 revenues 一一对应，无数据日期补 0
 */
export function getRevenue(params) {
  return request.get('/admin/stat/revenue', { params })
}

/**
 * 服务销量排行 Top10（按销量降序，全量口径）
 *
 * @returns {Promise<Array<{ serviceId: number, name: string, sales: number,
 *   amount: number, ratio: number }>>} ratio 为 0~1 的销量占比（分母为全部服务总销量）
 */
export function getServiceRank() {
  return request.get('/admin/stat/serviceRank')
}

/**
 * 美容师业绩排行（按营业额降序，全量口径）
 *
 * @returns {Promise<Array<{ beauticianId: number, name: string|null, orderCount: number,
 *   amount: number, score: number }>>} 美容师记录被删除时 name 为 null
 */
export function getBeauticianRank() {
  return request.get('/admin/stat/beauticianRank')
}

/**
 * 用户复购率（全量口径）
 *
 * @returns {Promise<{ repeatUsers: number, totalUsers: number, repurchaseRate: number }>}
 *   repeatUsers 已完成订单 ≥ 2 笔的用户数；totalUsers 有已完成订单的用户数；repurchaseRate 为 0~1
 */
export function getRepurchase() {
  return request.get('/admin/stat/repurchase')
}
