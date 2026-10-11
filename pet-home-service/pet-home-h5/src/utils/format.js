/**
 * 格式化工具
 */

/**
 * 金额格式化：统一展示为 ￥99.00
 *
 * @param {unknown} value 金额（后端为 decimal 字符串或数字）
 * @param {string} fallback 非数字时的占位
 * @returns {string}
 */
export function formatPrice(value, fallback = '￥--') {
  const number = Number(value)
  if (value === null || value === undefined || value === '' || !Number.isFinite(number)) {
    return fallback
  }
  return `￥${number.toFixed(2)}`
}

/**
 * 服务时长格式化：统一展示为 60分钟
 *
 * @param {unknown} value 时长（分钟）
 * @param {string} fallback 空值时的占位
 * @returns {string}
 */
export function formatDuration(value, fallback = '时长待定') {
  const number = Number(value)
  if (!Number.isFinite(number) || number <= 0) {
    return fallback
  }
  return `${number}分钟`
}
