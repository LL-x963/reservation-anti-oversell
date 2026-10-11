<script setup>
/**
 * 知识库文档管理页面
 *
 * 对接 /api/admin/knowledge/doc：
 * page（分页，不返回正文）、{id}（详情含正文）、add、update、status、delete、reindex（异步）、indexStatus。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import {
  addKnowledgeDoc,
  deleteKnowledgeDoc,
  getKnowledgeDocDetail,
  getKnowledgeIndexStatus,
  pageKnowledgeDocs,
  reindexKnowledgeDocs,
  updateKnowledgeDoc,
  updateKnowledgeDocStatus
} from '@/api/knowledgeDoc'
import { formatDateTime } from '@/utils/format'

/** 固定分类（与后端 KnowledgeCategory 枚举一致） */
const CATEGORIES = ['护理', '饮食', '疾病预防']

/** 索引状态展示配置（与后端 IndexStatus 枚举一致） */
const INDEX_STATUS_STYLE = {
  0: { text: '未索引', type: 'info' },
  1: { text: '索引中', type: 'primary' },
  2: { text: '正常', type: 'success' },
  3: { text: '失败', type: 'danger' }
}

/**
 * 索引状态标签样式；状态值异常时退化为后端描述
 */
function indexStatusStyle(row) {
  return INDEX_STATUS_STYLE[row?.indexStatus] || { text: row?.indexStatusDesc || '-', type: 'info' }
}

/** 重建索引后的轮询间隔与最长轮询时间 */
const POLL_INTERVAL = 1500
const POLL_TIMEOUT = 60000

// ---------------- 列表 ----------------

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  category: null,
  enabled: null,
  // 后端分页参数名为 page / pageSize（不是 pageNum）
  page: 1,
  pageSize: 10
})

/**
 * 加载分页数据
 */
async function loadList() {
  loading.value = true
  try {
    const data = await pageKnowledgeDocs({
      page: query.page,
      pageSize: query.pageSize,
      category: query.category || undefined,
      enabled: query.enabled === null || query.enabled === '' ? undefined : query.enabled
    })
    list.value = data?.records || []
    total.value = Number(data?.total || 0)
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleSizeChange() {
  query.page = 1
  loadList()
}

function handleCurrentChange() {
  loadList()
}

// ---------------- 索引状态（概览 + 轮询） ----------------

const statusLoading = ref(false)
const indexStatus = ref(null)

/** 状态数量兜底，避免接口未返回时模板报错 */
const counts = computed(
  () =>
    indexStatus.value || {
      totalCount: 0,
      enabledCount: 0,
      disabledCount: 0,
      notIndexedCount: 0,
      indexingCount: 0,
      normalCount: 0,
      failedCount: 0,
      rebuilding: false
    }
)

const rebuilding = computed(() => Boolean(indexStatus.value?.rebuilding))

const indexStatusTag = computed(() =>
  rebuilding.value ? { text: '索引重建中', type: 'warning' } : { text: '索引空闲', type: 'success' }
)

let pollTimer = null
let pollElapsed = 0
/** 是否观察到过「重建中」，用于区分「提交后很快完成」与「本就没有启用文档」 */
let sawRebuilding = false

async function loadIndexStatus() {
  statusLoading.value = true
  try {
    indexStatus.value = await getKnowledgeIndexStatus()
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    statusLoading.value = false
  }
}

function stopPolling() {
  if (pollTimer) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

/**
 * 轮询索引状态，直到不再处于「重建中」
 */
async function pollIndexStatus() {
  await loadIndexStatus()

  // 仍在重建：继续轮询（超时后停止，避免无限请求）
  if (rebuilding.value) {
    sawRebuilding = true
    pollElapsed += POLL_INTERVAL
    if (pollElapsed >= POLL_TIMEOUT) {
      stopPolling()
      ElMessage.warning('索引重建耗时较长，已停止自动刷新，可稍后点「刷新状态」查看')
      return
    }
    pollTimer = window.setTimeout(pollIndexStatus, POLL_INTERVAL)
    return
  }

  stopPolling()

  // 提交后从未进入「重建中」：说明没有启用的文档，后端直接跳过
  if (!sawRebuilding && counts.value.enabledCount === 0) {
    ElMessage.info('当前没有启用的文档，无需重建索引')
    return
  }

  if (counts.value.failedCount > 0) {
    ElMessage.warning(
      `索引重建完成：${counts.value.normalCount} 篇正常，${counts.value.failedCount} 篇失败（常见原因：RAG 服务未启动，请检查后重试）`
    )
  } else {
    ElMessage.success(`索引重建完成：${counts.value.normalCount} 篇文档索引正常`)
  }
  // 索引状态变化后同步刷新列表标签
  await loadList()
}

function startPolling() {
  stopPolling()
  pollElapsed = 0
  sawRebuilding = false
  pollIndexStatus()
}

/** 手动刷新索引状态（轮询超时或外部变更后使用） */
async function handleRefreshStatus() {
  await loadIndexStatus()
  if (rebuilding.value) {
    startPolling()
  }
}

// ---------------- 重建索引 ----------------

const reindexLoading = ref(false)

async function handleReindex() {
  reindexLoading.value = true
  try {
    await reindexKnowledgeDocs()
    ElMessage.success('索引重建任务已提交')
    await loadList()
    startPolling()
  } catch (error) {
    // 「索引正在重建中」等业务提示已由拦截器弹出
  } finally {
    reindexLoading.value = false
  }
}

// ---------------- 启用 / 禁用 ----------------

/**
 * 开关切换前处理：禁用需二次确认，切换成功后刷新列表与索引概览
 *
 * @returns {Promise<boolean>} true 才真正切换
 */
async function handleBeforeToggle(row) {
  const nextEnabled = row.enabled === 1 ? 0 : 1

  if (nextEnabled === 0) {
    try {
      await ElMessageBox.confirm(
        `确定要禁用文档「${row.title}」吗？禁用后该文档不参与知识库检索。`,
        '禁用确认',
        { type: 'warning', confirmButtonText: '禁用', cancelButtonText: '取消' }
      )
    } catch {
      return false
    }
  }

  try {
    await updateKnowledgeDocStatus(row.id, nextEnabled)
    ElMessage.success(nextEnabled === 1 ? '已启用' : '已禁用')
    await loadList()
    await loadIndexStatus()
    return true
  } catch (error) {
    // 失败保持原状态，提示已由拦截器弹出
    return false
  }
}

// ---------------- 删除 ----------------

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除文档「${row.title}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }

  try {
    await deleteKnowledgeDoc(row.id)
    ElMessage.success('删除成功')
    // 删除当前页最后一条时回退一页
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    await loadList()
    await loadIndexStatus()
  } catch (error) {
    // 提示已由拦截器弹出
  }
}

// ---------------- 新增 / 编辑 ----------------

const dialogVisible = ref(false)
const dialogMode = ref('add')
const submitting = ref(false)
const detailLoading = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  title: '',
  category: '',
  content: ''
})

const dialogTitle = computed(() => (dialogMode.value === 'add' ? '新增文档' : '编辑文档'))

const rules = {
  title: [
    { required: true, message: '请输入文档标题', trigger: 'blur' },
    { max: 200, message: '标题长度不能超过 200', trigger: 'blur' }
  ],
  category: [{ required: true, message: '请选择文档分类', trigger: 'change' }],
  content: [
    { required: true, message: '请输入文档内容', trigger: 'blur' },
    { max: 20000, message: '内容长度不能超过 20000', trigger: 'blur' }
  ]
}

/** 编辑前的原始值，用于判断内容是否变更（变更后后端会重置索引状态） */
let original = null

function handleAdd() {
  dialogMode.value = 'add'
  resetForm()
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗
 *
 * 列表接口不返回正文，这里按 id 拉取详情后再填充内容
 */
async function handleEdit(row) {
  dialogMode.value = 'edit'
  resetForm()
  form.id = row.id
  dialogVisible.value = true
  detailLoading.value = true

  try {
    const detail = await getKnowledgeDocDetail(row.id)
    form.title = detail?.title || ''
    form.category = detail?.category || ''
    form.content = detail?.content || ''
    original = { title: form.title, category: form.category, content: form.content }
  } catch (error) {
    // 提示已由拦截器弹出，关闭空弹窗
    dialogVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload = {
    title: form.title.trim(),
    category: form.category,
    content: form.content
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      await addKnowledgeDoc(payload)
      ElMessage.success('新增成功：该文档尚未建立索引，请点击「重建索引」')
    } else {
      const contentChanged =
        !original ||
        original.title !== payload.title ||
        original.category !== payload.category ||
        original.content !== payload.content

      await updateKnowledgeDoc({ id: form.id, ...payload })

      if (contentChanged) {
        ElMessage({
          type: 'warning',
          message: '修改成功：内容已变更，索引状态已重置为未索引，需重建索引后生效',
          duration: 4000
        })
      } else {
        ElMessage.success('修改成功')
      }
    }

    dialogVisible.value = false
    await loadList()
    await loadIndexStatus()
  } catch (error) {
    // 标题重复等业务提示已由拦截器弹出，失败时保持弹窗打开
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.id = null
  form.title = ''
  form.category = ''
  form.content = ''
  original = null
  formRef.value?.clearValidate()
}

// ---------------- 生命周期 ----------------

onMounted(async () => {
  loadList()
  await loadIndexStatus()
  // 进入页面时若上一次重建仍在进行，自动接续轮询
  if (rebuilding.value) {
    startPolling()
  }
})

onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="page-container">
    <!-- 顶部操作栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 150px">
            <el-option v-for="item in CATEGORIES" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="query.enabled" placeholder="全部" clearable style="width: 130px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button type="primary" plain :icon="Plus" @click="handleAdd">新增文档</el-button>
          <el-tooltip :disabled="!rebuilding" content="索引正在重建中，请等待完成" placement="top">
            <span class="reindex-wrapper">
              <el-button
                type="warning"
                :icon="Refresh"
                :loading="reindexLoading"
                :disabled="rebuilding"
                @click="handleReindex"
              >
                重建索引
              </el-button>
            </span>
          </el-tooltip>
        </el-form-item>
      </el-form>

      <el-divider class="divider" />

      <!-- 当前索引状态 -->
      <div v-loading="statusLoading" class="status-strip">
        <span class="status-label">索引状态</span>
        <el-tag :type="indexStatusTag.type" effect="dark" size="small">
          {{ indexStatusTag.text }}
        </el-tag>

        <span class="status-item">文档 <b>{{ counts.totalCount }}</b></span>
        <span class="status-item">启用 <b>{{ counts.enabledCount }}</b></span>
        <span class="status-item is-success">正常 <b>{{ counts.normalCount }}</b></span>
        <span class="status-item is-warning">未索引 <b>{{ counts.notIndexedCount }}</b></span>
        <span class="status-item is-primary">索引中 <b>{{ counts.indexingCount }}</b></span>
        <span class="status-item is-danger">失败 <b>{{ counts.failedCount }}</b></span>

        <span v-if="counts.failedCount > 0" class="status-hint">
          索引失败通常是 RAG 服务未启动（默认 http://localhost:8000），请启动服务后重新点击「重建索引」
        </span>

        <el-button link type="primary" :icon="Refresh" @click="handleRefreshStatus">
          刷新状态
        </el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border stripe empty-text="暂无知识库文档">
        <el-table-column label="序号" type="index" width="70" align="center" />

        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />

        <el-table-column label="分类" width="130" align="center">
          <template #default="{ row }">
            <el-tag type="primary" effect="plain">{{ row.category }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" effect="light">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="索引状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="indexStatusStyle(row).type" effect="light">
              {{ indexStatusStyle(row).text }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>

            <el-tooltip
              :content="row.enabled === 1 ? '点击禁用（禁用后不参与检索）' : '点击启用'"
              placement="top"
            >
              <span class="switch-wrapper">
                <el-switch
                  :model-value="row.enabled === 1"
                  :before-change="() => handleBeforeToggle(row)"
                />
              </span>
            </el-tooltip>

            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        class="pagination"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="720px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        v-loading="detailLoading"
        :model="form"
        :rules="rules"
        label-width="80px"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="请输入文档标题（全局唯一）"
            maxlength="200"
            show-word-limit
            clearable
          />
        </el-form-item>

        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择文档分类" style="width: 100%">
            <el-option v-for="item in CATEGORIES" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="14"
            resize="vertical"
            placeholder="支持 Markdown 纯文本，例如：## 猫咪日常护理 / - 每周梳毛 2~3 次"
            maxlength="20000"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="标题全局唯一；内容按纯文本存储，修改标题 / 分类 / 内容后索引状态会重置为「未索引」，需重建索引才会参与检索。"
      />

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.search-card {
  margin-bottom: 16px;
}

.search-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.divider {
  margin: 16px 0 12px;
}

.reindex-wrapper {
  display: inline-flex;
  margin-left: 12px;
}

.status-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  min-height: 28px;
  font-size: 13px;
  color: #606266;
}

.status-label {
  color: #909399;
}

.status-item b {
  margin-left: 2px;
  color: #303133;
}

.status-item.is-success b {
  color: #67c23a;
}

.status-item.is-warning b {
  color: #e6a23c;
}

.status-item.is-primary b {
  color: #409eff;
}

.status-item.is-danger b {
  color: #f56c6c;
}

.status-hint {
  font-size: 12px;
  color: #f56c6c;
}

.switch-wrapper {
  display: inline-flex;
  align-items: center;
  margin: 0 8px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
