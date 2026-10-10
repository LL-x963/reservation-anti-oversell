<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'

import { addCategory, deleteCategory, listCategories, updateCategory } from '@/api/category'
import { formatDateTime } from '@/utils/format'

/** 列表状态 */
const loading = ref(false)
const list = ref([])

/** 弹窗状态 */
const dialogVisible = ref(false)
const dialogMode = ref('add')
const submitting = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  name: '',
  sort: 0,
  // 修改接口要求 status 必填：编辑时沿用原状态，不在本页面修改
  status: 1
})

const dialogTitle = computed(() => (dialogMode.value === 'add' ? '新增分类' : '编辑分类'))

const rules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '分类名称长度不能超过 50', trigger: 'blur' }
  ],
  sort: [{ required: true, message: '请输入排序值', trigger: 'blur' }]
}

/**
 * 加载全部分类（后端已排序，全量展示不分页）
 */
async function loadList() {
  loading.value = true
  try {
    const data = await listCategories()
    list.value = Array.isArray(data) ? data : []
  } catch (error) {
    // 错误提示已由 utils/request.js 统一弹出
  } finally {
    loading.value = false
  }
}

/**
 * 打开新增弹窗
 */
function handleAdd() {
  dialogMode.value = 'add'
  resetForm()
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗
 */
function handleEdit(row) {
  dialogMode.value = 'edit'
  resetForm()
  form.id = row.id
  form.name = row.name
  form.sort = row.sort ?? 0
  form.status = row.status ?? 1
  dialogVisible.value = true
}

/**
 * 提交新增 / 修改
 */
async function handleSubmit() {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      await addCategory({ name: form.name.trim(), sort: form.sort })
      ElMessage.success('新增成功')
    } else {
      await updateCategory({
        id: form.id,
        name: form.name.trim(),
        sort: form.sort,
        status: form.status
      })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await loadList()
  } catch (error) {
    // 失败（如分类名称重复）时保持弹窗打开，提示已由 request 拦截器弹出
  } finally {
    submitting.value = false
  }
}

/**
 * 删除分类（二次确认）
 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除分类「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    // 用户取消
    return
  }

  try {
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch (error) {
    // 分类下存在服务项目时后端会拒绝删除，提示已由 request 拦截器弹出
  }
}

/**
 * 重置表单
 */
function resetForm() {
  form.id = null
  form.name = ''
  form.sort = 0
  form.status = 1
  formRef.value?.clearValidate()
}

onMounted(loadList)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">服务分类</span>
          <div>
            <el-button :icon="Refresh" @click="loadList">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="handleAdd">新增分类</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border stripe empty-text="暂无分类数据">
        <el-table-column label="序号" type="index" width="70" align="center" />

        <el-table-column prop="name" label="分类名称" min-width="180" show-overflow-tooltip />

        <el-table-column prop="sort" label="排序" width="90" align="center" />

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="180" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑共用弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="分类名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入分类名称"
            maxlength="50"
            show-word-limit
            clearable
          />
        </el-form-item>

        <el-form-item label="排序值" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" :step="1" controls-position="right" />
          <span class="form-tip">数值越小越靠前</span>
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
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
