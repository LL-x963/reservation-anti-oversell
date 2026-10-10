<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, UploadFilled } from '@element-plus/icons-vue'

import { listCategories } from '@/api/category'
import { uploadImage } from '@/api/file'
import {
  addServiceItem,
  deleteServiceItem,
  pageServiceItems,
  updateServiceItem,
  updateServiceItemStatus
} from '@/api/serviceItem'
import { formatAmount } from '@/utils/format'

/** 适配宠物可选项 */
const PET_TYPES = ['猫', '狗', '通用']

/** 图片大小上限（与后端一致：5MB） */
const MAX_IMAGE_SIZE = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']

// ---------------- 列表 ----------------

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  categoryId: null,
  name: '',
  status: null,
  // 后端分页参数名为 page / pageSize（不是 pageNum）
  page: 1,
  pageSize: 10
})

/** 所属分类（下拉 + 表格里 id 转名称） */
const categories = ref([])
const categoryNameMap = computed(() => {
  const map = {}
  categories.value.forEach((item) => {
    map[item.id] = item.name
  })
  return map
})

/**
 * 加载分类下拉数据
 */
async function loadCategories() {
  try {
    const data = await listCategories()
    categories.value = Array.isArray(data) ? data : []
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  }
}

/**
 * 加载服务项目分页数据
 */
async function loadList() {
  loading.value = true
  try {
    const data = await pageServiceItems({
      page: query.page,
      pageSize: query.pageSize,
      categoryId: query.categoryId || undefined,
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

/**
 * 搜索：回到第一页
 */
function handleSearch() {
  query.page = 1
  loadList()
}

/**
 * 重置搜索条件
 */
function handleReset() {
  query.categoryId = null
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

// ---------------- 新增 / 编辑弹窗 ----------------

const dialogVisible = ref(false)
const dialogMode = ref('add')
const submitting = ref(false)
const uploading = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  categoryId: null,
  name: '',
  price: 0,
  serviceDuration: null,
  petType: '',
  suitWeight: '',
  description: '',
  image: ''
})

const dialogTitle = computed(() => (dialogMode.value === 'add' ? '新增服务项目' : '编辑服务项目'))

const rules = {
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  name: [
    { required: true, message: '请输入服务名称', trigger: 'blur' },
    { max: 100, message: '服务名称长度不能超过 100', trigger: 'blur' }
  ],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  serviceDuration: [{ required: true, message: '请输入服务时长', trigger: 'blur' }],
  petType: [{ required: true, message: '请选择适配宠物', trigger: 'change' }],
  suitWeight: [{ max: 50, message: '适配体重长度不能超过 50', trigger: 'blur' }],
  description: [{ max: 1000, message: '描述长度不能超过 1000', trigger: 'blur' }],
  image: [
    {
      pattern: /^(https?:\/\/|\/).+/,
      message: '图片地址必须以 http(s):// 或 / 开头',
      trigger: 'blur'
    }
  ]
}

/** 打开新增弹窗 */
function handleAdd() {
  dialogMode.value = 'add'
  resetForm()
  dialogVisible.value = true
}

/** 打开编辑弹窗 */
function handleEdit(row) {
  dialogMode.value = 'edit'
  resetForm()
  form.id = row.id
  form.categoryId = row.categoryId ?? null
  form.name = row.name || ''
  form.price = row.price === null || row.price === undefined ? 0 : Number(row.price)
  form.serviceDuration = row.serviceDuration ?? null
  form.petType = row.petType || ''
  form.suitWeight = row.suitWeight || ''
  form.description = row.description || ''
  form.image = row.image || ''
  dialogVisible.value = true
}

/** 提交新增 / 修改 */
async function handleSubmit() {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload = {
    categoryId: form.categoryId,
    name: form.name.trim(),
    price: form.price,
    serviceDuration: form.serviceDuration,
    petType: form.petType,
    suitWeight: form.suitWeight?.trim() || null,
    description: form.description?.trim() || null,
    image: form.image?.trim() || null
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      // 不传 status：后端默认上架，之后可用列表开关调整
      await addServiceItem(payload)
      ElMessage.success('新增成功')
    } else {
      await updateServiceItem({ id: form.id, ...payload })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await loadList()
  } catch (error) {
    // 失败（如名称重复、分类不存在）时保持弹窗打开，提示已由拦截器弹出
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.id = null
  form.categoryId = null
  form.name = ''
  form.price = 0
  form.serviceDuration = null
  form.petType = ''
  form.suitWeight = ''
  form.description = ''
  form.image = ''
  formRef.value?.clearValidate()
}

// ---------------- 图片上传 ----------------

/** 上传前校验类型与大小 */
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

/** 自定义上传：走统一的 axios（自动带 token），成功后回填地址 */
async function handleImageUpload(options) {
  uploading.value = true
  try {
    const url = await uploadImage(options.file)
    form.image = url
    ElMessage.success('图片上传成功')
    options.onSuccess?.(url)
  } catch (error) {
    options.onError?.(error)
  } finally {
    uploading.value = false
  }
}

// ---------------- 上下架 / 删除 ----------------

/**
 * 上下架前确认，并把结果交给 el-switch 的 before-change
 *
 * @returns {Promise<boolean>} true 才真正切换开关状态
 */
async function handleBeforeStatusChange(row) {
  const nextStatus = row.status === 1 ? 0 : 1
  const action = nextStatus === 1 ? '上架' : '下架'

  try {
    await ElMessageBox.confirm(`确定要${action}服务「${row.name}」吗？`, '操作确认', {
      type: 'warning',
      confirmButtonText: `确定${action}`,
      cancelButtonText: '取消'
    })
  } catch {
    return false
  }

  try {
    await updateServiceItemStatus(row.id, nextStatus)
    ElMessage.success(`${action}成功`)
    await loadList()
    return true
  } catch (error) {
    // 失败时保持原状态，提示已由拦截器弹出
    return false
  }
}

/** 删除（二次确认） */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除服务「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }

  try {
    await deleteServiceItem(row.id)
    ElMessage.success('删除成功')
    // 删除后当前页可能已为空，回退一页
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    await loadList()
  } catch (error) {
    // 存在未完成订单时后端会拒绝，提示已由拦截器弹出
  }
}

onMounted(() => {
  loadCategories()
  loadList()
})
</script>

<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="所属分类">
          <el-select
            v-model="query.categoryId"
            placeholder="全部分类"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="item in categories"
              :key="item.id"
              :label="item.status === 1 ? item.name : `${item.name}（禁用）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="服务名称">
          <el-input
            v-model="query.name"
            placeholder="请输入服务名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="primary" plain :icon="Plus" @click="handleAdd">新增服务</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border stripe empty-text="暂无服务项目">
        <el-table-column label="序号" type="index" width="70" align="center" />

        <el-table-column label="图片" width="90" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.image"
              :src="row.image"
              :preview-src-list="[row.image]"
              preview-teleported
              fit="cover"
              class="table-image"
            />
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="服务名称" min-width="160" show-overflow-tooltip />

        <el-table-column label="所属分类" width="140">
          <template #default="{ row }">
            {{ categoryNameMap[row.categoryId] || '-' }}
          </template>
        </el-table-column>

        <el-table-column label="价格" width="110" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.price) }}</template>
        </el-table-column>

        <el-table-column label="时长" width="100" align="center">
          <template #default="{ row }">
            {{ row.serviceDuration ? `${row.serviceDuration} 分钟` : '-' }}
          </template>
        </el-table-column>

        <el-table-column label="适配宠物" width="140">
          <template #default="{ row }">
            <div>{{ row.petType || '-' }}</div>
            <div v-if="row.suitWeight" class="text-muted">{{ row.suitWeight }}</div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :before-change="() => handleBeforeStatusChange(row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="handleBeforeStatusChange(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
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
      width="620px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择所属分类" style="width: 100%">
            <el-option
              v-for="item in categories"
              :key="item.id"
              :label="item.status === 1 ? item.name : `${item.name}（禁用）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="服务名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入服务名称" maxlength="100" show-word-limit />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="价格(元)" prop="price">
              <el-input-number
                v-model="form.price"
                :min="0.01"
                :max="99999999"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="时长(分钟)" prop="serviceDuration">
              <el-input-number
                v-model="form.serviceDuration"
                :min="1"
                :max="1440"
                :step="10"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="适配宠物" prop="petType">
              <el-select v-model="form.petType" placeholder="请选择适配宠物" style="width: 100%">
                <el-option v-for="type in PET_TYPES" :key="type" :label="type" :value="type" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适配体重" prop="suitWeight">
              <el-input v-model="form.suitWeight" placeholder="如：5kg 以下" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入服务描述"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="图片地址" prop="image">
          <div class="image-row">
            <el-input v-model="form.image" placeholder="请输入图片地址，或点击右侧按钮上传" clearable />
            <el-upload
              :show-file-list="false"
              :before-upload="beforeImageUpload"
              :http-request="handleImageUpload"
              accept=".jpg,.jpeg,.png,.gif,.webp"
            >
              <el-button :icon="UploadFilled" :loading="uploading">上传图片</el-button>
            </el-upload>
          </div>
        </el-form-item>

        <el-form-item v-if="form.image" label="图片预览">
          <el-image
            :src="form.image"
            :preview-src-list="[form.image]"
            preview-teleported
            fit="cover"
            class="form-image"
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

.table-image {
  width: 46px;
  height: 46px;
  border-radius: 4px;
}

.form-image {
  width: 90px;
  height: 90px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.image-row {
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
