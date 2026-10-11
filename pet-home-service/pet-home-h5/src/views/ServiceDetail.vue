<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * 服务详情
 *
 * <p>本页为骨架结构，待对接接口：</p>
 * <ul>
 *   <li>GET /api/user/service/{id} —— 服务详情（名称、价格、时长、图文说明）</li>
 *   <li>POST /api/user/cart/add —— 加入预约车</li>
 * </ul>
 */
const route = useRoute()
const router = useRouter()

/** 路由参数中的服务 id */
const serviceId = computed(() => route.params.id)

function handleReserve() {
  router.push({ path: '/submit-order', query: { serviceId: serviceId.value } })
}
</script>

<template>
  <div class="detail h5-page">
    <div class="h5-card">
      <div class="h5-placeholder">待接入 GET /api/user/service/{{ serviceId }}</div>
    </div>

    <!-- 底部操作栏 -->
    <div class="detail__footer">
      <el-button size="large" @click="handleReserve">立即预约</el-button>
    </div>
  </div>
</template>

<style scoped>
.detail__footer {
  position: sticky;
  bottom: 0;
  padding: 10px 12px;
  background-color: #fff;
  border-radius: 10px;
  box-shadow: 0 -1px 6px rgba(0, 0, 0, 0.05);
}

.detail__footer :deep(.el-button) {
  width: 100%;
}
</style>
