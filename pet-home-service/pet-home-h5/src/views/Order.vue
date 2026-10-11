<script setup>
import { ref } from 'vue'

/**
 * 我的订单
 *
 * <p>本页为骨架结构，待对接接口：</p>
 * <ul>
 *   <li>GET /api/user/order/page —— 订单分页（按状态筛选）</li>
 *   <li>GET /api/user/order/{id} —— 订单详情</li>
 *   <li>POST /api/user/order/pay —— 支付</li>
 *   <li>PUT /api/user/order/cancel/{id} —— 取消订单</li>
 *   <li>POST /api/user/order/reminder/{id} —— 开启上门提醒</li>
 *   <li>POST /api/user/order/comment —— 评价</li>
 * </ul>
 *
 * <p>订单状态：1 待支付、2 待确认、3 待上门、4 服务中、5 已完成、6 已取消、7 待评价。</p>
 */
const STATUS_TABS = [
  { key: 'all', label: '全部' },
  { key: 1, label: '待支付' },
  { key: 2, label: '待确认' },
  { key: 3, label: '待上门' },
  { key: 5, label: '已完成' }
]

const activeStatus = ref('all')
</script>

<template>
  <div class="order h5-page">
    <!-- 状态筛选 -->
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

    <!-- 订单列表 -->
    <div class="h5-card">
      <div class="h5-placeholder">待接入 GET /api/user/order/page?status=...</div>
    </div>
  </div>
</template>

<style scoped>
.order__tabs {
  display: flex;
  margin-bottom: 8px;
  overflow-x: auto;
  background-color: #fff;
  border-radius: 10px;
}

.order__tab {
  flex: 1;
  flex-shrink: 0;
  padding: 10px 12px;
  color: #606266;
  font-size: 14px;
  text-align: center;
}

.order__tab.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
  box-shadow: inset 0 -2px 0 var(--el-color-primary);
}
</style>
