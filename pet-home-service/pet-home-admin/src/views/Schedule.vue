<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'

import { listOnDutyBeauticians } from '@/api/beautician'
import { batchSetSchedule, deleteSchedule, listSchedules, updateScheduleStatus } from '@/api/schedule'
import { todayString } from '@/utils/format'

/** 固定时段（与后端 TimeSlot 枚举字面值一致） */
const TIME_SLOTS = ['10:00-12:00', '12:00-14:00', '14:00-16:00', '16:00-18:00', '18:00-20:00']

// ---------------- 列表 ----------------

const loading = ref(false)
const list = ref([])
const beauticians = ref([])

/** 查询条件（后端两个参数均为必填） */
const query = reactive({
  beauticianId: null,
  date: todayString()
})

/** 空表格提示 */
const emptyText = computed(() =>
  query.beauticianId ? '该美容师当日暂无排班，可点击「批量排班」设置' : '请选择美容师与日期后查询'
)

/**
 * 加载在职美容师下拉数据
 */
async function loadBeauticians() {
  try {
    const data = await listOnDutyBeauticians()
    beauticians.value = Array.isArray(data) ? data : []
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  }
}

/**
 * 查询排班列表
 */
async function loadList() {
  if (!query.beauticianId || !query.date) {
    list.value = []
    return
  }

  loading.value = true
  try {
    const data = await listSchedules({ beauticianId: query.beauticianId, date: query.date })
    list.value = Array.isArray(data) ? data : []
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  if (!query.beauticianId) {
    ElMessage.warning('请先选择美容师')
    return
  }
  if (!query.date) {
    ElMessage.warning('请先选择日期')
    return
  }
  loadList()
}

/**
 * 可约状态标签：可约绿色、不可约红色、被订单占用额外区分
 */
function statusTag(row) {
  if (row.occupied) {
    return { type: 'danger', text: '已占用' }
  }
  return row.available === 1
    ? { type: 'success', text: '可约' }
    : { type: 'danger', text: '不可约' }
}

// ---------------- 修改状态（开关） ----------------

/**
 * 开关切换前确认；被订单占用的时段禁止直接设为可约
 *
 * @returns {Promise<boolean>} true 才真正切换
 */
async function handleBeforeToggle(row) {
  if (row.occupied) {
    ElMessage.warning('该时段已被订单占用，需先取消或改派订单')
    return false
  }

  const nextAvailable = row.available === 1 ? 0 : 1
  const action = nextAvailable === 1 ? '设为可约' : '设为不可约'

  try {
    await ElMessageBox.confirm(`确定要将 ${row.date} ${row.timeSlot} 时段${action}吗？`, '操作确认', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch {
    return false
  }

  try {
    await updateScheduleStatus(row.id, nextAvailable)
    ElMessage.success(`${action}成功`)
    await loadList()
    return true
  } catch (error) {
    // 失败时保持原状态，提示已由拦截器弹出
    return false
  }
}

// ---------------- 删除 ----------------

async function handleDelete(row) {
  if (row.occupied) {
    ElMessage.warning('该时段已被订单占用，不能删除')
    return
  }

  try {
    await ElMessageBox.confirm(`确定要删除 ${row.date} ${row.timeSlot} 的排班吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }

  try {
    await deleteSchedule(row.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch (error) {
    // 提示已由拦截器弹出
  }
}

// ---------------- 批量排班 ----------------

const dialogVisible = ref(false)
const submitting = ref(false)
const batchFormRef = ref()

const batchForm = reactive({
  beauticianId: null,
  date: todayString(),
  // 默认全选 5 个固定时段
  timeSlots: [...TIME_SLOTS]
})

const batchRules = {
  beauticianId: [{ required: true, message: '请选择美容师', trigger: 'change' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  timeSlots: [
    { required: true, type: 'array', min: 1, message: '请至少选择一个时段', trigger: 'change' }
  ]
}

/** 全选 / 取消全选 */
const allChecked = computed({
  get: () => batchForm.timeSlots.length === TIME_SLOTS.length,
  set: (checked) => {
    batchForm.timeSlots = checked ? [...TIME_SLOTS] : []
  }
})

function handleBatchOpen() {
  batchForm.beauticianId = query.beauticianId || beauticians.value[0]?.id || null
  batchForm.date = query.date || todayString()
  batchForm.timeSlots = [...TIME_SLOTS]
  dialogVisible.value = true
  batchFormRef.value?.clearValidate()
}

async function handleBatchSubmit() {
  if (!batchFormRef.value) {
    return
  }

  const valid = await batchFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    const result = await batchSetSchedule({
      beauticianId: batchForm.beauticianId,
      date: batchForm.date,
      timeSlots: batchForm.timeSlots
    })

    // 后端会返回新增 / 改为可约 / 本来已可约 / 跳过的统计，这里拼成一句可读提示
    const parts = []
    if (result?.addedCount) {
      parts.push(`新增 ${result.addedCount} 个`)
    }
    if (result?.updatedCount) {
      parts.push(`改为可约 ${result.updatedCount} 个`)
    }
    if (result?.unchangedCount) {
      parts.push(`本来已可约 ${result.unchangedCount} 个`)
    }
    ElMessage.success(parts.length ? `排班设置成功：${parts.join('，')}` : '排班设置成功')

    if (result?.skippedCount) {
      const detail = (result.skippedSlots || [])
        .map((item) => `${item.timeSlot}（${item.reason || '已被订单占用'}）`)
        .join('；')
      ElMessage.warning(`${result.skippedCount} 个时段被跳过：${detail}`)
    }

    dialogVisible.value = false
    // 同步查询条件，直接看到本次批量结果
    query.beauticianId = batchForm.beauticianId
    query.date = batchForm.date
    await loadList()
  } catch (error) {
    // 失败时保持弹窗打开，提示已由拦截器弹出
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadBeauticians()
  if (beauticians.value.length) {
    // 默认查询第一个在职美容师当天的排班
    query.beauticianId = beauticians.value[0].id
    await loadList()
  } else {
    ElMessage.warning('暂无在职美容师，请先到「美容师管理」添加')
  }
})
</script>

<template>
  <div class="page-container">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="美容师">
          <el-select v-model="query.beauticianId" placeholder="请选择美容师" style="width: 200px">
            <el-option
              v-for="item in beauticians"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="日期">
          <el-date-picker
            v-model="query.date"
            type="date"
            :editable="false"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            style="width: 170px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button type="primary" plain :icon="Plus" @click="handleBatchOpen">批量排班</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border stripe :empty-text="emptyText">
        <el-table-column label="序号" type="index" width="70" align="center" />

        <el-table-column prop="date" label="日期" width="130" align="center" />

        <el-table-column prop="timeSlot" label="时段" width="140" align="center" />

        <el-table-column label="可约状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row).type" effect="light">{{ statusTag(row).text }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="关联订单" width="200">
          <template #default="{ row }">
            <template v-if="row.orderId">
              <span>{{ row.orderId }}</span>
              <el-tag v-if="row.occupied" type="danger" size="small" effect="plain" class="order-tag">
                已被订单占用
              </el-tag>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-tooltip
              :disabled="!row.occupied"
              content="该时段已被订单占用，需先取消或改派订单"
              placement="top"
            >
              <span class="switch-wrapper">
                <el-switch
                  :model-value="row.available === 1"
                  :disabled="row.occupied"
                  :before-change="() => handleBeforeToggle(row)"
                />
              </span>
            </el-tooltip>
            <el-tooltip
              :disabled="!row.occupied"
              content="该时段已被订单占用，不能删除"
              placement="top"
            >
              <span class="delete-wrapper">
                <el-button link type="danger" :disabled="row.occupied" @click="handleDelete(row)">
                  删除
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 批量排班弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="批量排班"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="batchFormRef" :model="batchForm" :rules="batchRules" label-width="90px">
        <el-form-item label="美容师" prop="beauticianId">
          <el-select v-model="batchForm.beauticianId" placeholder="请选择美容师" style="width: 100%">
            <el-option
              v-for="item in beauticians"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="日期" prop="date">
          <el-date-picker
            v-model="batchForm.date"
            type="date"
            :editable="false"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="时段" prop="timeSlots">
          <div class="slot-wrapper">
            <el-checkbox v-model="allChecked" class="slot-all">全选</el-checkbox>
            <el-checkbox-group v-model="batchForm.timeSlots">
              <el-checkbox v-for="slot in TIME_SLOTS" :key="slot" :value="slot" :label="slot">
                {{ slot }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </el-form-item>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="所选时段将统一设置为「可约」；已被订单占用的时段会自动跳过，不影响其它时段。"
        />
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleBatchSubmit">确定</el-button>
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

.order-tag {
  margin-left: 8px;
}

.slot-wrapper {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.slot-all {
  margin-bottom: 4px;
}

.text-muted {
  font-size: 12px;
  color: #909399;
}

.switch-wrapper,
.delete-wrapper {
  display: inline-flex;
  align-items: center;
  margin: 0 6px;
}
</style>
