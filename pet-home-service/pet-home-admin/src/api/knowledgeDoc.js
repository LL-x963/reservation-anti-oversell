/**
 * 知识库文档管理接口（后端 com.pethome.controller.AdminKnowledgeDocController）
 *
 * 分类固定三类：护理 / 饮食 / 疾病预防；
 * 索引状态：0未索引 1索引中 2正常 3失败。
 */
import request from '@/utils/request'

/**
 * 分页查询文档（**不返回大字段 content**，编辑时再调详情接口取正文）
 *
 * 注意：后端分页参数名为 page / pageSize（非 pageNum），pageSize 上限 100。
 *
 * @param {{ page: number, pageSize: number, category?: string, enabled?: number }} params
 *   category 精确匹配；enabled 1 启用 / 0 禁用
 * @returns {Promise<{ records: Array<object>, total: number, current: number, size: number }>}
 */
export function pageKnowledgeDocs(params) {
  return request.get('/admin/knowledge/doc/page', { params })
}

/**
 * 文档详情（含正文 content）
 *
 * @param {number} id 文档 id
 */
export function getKnowledgeDocDetail(id) {
  return request.get(`/admin/knowledge/doc/${id}`)
}

/**
 * 新增文档
 *
 * 说明：标题全局唯一（重复时后端返回「文档标题已存在：xxx」，拦截器会直接提示）；
 * 新增后默认启用、索引状态为 0未索引，需重建索引后才会参与检索。
 *
 * @param {{ title: string, category: string, content: string }} data
 * @returns {Promise<number>} 新增文档 id
 */
export function addKnowledgeDoc(data) {
  return request.post('/admin/knowledge/doc/add', data)
}

/**
 * 修改文档
 *
 * 注意：标题 / 分类 / 内容任一变更，后端会把索引状态重置为 0未索引；
 * 启用状态不通过该接口修改（走 /status）。
 *
 * @param {{ id: number, title: string, category: string, content: string }} data
 */
export function updateKnowledgeDoc(data) {
  return request.put('/admin/knowledge/doc/update', data)
}

/**
 * 启用 / 禁用文档（禁用后不参与索引与检索）
 *
 * @param {number} id 文档 id
 * @param {number} enabled 1 启用 / 0 禁用
 */
export function updateKnowledgeDocStatus(id, enabled) {
  return request.put('/admin/knowledge/doc/status', { id, enabled })
}

/**
 * 删除文档（物理删除）
 *
 * @param {number} id 文档 id
 */
export function deleteKnowledgeDoc(id) {
  return request.delete(`/admin/knowledge/doc/${id}`)
}

/**
 * 重建索引（异步执行，接口立即返回，无响应数据）
 *
 * 说明：提交后所有「启用」文档先被置为 1索引中，随后异步调用 RAG 服务，
 * 成功置 2正常、失败置 3失败，需轮询 /indexStatus 的 rebuilding 字段观察进度。
 * 存在「索引中」文档时后端会拒绝并提示「索引正在重建中」；没有启用的文档时直接跳过。
 */
export function reindexKnowledgeDocs() {
  return request.post('/admin/knowledge/doc/reindex')
}

/**
 * 查询索引状态概览
 *
 * @returns {Promise<{ totalCount: number, enabledCount: number, disabledCount: number,
 *   notIndexedCount: number, indexingCount: number, normalCount: number, failedCount: number,
 *   rebuilding: boolean }>} rebuilding 为 true 表示存在「索引中」文档（重建进行中）
 */
export function getKnowledgeIndexStatus() {
  return request.get('/admin/knowledge/doc/indexStatus')
}
