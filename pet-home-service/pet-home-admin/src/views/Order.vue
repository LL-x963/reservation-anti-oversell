<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'

import { listOnDutyBeauticians } from '@/api/beautician'
import {
  assignOrder,
  cancelOrder,
  completeOrder,
  confirmOrder,
  getOrderDetail,
  pageOrders
} from '@/api/order'
import { formatAmount, formatDateTime, formatDecimal } from '@/utils/format'

/**
 * 订单状态展示配置（与后端 OrderStatus 枚举一致）
 *
 * Element Plus 只有 5 种 tag 类型，7 个状态用「同色不同深浅」区分：
 * 服务中(浅绿) / 已完成(深绿)，待支付(浅橙) / 待评价(深橙)。
 */
const STATUS_STYLE = {
  1: { text: '待支付', type: 'warning', effect: 'light' },
  2: { text: '待确认', type: 'danger', effect: 'light' },
  3: { text: '待上门', type: 'primary', effect: 'light' },
  4: { text: '服务中', type: 'success', effect: 'light' },
  5: { text: '已完成', type: 'success', effect: 'dark' },
  6: { text: '已取消', type: 'info', effect: 'light' },
  7: { text: '待评价', type: 'warning', effect: 'dark' }
}

/** 状态下拉选项（全部 + 7 个状态） */
const STATUS_OPTIONS = Object.entries(STATUS_STYLE).map(([value, item]) => ({
  value: Number(value),
  label: item.text
}))

/**
 * 状态标签样式；状态值异常时退化为后端描述
 */
function statusStyle(row) {
  return STATUS_STYLE[row?.status] || { text: row?.statusDesc || '-', type: 'info', effect: 'light' }
}

// ---------------- 列表 ----------------

const loading = ref(false)
const list = ref([])
const total = ref(0)
const beauticians = ref([])

const query = reactive({
  status: null,
  // 按「预约日期」筛选（后端参数名 appointmentDate，精确匹配）
  appointmentDate: '',
  beauticianId: null,
  // 后端分页参数名为 page / pageSize（不是 pageNum）
  page: 1,
  pageSize: 10
})

/**
 * 加载在职美容师下拉数据（筛选栏与分配弹窗共用）
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
 * 加载订单分页数据
 */
async function loadList() {
  loading.value = true
  try {
    const data = await pageOrders({
      page: query.page,
      pageSize: query.pageSize,
      status: query.status ?? undefined,
      appointmentDate: query.appointmentDate || undefined,
      beauticianId: query.beauticianId ?? undefined
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
  query.status = null
  query.appointmentDate = ''
  query.beauticianId = null
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

// ---------------- 操作按钮可用性（与后端状态流转校验保持一致） ----------------

/** 确认：仅「待确认」 */
function canConfirm(row) {
  return row.status === 2
}

/** 分配 / 改派：「待确认」「待上门」 */
function canAssign(row) {
  return row.status === 2 || row.status === 3
}

/** 完成：「待上门」「服务中」 */
function canComplete(row) {
  return row.status === 3 || row.status === 4
}

/** 取消：除「已完成」「已取消」外都可取消 */
function canCancel(row) {
  return row.status !== 5 && row.status !== 6
}

// ---------------- 确认 / 完成 / 取消 ----------------

/**
 * 二次确认后执行状态流转操作
 *
 * @param {object} row 订单行
 * @param {string} message 确认文案
 * @param {string} tip 操作中的提示语
 * @param {Function} action 实际请求
 */
async function confirmAndRun(row, message, tip, action) {
  try {
    await ElMessageBox.confirm(message, '操作确认', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch {
    // 用户取消
    return
  }

  try {
    await action(row.id)
    ElMessage.success(tip)
    await loadList()
  } catch (error) {
    // 后端会拒绝非法状态流转，提示已由拦截器弹出
  }
}

function handleConfirm(row) {
  confirmAndRun(
    row,
    `确定要确认订单「${row.orderNumber}」吗？确认后状态变为「待上门」，并会为该预约发送到点提醒。`,
    '确认成功',
    confirmOrder
  )
}

function handleComplete(row) {
  confirmAndRun(
    row,
    `确定要完成订单「${row.orderNumber}」吗？完成后状态变为「待评价」，等待用户评价。`,
    '操作成功',
    completeOrder
  )
}

function handleCancel(row) {
  return confirmAndRun(
    row,
    `确定要取消订单「${row.orderNumber}」吗？取消后订单不可恢复，已占用的排班会被释放。`,
    '取消成功',
    cancelOrder
  )
}

// ---------------- 分配 / 改派美容师 ----------------

const assignVisible = ref(false)
const assignSubmitting = ref(false)
const assignTarget = ref(null)
const assignBeauticianId = ref(null)

const assignTitle = computed(() => (assignTarget.value?.beauticianId ? '改派美容师' : '分配美容师'))

function handleAssignOpen(row) {
  assignTarget.value = row
  // 默认选中当前美容师，便于改派时对比
  assignBeauticianId.value = row.beauticianId || null
  assignVisible.value = true
}

async function handleAssignSubmit() {
  if (!assignTarget.value) {
    return
  }
  if (!assignBeauticianId.value) {
    ElMessage.warning('请选择美容师')
    return
  }
  if (assignBeauticianId.value === assignTarget.value.beauticianId) {
    ElMessage.warning('所选美容师与当前一致，无需修改')
    return
  }

  assignSubmitting.value = true
  try {
    await assignOrder(assignTarget.value.id, assignBeauticianId.value)
    ElMessage.success('操作成功')
    assignVisible.value = false
    await loadList()
  } catch (error) {
    // 失败时保持弹窗打开，提示已由拦截器弹出
  } finally {
    assignSubmitting.value = false
  }
}

// ---------------- 详情 ----------------

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

async function handleDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getOrderDetail(row.id)
  } catch (error) {
    // 提示已由拦截器弹出，关闭空弹窗
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  loadBeauticians()
  loadList()
})
</script>

<template>
  <div class="page-container">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option
              v-for="item in STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="预约日期">
          <el-date-picker
            v-model="query.appointmentDate"
            type="date"
            placeholder="请选择预约日期"
            value-format="yyyy-MM-dd"
            format="yyyy-MM-dd"
            clearable
            style="width: 180px"
          />
        </el-form-item>

        <el-form-item label="美容师">
          <el-select v-model="query.beauticianId" placeholder="全部美容师" clearable style="width: 170px">
            <el-option
              v-for="item in beauticians"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border stripe empty-text="暂无订单数据">
        <el-table-column label="序号" type="index" width="70" align="center" />

        <el-table-column prop="orderNumber" label="订单号" min-width="180" show-overflow-tooltip />

        <el-table-column label="用户" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.userName || (row.userId ? `用户ID ${row.userId}` : '-') }}
          </template>
        </el-table-column>

        <el-table-column label="宠物" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.petName || (row.petId ? `宠物ID ${row.petId}` : '-') }}
          </template>
        </el-table-column>

        <el-table-column label="美容师" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.beauticianName">{{ row.beauticianName }}</span>
            <span v-else class="text-muted">未分配</span>
          </template>
        </el-table-column>

        <el-table-column label="预约日期" width="120" align="center">
          <template #default="{ row }">{{ row.appointmentDate || '-' }}</template>
        </el-table-column>

        <el-table-column prop="timeSlot" label="时段" width="130" align="center" />

        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">￥{{ formatAmount(row.totalAmount) }}</template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusStyle(row).type" :effect="statusStyle(row).effect">
              {{ statusStyle(row).text }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canConfirm(row)" link type="primary" @click="handleConfirm(row)">
              确认
            </el-button>
            <el-button v-if="canAssign(row)" link type="primary" @click="handleAssignOpen(row)">
              {{ row.beauticianId ? '改派' : '分配' }}
            </el-button>
            <el-button v-if="canComplete(row)" link type="primary" @click="handleComplete(row)">
              完成
            </el-button>
            <el-button v-if="canCancel(row)" link type="danger" @click="handleCancel(row)">
              取消
            </el-button>
            <el-button link type="info" @click="handleDetail(row)">详情</el-button>
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

    <!-- 分配 / 改派美容师 -->
    <el-dialog
      v-model="assignVisible"
      :title="assignTitle"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="订单号">
          <span>{{ assignTarget?.orderNumber }}</span>
        </el-form-item>

        <el-form-item label="当前美容师">
          <span :class="{ 'text-muted': !assignTarget?.beauticianName }">
            {{ assignTarget?.beauticianName || '未分配' }}
          </span>
        </el-form-item>

        <el-form-item label="美容师" required>
          <el-select v-model="assignBeauticianId" placeholder="请选择美容师" style="width: 100%">
            <el-option
              v-for="item in beauticians"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="分配只调整订单归属，不释放原排班、也不占用新排班；如需调整可用时段请到「排班管理」处理。"
      />

      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="handleAssignSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 订单详情 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="760px">
      <div v-loading="detailLoading" class="detail-wrapper">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="订单号">{{ detail.orderNumber }}</el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="statusStyle(detail).type" :effect="statusStyle(detail).effect" size="small">
                {{ statusStyle(detail).text }}
              </el-tag>
            </el-descriptions-item>

            <el-descriptions-item label="用户">
              {{ detail.userName || (detail.userId ? `用户ID ${detail.userId}` : '-') }}
            </el-descriptions-item>
            <el-descriptions-item label="宠物">
              {{ detail.petName || (detail.petId ? `宠物ID ${detail.petId}` : '-') }}
            </el-descriptions-item>

            <el-descriptions-item label="美容师">
              <span :class="{ 'text-muted': !detail.beauticianName }">
                {{ detail.beauticianName || '未分配' }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="预约时间">
              {{ detail.appointmentDate }} {{ detail.timeSlot }}
            </el-descriptions-item>

            <el-descriptions-item label="订单金额">
              ￥{{ formatAmount(detail.totalAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="支付时间">
              {{ formatDateTime(detail.payTime) }}
            </el-descriptions-item>

            <el-descriptions-item label="创建时间">
              {{ formatDateTime(detail.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ formatDateTime(detail.updateTime) }}
            </el-descriptions-item>

            <el-descriptions-item label="收货信息" :span="2">
              <template v-if="detail.consignee || detail.address">
                <span>{{ detail.consignee || '-' }}</span>
                <span v-if="detail.consigneePhone" class="consignee-phone">
                  {{ detail.consigneePhone }}
                </span>
                <div>{{ detail.address || '-' }}</div>
              </template>
              <span v-else class="text-muted">地址信息已不存在</span>
            </el-descriptions-item>

            <el-descriptions-item label="备注" :span="2">
              {{ detail.remark || '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">服务明细</div>
          <el-table :data="detail.items || []" border size="small" empty-text="暂无服务明细">
            <el-table-column label="序号" type="index" width="70" align="center" />
            <el-table-column prop="name" label="服务项目" min-width="180" />
            <el-table-column label="单价" width="110" align="right">
              <template #default="{ row }">￥{{ formatAmount(row.price) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" align="center" />
            <el-table-column label="小计" width="110" align="right">
              <template #default="{ row }">￥{{ formatAmount(row.amount) }}</template>
            </el-table-column>
          </el-table>

          <template v-if="detail.comment">
            <div class="section-title">用户评价</div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="评分">
                <el-rate :model-value="Number(detail.comment.score) || 0" disabled />
                <span class="score-text">{{ formatDecimal(detail.comment.score, 1) }} 分</span>
              </el-descriptions-item>
              <el-descriptions-item label="评价内容">
                {{ detail.comment.content || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="评价时间">
                {{ formatDateTime(detail.comment.createTime) }}
              </el-descriptions-item>
            </el-descriptions>
          </template>
        </template>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
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

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.text-muted {
  font-size: 12px;
  color: #909399;
}

.detail-wrapper {
  min-height: 120px;
}

.section-title {
  margin: 16px 0 8px;
  font-weight: 600;
}

.consignee-phone {
  margin-left: 8px;
}

.score-text {
  margin-left: 8px;
  color: #606266;
}
</style>
