import request from '@/utils/request'

/**
 * 知识库问答（AI 养宠百科）
 *
 * <p>后端契约：POST /api/user/knowledge/ask</p>
 * <ul>
 *   <li>请求：{ question（≤1000 字）, conversationHistory: [{ role: 'user' | 'assistant', content }], petId（可选） }</li>
 *   <li>响应 data：{ answer, references: [{ docId, title, content }] }</li>
 *   <li>RAG 服务不可用时返回 code=0 / bizCode=600</li>
 * </ul>
 *
 * @param {object} data 请求体
 * @param {object} [config] 透传给 axios 的额外配置（如 { silent: true } 表示失败不弹全局提示）
 */
export function askKnowledge(data, config = {}) {
  return request({
    url: '/user/knowledge/ask',
    method: 'post',
    data,
    ...config
  })
}

/**
 * 问答历史分页（服务端保留的完整记录，按需使用）
 *
 * @param {object} params { page, pageSize }
 */
export function pageAskHistory(params) {
  return request({
    url: '/user/knowledge/history/page',
    method: 'get',
    params
  })
}

/**
 * 清空问答历史
 */
export function clearAskHistory() {
  return request({
    url: '/user/knowledge/history',
    method: 'delete'
  })
}
