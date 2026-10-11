<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

import { addToCart } from '@/api/cart'
import {
  cancelOrder,
  commentOrder,
  deleteOrder,
  getUserOrderDetail,
  pageUserOrders,
  payOrder,
  remindOrder
} from '@/api/order'
import { formatPrice } from '@/utils/format'

/**
 * 我的订单
 *
 * <p>接口：GET /api/user/order/page（参数 status / page / pageSize，注意不是 pageNum）；
 * 列表项自带服务明细 items（下单快照：name / price / quantity / amount）。</p>
 *
 * <p>状态：1待支付 2待确认 3待上门 4服务中 5已完成 6已取消 7待评价。</p>
 */

/** 状态 Tab（按需求列出，服务中/待评价通过状态标签与按钮体现） */
const STATUS_TABS = [
  { key: '', label: '全部' },
  { key: 1, label: '待支付' },
  { key: 2, label: '待确认' },
  { key: 3, label: '待上门' },
  { key: 5, label: '已完成' },
  { key: 6, label: '已取消' }
]

/**
 * 状态标签样式：type 决定色系，effect 区分同色系的不同状态
 * （待支付浅橙 / 待确认红 / 待上门蓝 / 服务中浅绿 / 已完成深绿 / 已取消灰 / 待评价深橙）
 */
const STATUS_TAGS = {
  1: { type: 'warning', effect: 'light' },
  2: { type: 'danger', effect: 'light' },
  3: { type: 'primary', effect: 'light' },
  4: { type: 'success', effect: 'light' },
  5: { type: 'success', effect: 'dark' },
  6: { type: 'info', effect: 'light' },
  7: { type: 'warning', effect: 'dark' }
}

/** 每页条数（下滑加载更多） */
const PAGE_SIZE = 10

/** 备注等长文本在详情里的展示上限（超出截断，避免弹层过长） */
const MAX_COMMENT_LENGTH = 500

const router = useRouter()

const activeStatus = ref('')
const orders = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const loadingMore = ref(false)
/** 正在处理操作的订单 id（用于禁用该卡片按钮） */
const pendingId = ref(null)

const finished = computed(() => orders.value.length >= total.value)

/** 订单详情弹层 */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

/** 评价弹窗 */
const commentVisible = ref(false)
const commentSubmitting = ref(false)
const commentTarget = ref(null)
const commentForm = ref({ score: 5, content: '' })

const sentinelRef = ref()
let observer = null

onMounted(async () => {
  await reload()
  observeSentinel()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})

// 切换状态 Tab：回到第 1 页重新加载
watch(activeStatus, reload)

async function reload() {
  loading.value = true
  page.value = 1
  try {
    const data = await pageUserOrders(buildParams(1))
    orders.value = data?.records || []
    total.value = data?.total || 0
  } catch (e) {
    orders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || finished.value) {
    return
  }
  loadingMore.value = true
  const next = page.value + 1
  try {
    const data = await pageUserOrders(buildParams(next))
    orders.value.push(...(data?.records || []))
    total.value = data?.total ?? total.value
    page.value = next
  } catch (e) {
    // 保留已加载内容，提示由拦截器统一弹出
  } finally {
    loadingMore.value = false
  }
}

function buildParams(targetPage) {
  const params = { page: targetPage, pageSize: PAGE_SIZE }
  if (activeStatus.value !== '') {
    params.status = activeStatus.value
  }
  return params
}

/** 哨兵元素进入视口即加载下一页 */
function observeSentinel() {
  if (!sentinelRef.value || typeof IntersectionObserver === 'undefined') {
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) {
        loadMore()
      }
    },
    { rootMargin: '100px' }
  )
  observer.observe(sentinelRef.value)
}

function statusTag(status) {
  return STATUS_TAGS[status] || { type: 'info', effect: 'light' }
}

/** 服务明细摘要：名称 ×数量 */
function itemSummary(order) {
  const items = order.items || []
  if (!items.length) {
    return '服务明细加载中'
  }
  return items.map((item) => `${item.name} ×${item.quantity}`).join('、')
}

function goServiceList() {
  router.push('/service')
}

/** 点击卡片查看详情 */
async function openDetail(order) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getUserOrderDetail(order.id)
  } catch (e) {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

/** 支付（模拟） */
async function handlePay(order) {
  try {
    await ElMessageBox.confirm(`确认支付订单 ${order.orderNumber} 吗？`, '确认支付', {
      confirmButtonText: '确认支付',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  pendingId.value = order.id
  try {
    await payOrder(order.id)
    ElMessage.success('支付成功')
    await reload()
  } catch (e) {
    // 提示由拦截器统一弹出
  } finally {
    pendingId.value = null
  }
}

/** 取消订单（先确认） */
async function handleCancel(order) {
  try {
    await ElMessageBox.confirm('取消后该时段将被释放，确定取消这笔订单吗？', '取消订单', {
      confirmButtonText: '确定取消',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  pendingId.value = order.id
  try {
    await cancelOrder(order.id)
    ElMessage.success('订单已取消')
    await reload()
  } catch (e) {
    // 提示由拦截器统一弹出
  } finally {
    pendingId.value = null
  }
}

/** 催单 */
async function handleRemind(order) {
  pendingId.value = order.id
  try {
    await remindOrder(order.id)
    ElMessage.success('已催单，美容师会尽快确认')
    await reload()
  } catch (e) {
    // 超频会返回 bizCode 603，提示由拦截器统一弹出
  } finally {
    pendingId.value = null
  }
}

/** 删除已取消订单（先确认） */
async function handleDelete(order) {
  try {
    await ElMessageBox.confirm('删除后订单记录将无法恢复，确定删除吗？', '删除订单', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  pendingId.value = order.id
  try {
    await deleteOrder(order.id)
    ElMessage.success('订单已删除')
    await reload()
  } catch (e) {
    // 提示由拦截器统一弹出
  } finally {
    pendingId.value = null
  }
}

/** 去评价 */
function openComment(order) {
  commentTarget.value = order
  commentForm.value = { score: 5, content: '' }
  commentVisible.value = true
}

async function submitComment() {
  if (!commentTarget.value) {
    return
  }
  commentSubmitting.value = true
  try {
    await commentOrder({
      orderId: commentTarget.value.id,
      score: commentForm.value.score,
      content: commentForm.value.content.trim() || null
    })
    ElMessage.success('评价成功，感谢你的反馈')
    commentVisible.value = false
    await reload()
  } catch (e) {
    // 提示由拦截器统一弹出
  } finally {
    commentSubmitting.value = false
  }
}

/**
 * 再次预约：把该订单的服务重新加入预约车，再进提交预约页
 * （服务已下架时后端会拒绝，这里跳过并提示）
 */
async function handleReorder(order) {
  const items = order.items || []
  if (!items.length) {
    ElMessage.warning('该订单没有服务明细，无法再次预约')
    return
  }

  const serviceIds = []
  let failed = 0
  for (const item of items) {
    try {
      await addToCart(item.serviceId, item.quantity || 1)
      serviceIds.push(item.serviceId)
    } catch (e) {
      failed += 1
    }
  }

  if (!serviceIds.length) {
    ElMessage.error('相关服务已下架，无法再次预约')
    return
  }
  if (failed > 0) {
    ElMessage.warning(`${failed} 项服务已下架，已带入其余 ${serviceIds.length} 项`)
  }
  router.push({ path: '/submit-order', query: { serviceIds: serviceIds.join(',') } })
}
</script>

<template>
  <div class="order h5-page">
    <!-- 1. 状态 Tab 栏（吸顶） -->
    <div class="order__tabs">
      <span
        v-for="tab in STATUS_TABS"
        :key="tab.key"
        class="order__tab h5-tap"
        :class="{ 'is-active': activeStatus === tab.key }"
        @click="activeStatus = tab.key"
      >
        {{ tab.label }}
      </span>
    </div>

    <!-- 首屏骨架 -->
    <div v-if="loading" class="order__list">
      <div v-for="n in 2" :key="n" class="order__card order__card--skeleton">
        <div class="order__line"></div>
        <div class="order__line order__line--short"></div>
      </div>
    </div>

    <!-- 空状态 -->
    <el-empty v-else-if="!orders.length" :image-size="90" description="暂无订单，快去挑选服务吧">
      <el-button type="primary" size="small" round @click="goServiceList">去挑服务</el-button>
    </el-empty>

    <!-- 2. 订单列表 -->
    <div v-else class="order__list">
      <article
        v-for="order in orders"
        :key="order.id"
        class="order__card h5-tap"
        @click="openDetail(order)"
      >
        <!-- 订单号 + 状态 -->
        <div class="order__head">
          <span class="order__no">订单号 {{ order.orderNumber }}</span>
          <el-tag
            size="small"
            :type="statusTag(order.status).type"
            :effect="statusTag(order.status).effect"
          >
            {{ order.statusDesc }}
          </el-tag>
        </div>

        <!-- 服务名称、数量 -->
        <p class="order__items">{{ itemSummary(order) }}</p>

        <!-- 预约时间 -->
        <p class="order__time">
          <el-icon><Clock /></el-icon>
          预约：{{ order.appointmentDate }} {{ order.timeSlot }}
        </p>

        <!-- 金额 -->
        <div class="order__amount-row">
          <span class="order__amount-label">合计</span>
          <span class="order__amount">{{ formatPrice(order.totalAmount) }}</span>
        </div>

        <!-- 底部操作按钮（按状态显示） -->
        <div class="order__actions" @click.stop>
          <!-- 待支付：去支付、取消订单 -->
          <template v-if="order.status === 1">
            <el-button size="small" round :loading="pendingId === order.id" @click="handleCancel(order)">
              取消订单
            </el-button>
            <el-button size="small" type="primary" round :loading="pendingId === order.id" @click="handlePay(order)">
              去支付
            </el-button>
          </template>

          <!-- 待确认：取消订单 -->
          <template v-else-if="order.status === 2">
            <el-button size="small" round :loading="pendingId === order.id" @click="handleCancel(order)">
              取消订单
            </el-button>
          </template>

          <!-- 待上门 / 服务中：取消订单（服务中不可取消，仅催单） + 催单 -->
          <template v-else-if="order.status === 3 || order.status === 4">
            <el-button
              v-if="order.status === 3"
              size="small"
              round
              :loading="pendingId === order.id"
              @click="handleCancel(order)"
            >
              取消订单
            </el-button>
            <el-button size="small" round :loading="pendingId === order.id" @click="handleRemind(order)">
              催单
            </el-button>
          </template>

          <!-- 已完成：去评价、再次预约 -->
          <template v-else-if="order.status === 5">
            <el-button size="small" round @click="handleReorder(order)">再次预约</el-button>
            <el-button size="small" type="primary" round @click="openComment(order)">去评价</el-button>
          </template>

          <!-- 待评价：去评价、再次预约 -->
          <template v-else-if="order.status === 7">
            <el-button size="small" round @click="handleReorder(order)">再次预约</el-button>
            <el-button size="small" type="primary" round @click="openComment(order)">去评价</el-button>
          </template>

          <!-- 已取消：删除、再次预约 -->
          <template v-else-if="order.status === 6">
            <el-button size="small" round :loading="pendingId === order.id" @click="handleDelete(order)">
              删除
            </el-button>
            <el-button size="small" type="primary" round @click="handleReorder(order)">再次预约</el-button>
          </template>

          <!-- 兜底：仅再次预约 -->
          <template v-else>
            <el-button size="small" round @click="handleReorder(order)">再次预约</el-button>
          </template>
        </div>
      </article>
    </div>

    <!-- 3. 加载更多哨兵 -->
    <div ref="sentinelRef" class="order__sentinel">
      <span v-if="loadingMore">正在加载…</span>
      <span v-else-if="orders.length && finished">已全部加载（共 {{ total }} 笔）</span>
    </div>

    <!-- 4. 订单详情（简化弹层） -->
    <el-drawer v-model="detailVisible" title="订单详情" direction="btt" size="72%">
      <div v-loading="detailLoading" class="order__detail">
        <template v-if="detail">
          <div class="order__detail-head">
            <span class="order__detail-no">{{ detail.orderNumber }}</span>
            <el-tag size="small" :type="statusTag(detail.status).type" :effect="statusTag(detail.status).effect">
              {{ detail.statusDesc }}
            </el-tag>
          </div>

          <div class="order__detail-rows">
            <div class="order__detail-row">
              <span class="order__detail-label">预约时间</span>
              <span>{{ detail.appointmentDate }} {{ detail.timeSlot }}</span>
            </div>
            <div class="order__detail-row">
              <span class="order__detail-label">服务宠物</span>
              <span>{{ detail.petName || '-' }}</span>
            </div>
            <div class="order__detail-row">
              <span class="order__detail-label">美容师</span>
              <span>{{ detail.beauticianName || '待分配' }}</span>
            </div>
            <div class="order__detail-row">
              <span class="order__detail-label">服务地址</span>
              <span>{{ detail.address || '地址信息已不存在' }}</span>
            </div>
            <div v-if="detail.consignee" class="order__detail-row">
              <span class="order__detail-label">联系人</span>
              <span>{{ detail.consignee }} {{ detail.consigneePhone }}</span>
            </div>
            <div class="order__detail-row">
              <span class="order__detail-label">下单时间</span>
              <span>{{ detail.createTime }}</span>
            </div>
            <div v-if="detail.remark" class="order__detail-row">
              <span class="order__detail-label">备注</span>
              <span>{{ detail.remark }}</span>
            </div>
          </div>

          <div class="order__detail-section">服务明细</div>
          <div class="order__detail-items">
            <div v-for="(item, index) in detail.items || []" :key="index" class="order__detail-item">
              <span class="order__detail-item-name">{{ item.name }}</span>
              <span class="order__detail-item-qty">×{{ item.quantity }}</span>
              <span class="order__detail-item-amount">{{ formatPrice(item.amount) }}</span>
            </div>
          </div>

          <div class="order__detail-total">
            <span>合计</span>
            <span class="order__amount">{{ formatPrice(detail.totalAmount) }}</span>
          </div>

          <p v-if="detail.payTime" class="order__detail-pay">支付时间：{{ detail.payTime }}</p>

          <template v-if="detail.comment">
            <div class="order__detail-section">我的评价</div>
            <div class="order__comment">
              <el-rate :model-value="detail.comment.score" disabled size="small" />
              <p class="order__comment-content">{{ detail.comment.content || '未填写评价内容' }}</p>
              <p class="order__comment-time">{{ detail.comment.createTime }}</p>
            </div>
          </template>
        </template>
      </div>
    </el-drawer>

    <!-- 5. 评价弹窗 -->
    <el-dialog v-model="commentVisible" title="评价服务" width="86%">
      <div class="order__comment-form">
        <p class="order__comment-label">服务评分</p>
        <el-rate v-model="commentForm.score" />
        <p class="order__comment-label">评价内容</p>
        <el-input
          v-model="commentForm.content"
          type="textarea"
          :rows="3"
          resize="none"
          :maxlength="MAX_COMMENT_LENGTH"
          show-word-limit
          placeholder="说说这次服务体验（选填）"
        />
      </div>
      <template #footer>
        <el-button round @click="commentVisible = false">取消</el-button>
        <el-button type="primary" round :loading="commentSubmitting" @click="submitComment">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ---------------- 状态 Tab ---------------- */
.order__tabs {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  /* 抵消页面内边距，吸顶时铺满整行 */
  margin: 0 calc(-1 * var(--h5-page-padding)) 10px;
  padding: 8px var(--h5-page-padding);
  overflow-x: auto;
  background-color: #f5f6f8;
}

.order__tabs::-webkit-scrollbar {
  display: none;
}

.order__tab {
  position: relative;
  flex: 0 0 auto;
  padding: 4px 12px;
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}

.order__tab.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.order__tab.is-active::after {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 20px;
  height: 2px;
  background-color: var(--el-color-primary);
  border-radius: 1px;
  transform: translateX(-50%);
  content: '';
}

/* ---------------- 订单卡片 ---------------- */
.order__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.order__card {
  padding: 12px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.order__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.order__no {
  overflow: hidden;
  font-size: 12px;
  color: #909399;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order__items {
  display: -webkit-box;
  margin-top: 8px;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.5;
  color: #303133;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.order__time {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.order__amount-row {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.order__amount {
  margin-left: 4px;
  font-size: 16px;
  font-weight: 600;
  color: #f56c6c;
}

.order__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px solid #f5f6f8;
}

.order__actions :deep(.el-button) {
  margin-left: 0;
}

/* 骨架 */
.order__card--skeleton {
  box-shadow: none;
}

.order__line {
  height: 13px;
  margin-bottom: 10px;
  background-color: #f0f2f5;
  border-radius: 6px;
}

.order__line--short {
  width: 55%;
}

/* ---------------- 加载更多 ---------------- */
.order__sentinel {
  padding: 14px 0;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

/* ---------------- 详情弹层 ---------------- */
.order__detail {
  min-height: 120px;
}

.order__detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f5f6f8;
}

.order__detail-no {
  font-size: 13px;
  color: #606266;
}

.order__detail-rows {
  padding: 10px 0;
}

.order__detail-row {
  display: flex;
  align-items: flex-start;
  min-height: 28px;
  font-size: 13px;
  color: #303133;
}

.order__detail-label {
  flex: 0 0 72px;
  color: #909399;
}

.order__detail-section {
  padding-top: 10px;
  margin-top: 4px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  border-top: 1px solid #f5f6f8;
}

.order__detail-items {
  margin-top: 8px;
}

.order__detail-item {
  display: flex;
  align-items: center;
  padding: 6px 0;
  font-size: 13px;
}

.order__detail-item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order__detail-item-qty {
  margin-left: 8px;
  color: #909399;
}

.order__detail-item-amount {
  width: 76px;
  margin-left: 8px;
  color: #606266;
  text-align: right;
}

.order__detail-total {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  padding-top: 10px;
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
  border-top: 1px solid #f5f6f8;
}

.order__detail-pay {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

/* 评价展示 */
.order__comment {
  padding: 8px 0;
}

.order__comment-content {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}

.order__comment-time {
  margin-top: 4px;
  font-size: 12px;
  color: #c0c4cc;
}

/* 评价表单 */
.order__comment-form {
  padding-bottom: 4px;
}

.order__comment-label {
  margin: 10px 0 8px;
  font-size: 14px;
  color: #303133;
}

.order__comment-label:first-child {
  margin-top: 0;
}
</style>
