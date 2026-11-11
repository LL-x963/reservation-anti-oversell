<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

import { cleanCart, listCart, removeCartItem, updateCartQuantity } from '@/api/cart'
import { formatPrice } from '@/utils/format'

/**
 * 预约车
 *
 * <p>接口：</p>
 * <ul>
 *   <li>GET /api/user/cart/list → [{ serviceId, name, price, quantity, image, amount }]</li>
 *   <li>PUT /api/user/cart/update → { serviceId, quantity }，**直接覆盖**数量（1~99）</li>
 *   <li>DELETE /api/user/cart/delete?serviceId=</li>
 *   <li>DELETE /api/user/cart/clean</li>
 * </ul>
 * <p>后端用 Redis Hash 存储（TTL 7 天），list() 时会自动清理已删除/已下架的服务。</p>
 */

/** 数量上下限（与后端 CartAddRequest / CartUpdateRequest 保持一致） */
const MIN_QUANTITY = 1
const MAX_QUANTITY = 99

const router = useRouter()

const items = ref([])
const loading = ref(true)

/** 已勾选的服务 id */
const selectedIds = ref([])

/** 正在改数量/删除的服务 id（同一行操作期间禁用该行按钮，避免并发请求） */
const pendingId = ref(null)

const allSelected = computed(
  () => items.value.length > 0 && selectedIds.value.length === items.value.length
)

const selectedCount = computed(() => selectedIds.value.length)

/** 合计金额：只统计已勾选项，按本地数量实时计算 */
const totalAmount = computed(() =>
  items.value
    .filter((item) => selectedIds.value.includes(item.serviceId))
    .reduce((sum, item) => sum + Number(item.price || 0) * item.quantity, 0)
)

onMounted(loadCart)

async function loadCart() {
  loading.value = true
  try {
    const data = await listCart()
    items.value = (data || []).map((item) => ({
      ...item,
      quantity: Number(item.quantity) || MIN_QUANTITY
    }))
    // 保留仍然存在的勾选项（服务被下架清理后自动取消勾选）
    selectedIds.value = selectedIds.value.filter((id) =>
      items.value.some((item) => item.serviceId === id)
    )
  } catch (e) {
    // 失败提示由 request 拦截器统一弹出
    items.value = []
  } finally {
    loading.value = false
  }
}

function isSelected(serviceId) {
  return selectedIds.value.includes(serviceId)
}

function toggleSelect(serviceId) {
  selectedIds.value = isSelected(serviceId)
    ? selectedIds.value.filter((id) => id !== serviceId)
    : [...selectedIds.value, serviceId]
}

/** 全选 / 取消全选 */
function toggleSelectAll() {
  selectedIds.value = allSelected.value ? [] : items.value.map((item) => item.serviceId)
}

/**
 * 数量加减：先本地更新（小计与合计立即变化），接口失败再回滚
 */
async function changeQuantity(item, delta) {
  if (pendingId.value === item.serviceId) {
    return
  }
  const next = item.quantity + delta
  if (next < MIN_QUANTITY) {
    ElMessage.warning('数量不能小于 1，如需移除请点击删除')
    return
  }
  if (next > MAX_QUANTITY) {
    ElMessage.warning(`单个服务数量不能超过 ${MAX_QUANTITY}`)
    return
  }

  const previous = item.quantity
  item.quantity = next
  pendingId.value = item.serviceId
  try {
    await updateCartQuantity(item.serviceId, next)
  } catch (e) {
    // 失败回滚为原数量，保证界面与后端一致
    item.quantity = previous
  } finally {
    pendingId.value = null
  }
}

/** 删除单条服务 */
async function handleRemove(item) {
  try {
    await ElMessageBox.confirm(`确定从预约车移除「${item.name}」吗？`, '提示', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  try {
    await removeCartItem(item.serviceId)
    items.value = items.value.filter((row) => row.serviceId !== item.serviceId)
    selectedIds.value = selectedIds.value.filter((id) => id !== item.serviceId)
    ElMessage.success('已移除')
  } catch (e) {
    // 失败提示由 request 拦截器统一弹出
  }
}

/** 清空预约车 */
async function handleClean() {
  try {
    await ElMessageBox.confirm('确定清空预约车吗？', '提示', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  try {
    await cleanCart()
    items.value = []
    selectedIds.value = []
    ElMessage.success('预约车已清空')
  } catch (e) {
    // 失败提示由 request 拦截器统一弹出
  }
}

/**
 * 去结算：先校验至少选中一项，再携带选中的服务 id 跳提交预约页
 */
function handleCheckout() {
  if (!selectedCount.value) {
    ElMessage.warning('请至少选择一项服务')
    return
  }
  router.push({
    path: '/submit-order',
    query: { serviceIds: selectedIds.value.join(',') }
  })
}

function goServiceList() {
  router.push('/service')
}
</script>

<template>
  <div class="cart h5-page">
    <!-- 加载骨架 -->
    <div v-if="loading" class="cart__list">
      <div v-for="n in 2" :key="n" class="cart__item cart__item--skeleton">
        <div class="cart__thumb cart__thumb--plain"></div>
        <div class="cart__info">
          <div class="cart__line"></div>
          <div class="cart__line cart__line--short"></div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <el-empty v-else-if="!items.length" :image-size="90" description="预约车还是空的，快去挑选服务吧">
      <el-button type="primary" size="small" round @click="goServiceList">去挑服务</el-button>
    </el-empty>

    <template v-else>
      <!-- 头部：条数 + 清空 -->
      <div class="cart__head">
        <span class="cart__count">共 {{ items.length }} 项服务</span>
        <span class="cart__clean h5-tap" @click="handleClean">清空</span>
      </div>

      <!-- 服务列表 -->
      <div class="cart__list">
        <div v-for="item in items" :key="item.serviceId" class="cart__item">
          <el-checkbox
            class="cart__check"
            :model-value="isSelected(item.serviceId)"
            @change="toggleSelect(item.serviceId)"
          />

          <div class="cart__thumb">
            <img v-if="item.image" :src="item.image" :alt="item.name" loading="lazy" />
            <span v-else class="cart__thumb-tip">暂无图片</span>
          </div>

          <div class="cart__info">
            <h3 class="cart__name">{{ item.name }}</h3>

            <div class="cart__row">
              <span class="cart__price">{{ formatPrice(item.price) }}</span>

              <!-- 数量加减器 -->
              <div class="cart__stepper">
                <span
                  class="cart__step h5-tap"
                  :class="{ 'is-disabled': item.quantity <= MIN_QUANTITY || pendingId === item.serviceId }"
                  @click="changeQuantity(item, -1)"
                >
                  −
                </span>
                <span class="cart__qty">{{ item.quantity }}</span>
                <span
                  class="cart__step h5-tap"
                  :class="{ 'is-disabled': item.quantity >= MAX_QUANTITY || pendingId === item.serviceId }"
                  @click="changeQuantity(item, 1)"
                >
                  ＋
                </span>
              </div>
            </div>

            <div class="cart__row cart__row--bottom">
              <span class="cart__subtotal">
                小计 <em>{{ formatPrice(Number(item.price || 0) * item.quantity) }}</em>
              </span>
              <span class="cart__remove h5-tap" @click="handleRemove(item)">删除</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部固定结算栏（悬浮在 TabBar 之上） -->
      <div class="cart__bar">
        <!-- 全选：只由 checkbox 的 change 触发，外层不再绑定 click，避免冒泡导致触发两次互相抵消 -->
        <div class="cart__all">
          <el-checkbox :model-value="allSelected" @change="toggleSelectAll">全选</el-checkbox>
        </div>

        <div class="cart__total">
          <span class="cart__total-label">合计</span>
          <span class="cart__total-amount">{{ formatPrice(totalAmount) }}</span>
        </div>

        <el-button class="cart__checkout" type="primary" round @click="handleCheckout">
          去结算{{ selectedCount ? `(${selectedCount})` : '' }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 给底部固定结算栏留出空间（TabBar 留白由 Layout 负责） */
.cart {
  padding-bottom: 64px;
}

/* ---------------- 头部 ---------------- */
.cart__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px 10px;
}

.cart__count {
  font-size: 13px;
  color: #909399;
}

.cart__clean {
  font-size: 13px;
  color: #f56c6c;
}

/* ---------------- 服务条目 ---------------- */
.cart__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cart__item {
  display: flex;
  align-items: center;
  padding: 10px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.cart__check {
  flex: 0 0 auto;
  margin-right: 8px;
}

.cart__thumb {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  margin-right: 10px;
  overflow: hidden;
  background-color: #f5f6f8;
  border-radius: 10px;
}

.cart__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cart__thumb--plain {
  height: 72px;
}

.cart__thumb-tip {
  font-size: 11px;
  color: #c0c4cc;
}

.cart__info {
  flex: 1;
  min-width: 0;
}

.cart__name {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cart__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.cart__row--bottom {
  margin-top: 6px;
}

.cart__price {
  font-size: 13px;
  color: #606266;
}

/* ---------------- 数量加减器 ---------------- */
.cart__stepper {
  display: flex;
  align-items: center;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.cart__step {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 28px;
  font-size: 15px;
  color: #303133;
  background-color: #f7f8fa;
}

.cart__step.is-disabled {
  color: #c0c4cc;
}

.cart__qty {
  min-width: 34px;
  font-size: 14px;
  text-align: center;
}

/* ---------------- 小计与删除 ---------------- */
.cart__subtotal {
  font-size: 12px;
  color: #909399;
}

.cart__subtotal em {
  font-size: 14px;
  font-style: normal;
  font-weight: 600;
  color: #f56c6c;
}

.cart__remove {
  font-size: 12px;
  color: #909399;
}

/* ---------------- 骨架屏 ---------------- */
.cart__item--skeleton {
  box-shadow: none;
}

.cart__line {
  height: 12px;
  margin-bottom: 8px;
  background-color: #f0f2f5;
  border-radius: 6px;
}

.cart__line--short {
  width: 55%;
}

/* ---------------- 底部固定结算栏 ---------------- */
.cart__bar {
  position: fixed;
  right: 0;
  left: 0;
  /* 悬浮在 TabBar 之上 */
  bottom: calc(var(--h5-tabbar-height) + var(--safe-area-bottom));
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background-color: #fff;
  border-top: 1px solid #f0f0f0;
}

.cart__all {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  font-size: 13px;
  color: #606266;
}

.cart__total {
  display: flex;
  flex: 1;
  align-items: baseline;
  justify-content: flex-end;
}

.cart__total-label {
  font-size: 12px;
  color: #909399;
}

.cart__total-amount {
  margin-left: 4px;
  font-size: 18px;
  font-weight: 600;
  color: #f56c6c;
}

.cart__checkout {
  flex-shrink: 0;
  height: 38px;
  padding: 0 18px;
}
</style>
