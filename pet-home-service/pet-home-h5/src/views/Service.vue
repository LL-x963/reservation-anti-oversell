<script setup>
import { ref } from 'vue'

/**
 * 服务列表
 *
 * <p>本页为骨架结构，待对接接口：</p>
 * <ul>
 *   <li>GET /api/user/category/list —— 分类筛选（全部 + 各分类）</li>
 *   <li>GET /api/user/service/page —— 服务分页（关键词、分类、排序）</li>
 *   <li>POST /api/user/cart/add —— 加入预约车</li>
 * </ul>
 */
const keyword = ref('')
const activeCategory = ref('all')

function handleSearch() {
  // TODO 对接 GET /api/user/service/page?keyword=...&category=...
}
</script>

<template>
  <div class="service h5-page">
    <!-- 搜索栏 -->
    <div class="h5-card service__search">
      <el-input v-model="keyword" placeholder="搜索服务名称" clearable @keyup.enter="handleSearch">
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- 分类筛选 -->
    <div class="service__tabs">
      <span
        class="service__tab h5-tap"
        :class="{ 'is-active': activeCategory === 'all' }"
        @click="activeCategory = 'all'"
      >
        全部
      </span>
      <span class="service__tab-tip">分类待接入 GET /api/user/category/list</span>
    </div>

    <!-- 服务列表 -->
    <div class="h5-card">
      <div class="h5-placeholder">待接入 GET /api/user/service/page</div>
    </div>
  </div>
</template>

<style scoped>
.service__search {
  padding: 10px 12px;
}

.service__tabs {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  overflow-x: auto;
}

.service__tab {
  flex-shrink: 0;
  padding: 4px 14px;
  color: #606266;
  font-size: 14px;
  background-color: #fff;
  border-radius: 14px;
}

.service__tab.is-active {
  color: #fff;
  background-color: var(--el-color-primary);
}

.service__tab-tip {
  flex-shrink: 0;
  margin-left: 8px;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
