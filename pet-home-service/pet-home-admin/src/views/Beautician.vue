<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, UploadFilled, UserFilled } from '@element-plus/icons-vue'

import { addBeautician, deleteBeautician, pageBeauticians, updateBeautician } from '@/api/beautician'
import { uploadImage } from '@/api/file'
import { formatDecimal } from '@/utils/format'

/** 图片大小上限（与后端一致：5MB） */
const MAX_IMAGE_SIZE = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
/** 技能标签数量上限（与后端一致：20 个） */
const MAX_SKILL_TAGS = 20

// ---------------- 列表 ----------------

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  name: '',
  status: null,
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
    const data = await pageBeauticians({
      page: query.page,
      pageSize: query.pageSize,
      name: query.name?.trim() || undefined,
      status: query.status === null || query.status === '' ? undefined : query.status
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

function handleReset() {
  query.name = ''
  query.status = null
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

// ---------------- 技能标签辅助 ----------------

/**
 * 逗号分隔文本 → 标签数组（兼容中英文逗号，去空、去重）
 */
function splitTags(text) {
  if (!text) {
    return []
  }
  const tags = []
  String(text)
    .split(/[,，]/)
    .forEach((item) => {
      const tag = item.trim()
      if (tag && !tags.includes(tag)) {
        tags.push(tag)
      }
    })
  return tags
}

/**
 * 后端返回的技能标签（数组）→ 用于展示的标签数组
 */
function rowTags(value) {
  if (Array.isArray(value)) {
    return value.filter(Boolean)
  }
  return splitTags(value)
}

// ---------------- 新增 / 编辑弹窗 ----------------

const dialogVisible = ref(false)
const dialogMode = ref('add')
const submitting = ref(false)
const uploading = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  name: '',
  avatar: '',
  // 输入框以逗号分隔字符串填写，提交时拆成数组
  skillTags: '',
  serviceRange: '',
  entryTime: '',
  // 修改接口要求 status 必填：编辑时沿用原状态（本页面不修改在职状态）
  status: 1
})

const dialogTitle = computed(() => (dialogMode.value === 'add' ? '新增美容师' : '编辑美容师'))

const rules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名长度不能超过 50', trigger: 'blur' }
  ],
  avatar: [
    { max: 255, message: '头像地址长度不能超过 255', trigger: 'blur' },
    {
      pattern: /^(https?:\/\/|\/).+/,
      message: '头像地址必须以 http(s):// 或 / 开头',
      trigger: 'blur'
    }
  ],
  serviceRange: [{ max: 255, message: '服务范围长度不能超过 255', trigger: 'blur' }],
  skillTags: [
    {
      validator: (rule, value, callback) => {
        const tags = splitTags(value)
        if (tags.length > MAX_SKILL_TAGS) {
          return callback(new Error(`技能标签数量不能超过 ${MAX_SKILL_TAGS} 个`))
        }
        if (tags.some((tag) => tag.length > 50)) {
          return callback(new Error('单个技能标签长度不能超过 50'))
        }
        return callback()
      },
      trigger: 'blur'
    }
  ]
}

/** 禁止选择未来日期（后端校验入职时间不能晚于今天） */
function disabledFutureDate(date) {
  return date.getTime() > Date.now()
}

function handleAdd() {
  dialogMode.value = 'add'
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogMode.value = 'edit'
  resetForm()
  form.id = row.id
  form.name = row.name || ''
  form.avatar = row.avatar || ''
  // 数组 → 逗号分隔字符串
  form.skillTags = rowTags(row.skillTags).join(',')
  form.serviceRange = row.serviceRange || ''
  form.entryTime = row.entryTime || ''
  form.status = row.status ?? 1
  dialogVisible.value = true
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
    name: form.name.trim(),
    avatar: form.avatar?.trim() || null,
    // 逗号分隔字符串 → 数组（服务端会去重、去空后按逗号拼接存储）
    skillTags: splitTags(form.skillTags),
    serviceRange: form.serviceRange?.trim() || null,
    entryTime: form.entryTime || null
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      // 不传 status：后端默认 1 在职；score 由后端固定 5.00
      await addBeautician(payload)
      ElMessage.success('新增成功')
    } else {
      await updateBeautician({ id: form.id, status: form.status, ...payload })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await loadList()
  } catch (error) {
    // 失败时保持弹窗打开，提示已由拦截器弹出
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.id = null
  form.name = ''
  form.avatar = ''
  form.skillTags = ''
  form.serviceRange = ''
  form.entryTime = ''
  form.status = 1
  formRef.value?.clearValidate()
}

// ---------------- 头像上传 ----------------

function beforeImageUpload(file) {
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    ElMessage.error('仅支持 jpg / png / gif / webp 格式的图片')
    return false
  }
  if (file.size > MAX_IMAGE_SIZE) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

async function handleImageUpload(options) {
  uploading.value = true
  try {
    const url = await uploadImage(options.file)
    form.avatar = url
    ElMessage.success('头像上传成功')
    options.onSuccess?.(url)
  } catch (error) {
    options.onError?.(error)
  } finally {
    uploading.value = false
  }
}

// ---------------- 删除 ----------------

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除美容师「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }

  try {
    await deleteBeautician(row.id)
    ElMessage.success('删除成功')
    // 删除当前页最后一条时回退一页
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    await loadList()
  } catch (error) {
    // 存在未完成订单时后端会拒绝，提示已由拦截器弹出
  }
}

onMounted(loadList)
</script>

<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="姓名">
          <el-input
            v-model="query.name"
            placeholder="请输入姓名"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="在职" :value="1" />
            <el-option label="离职" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="primary" plain :icon="Plus" @click="handleAdd">新增美容师</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border stripe empty-text="暂无美容师数据">
        <el-table-column label="序号" type="index" width="70" align="center" />

        <el-table-column label="姓名" min-width="160">
          <template #default="{ row }">
            <div class="name-cell">
              <el-avatar :size="28" :src="row.avatar || undefined">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="技能标签" min-width="200">
          <template #default="{ row }">
            <template v-if="rowTags(row.skillTags).length">
              <el-tag
                v-for="tag in rowTags(row.skillTags)"
                :key="tag"
                class="skill-tag"
                type="primary"
                effect="light"
              >
                {{ tag }}
              </el-tag>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="serviceRange" label="服务范围" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.serviceRange || '-' }}</template>
        </el-table-column>

        <el-table-column label="入职日期" width="130" align="center">
          <template #default="{ row }">{{ row.entryTime || '-' }}</template>
        </el-table-column>

        <el-table-column label="评分" width="90" align="center">
          <template #default="{ row }">{{ formatDecimal(row.score, 1) }}</template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '在职' : '离职' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
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
      width="600px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="头像地址" prop="avatar">
          <div class="avatar-row">
            <el-input v-model="form.avatar" placeholder="请输入头像地址，或点击右侧按钮上传" clearable />
            <el-upload
              :show-file-list="false"
              :before-upload="beforeImageUpload"
              :http-request="handleImageUpload"
              accept=".jpg,.jpeg,.png,.gif,.webp"
            >
              <el-button :icon="UploadFilled" :loading="uploading">上传头像</el-button>
            </el-upload>
          </div>
        </el-form-item>

        <el-form-item v-if="form.avatar" label="头像预览">
          <el-avatar :size="80" :src="form.avatar">
            <el-icon :size="28"><UserFilled /></el-icon>
          </el-avatar>
        </el-form-item>

        <el-form-item label="技能标签" prop="skillTags">
          <el-input
            v-model="form.skillTags"
            placeholder="多个标签用英文逗号分隔，如：洗护,美容,驱虫"
            maxlength="500"
            clearable
          />
        </el-form-item>

        <el-form-item label="服务范围" prop="serviceRange">
          <el-input
            v-model="form.serviceRange"
            placeholder="如：朝阳区、海淀区"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="入职日期" prop="entryTime">
          <el-date-picker
            v-model="form.entryTime"
            type="date"
            :editable="false"
            placeholder="请选择入职日期"
            value-format="yyyy-MM-dd"
            format="yyyy-MM-dd"
            :disabled-date="disabledFutureDate"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

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

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.skill-tag {
  margin: 2px 6px 2px 0;
}

.avatar-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.text-muted {
  font-size: 12px;
  color: #909399;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
