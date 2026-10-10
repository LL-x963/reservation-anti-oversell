/**
 * 文件上传接口（后端 com.pethome.controller.AdminFileController）
 */
import request from '@/utils/request'

/**
 * 上传图片
 *
 * 说明：这里刻意不手动设置 Content-Type，交由浏览器自动带上 multipart 边界，
 * 否则后端会因缺少 boundary 而解析失败。表单字段名固定为 file。
 *
 * @param {File} file 待上传文件
 * @returns {Promise<string>} 可访问的图片地址，如 /upload/2026/10/10/xxx.png
 */
export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/file/upload', formData)
}
