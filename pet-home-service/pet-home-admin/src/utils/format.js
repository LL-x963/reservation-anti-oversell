/**
 * 通用格式化工具
 */

/**
 * 格式化日期时间
 *
 * 兼容后端可能出现的几种序列化形式：
 *   "2026-10-10 20:13:38"、"2026-10-10T20:13:38"、"2026-10-10T20:13:38.123"、[2026,10,10,20,13,38]
 *
 * @param {unknown} value 原始值
 * @param {string} fallback 空值时的占位
 * @returns {string} 形如 2026-10-10 20:13:38
 */
export function formatDateTime(value, fallback = '-') {
  if (!value) {
    return fallback
  }

  // Jackson 关闭 WRITE_DATES_AS_TIMESTAMPS 时为字符串，开启时可能是数组，这里都兜住
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value
    const pad = (num) => String(num).padStart(2, '0')
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`
  }

  return String(value).replace('T', ' ').slice(0, 19)
}

/**
 * 金额格式化：保留两位小数
 *
 * @param {unknown} value 原始值
 * @param {string} fallback 空值时的占位
 * @returns {string} 形如 99.00
 */
export function formatAmount(value, fallback = '-') {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  const amount = Number(value)
  return Number.isFinite(amount) ? amount.toFixed(2) : String(value)
}
