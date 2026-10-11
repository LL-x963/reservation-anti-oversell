<script setup>
import { useRouter } from 'vue-router'

/**
 * 预约车
 *
 * <p>本页为骨架结构，待对接接口：</p>
 * <ul>
 *   <li>GET /api/user/cart/list —— 预约车列表</li>
 *   <li>PUT /api/user/cart/update —— 修改数量</li>
 *   <li>DELETE /api/user/cart/delete —— 删除单项</li>
 *   <li>DELETE /api/user/cart/clean —— 清空</li>
 * </ul>
 */
const router = useRouter()
</script>

<template>
  <div class="cart h5-page">
    <div class="h5-card">
      <div class="h5-placeholder">待接入 GET /api/user/cart/list</div>
    </div>

    <!-- 底部结算栏（内容区已预留 TabBar 高度，这里用 sticky 贴住内容区底部） -->
    <div class="cart__footer">
      <div class="cart__total">
        合计
        <span class="cart__amount">￥{{ 0 }}</span>
      </div>
      <el-button type="primary" round @click="router.push('/submit-order')">去预约</el-button>
    </div>
  </div>
</template>

<style scoped>
.cart__footer {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background-color: #fff;
  border-radius: 10px;
  box-shadow: 0 -1px 6px rgba(0, 0, 0, 0.05);
}

.cart__total {
  color: #606266;
  font-size: 13px;
}

.cart__amount {
  margin-left: 4px;
  color: #f56c6c;
  font-size: 18px;
  font-weight: 600;
}
</style>
