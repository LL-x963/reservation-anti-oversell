<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { addToCart } from '@/api/cart'
import { listUserCategories } from '@/api/category'
import { pageUserServices } from '@/api/service'
import { formatDuration, formatPrice } from '@/utils/format'

/**
 * 服务列表
 *
 * <p>接口：GET /api/user/service/page</p>
 * <ul>
 *   <li>参数：page（后端是 page，不是 pageNum）、pageSize（上限 100）、categoryId、petType</li>
 *   <li>返回 { records, total }；服务项字段：id / categoryId / name / price /
 *       serviceDuration（分钟）/ petType / suitWeight / description / image</li>
 * </ul>
 * <p>排序：后端该接口暂不支持排序参数（固定按 id 升序），故「综合排序」仅作占位。</p>
 */

/** 适配宠物筛选项 */
const PET_TYPES = [
  { label: '全部', value: '' },
  { label: '猫', value: '猫' },
  { label: '狗', value: '狗' }
]

/** 每页条数（下滑加载更多） */
const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const categories = ref([])
const services = ref([])
const total = ref(0)
const page = ref(1)

/** 首屏与切换筛选时的加载中 */
const loading = ref(true)
/** 加载更多时的加载中 */
const loadingMore = ref(false)
/** 正在加入预约车的服务 id */
const addingId = ref(null)

/** 已加载条数达到总数即结束 */
const finished = computed(() => services.value.length >= total.value)

/** 是否处于筛选状态（决定空状态是否给「重置筛选」） */
const hasFilter = computed(() => Boolean(filters.value.categoryId || filters.value.petType))

/** 筛选条件：分类默认取路由 query（首页分类点击进来时带上 categoryId） */
const filters = ref({
  categoryId: route.query.categoryId ? Number(route.query.categoryId) : '',
  petType: ''
})

const sentinelRef = ref()
let observer = null

onMounted(async () => {
  // 分类与列表并行加载，单接口失败不影响另一个
  await Promise.allSettled([loadCategories(), reload()])
  observeSentinel()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})

// 筛选条件变化 → 回到第 1 页重新加载
watch(filters, reload, { deep: true })

// 从首页切换分类再次进入本页时同步筛选条件
watch(
  () => route.query.categoryId,
  (value) => {
    const next = value ? Number(value) : ''
    if (next !== filters.value.categoryId) {
      filters.value.categoryId = next
    }
  }
)

async function loadCategories() {
  categories.value = (await listUserCategories()) || []
}

/** 重新从第 1 页加载（首屏 / 切换筛选） */
async function reload() {
  loading.value = true
  page.value = 1
  try {
    const data = await pageUserServices(buildParams(1))
    services.value = data?.records || []
    total.value = data?.total || 0
  } catch (e) {
    services.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 加载下一页（下滑到底部触发） */
async function loadMore() {
  if (loading.value || loadingMore.value || finished.value) {
    return
  }
  loadingMore.value = true
  const next = page.value + 1
  try {
    const data = await pageUserServices(buildParams(next))
    services.value.push(...(data?.records || []))
    total.value = data?.total ?? total.value
    page.value = next
  } catch (e) {
    // 加载更多失败时保留已加载内容，提示由 request 拦截器统一弹出
  } finally {
    loadingMore.value = false
  }
}

function buildParams(targetPage) {
  const params = { page: targetPage, pageSize: PAGE_SIZE }
  if (filters.value.categoryId) {
    params.categoryId = filters.value.categoryId
  }
  if (filters.value.petType) {
    params.petType = filters.value.petType
  }
  return params
}

/** 用哨兵元素做下滑加载更多（IntersectionObserver 在移动端比 scroll 事件更省性能） */
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

function resetFilters() {
  filters.value = { categoryId: '', petType: '' }
}

function goDetail(item) {
  router.push(`/service/${item.id}`)
}

/** 加入预约车：阻止冒泡，避免同时跳到详情页 */
async function handleAddCart(item) {
  if (addingId.value) {
    return
  }
  addingId.value = item.id
  try {
    await addToCart(item.id, 1)
    ElMessage.success('已加入预约车')
  } catch (e) {
    // 失败提示由 request 拦截器统一弹出
  } finally {
    addingId.value = null
  }
}
</script>

<template>
  <div class="service h5-page">
    <!-- 1. 顶部筛选栏 -->
    <div class="service__filter">
      <div class="service__filter-row">
        <el-select
          v-model="filters.categoryId"
          class="service__select"
          placeholder="全部分类"
          size="default"
        >
          <el-option label="全部分类" :value="''" />
          <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>

        <!-- 综合排序：后端接口暂不支持排序参数，先作占位 -->
        <div class="service__sort is-disabled" title="排序功能开发中">
          综合排序
          <el-icon><ArrowDown /></el-icon>
        </div>
      </div>

      <div class="service__pets">
        <span
          v-for="item in PET_TYPES"
          :key="item.value"
          class="service__pet h5-tap"
          :class="{ 'is-active': filters.petType === item.value }"
          @click="filters.petType = item.value"
        >
          {{ item.label }}
        </span>
      </div>
    </div>

    <!-- 2. 首屏加载骨架 -->
    <div v-if="loading" class="service__list">
      <div v-for="n in 3" :key="n" class="service__card service__card--skeleton">
        <div class="service__thumb service__thumb--plain"></div>
        <div class="service__info">
          <div class="service__line"></div>
          <div class="service__line service__line--short"></div>
          <div class="service__line service__line--short"></div>
        </div>
      </div>
    </div>

    <!-- 3. 服务列表 -->
    <div v-else-if="services.length" class="service__list">
      <article
        v-for="item in services"
        :key="item.id"
        class="service__card h5-tap"
        @click="goDetail(item)"
      >
        <div class="service__thumb">
          <img v-if="item.image" :src="item.image" :alt="item.name" loading="lazy" />
          <span v-else class="service__thumb-tip">暂无图片</span>
        </div>

        <div class="service__info">
          <h3 class="service__name">{{ item.name }}</h3>

          <p v-if="item.petType || item.suitWeight" class="service__tags">
            <span v-if="item.petType" class="service__tag">{{ item.petType }}</span>
            <span v-if="item.suitWeight" class="service__tag">{{ item.suitWeight }}</span>
          </p>

          <p v-if="item.description" class="service__desc">{{ item.description }}</p>

          <div class="service__bottom">
            <span class="service__price">{{ formatPrice(item.price) }}</span>
            <span class="service__duration">{{ formatDuration(item.serviceDuration) }}</span>
            <el-button
              class="service__cart"
              type="primary"
              size="small"
              round
              :loading="addingId === item.id"
              @click.stop="handleAddCart(item)"
            >
              加入预约车
            </el-button>
          </div>
        </div>
      </article>
    </div>

    <!-- 4. 空状态 -->
    <el-empty v-else :image-size="90" description="没有找到相关服务">
      <el-button v-if="hasFilter" size="small" round @click="resetFilters">重置筛选</el-button>
    </el-empty>

    <!-- 5. 加载更多哨兵 -->
    <div ref="sentinelRef" class="service__sentinel">
      <span v-if="loadingMore">正在加载…</span>
      <span v-else-if="services.length && finished">已全部加载（共 {{ total }} 项）</span>
    </div>
  </div>
</template>

<style scoped>
/* ---------------- 筛选栏 ---------------- */
.service__filter {
  padding: 10px 12px;
  margin-bottom: 10px;
  background-color: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.service__filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.service__select {
  flex: 1;
  max-width: 160px;
}

.service__sort {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  color: #606266;
}

.service__sort.is-disabled {
  color: #c0c4cc;
}

/* 宠物类型分段筛选 */
.service__pets {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.service__pet {
  padding: 4px 14px;
  font-size: 13px;
  color: #606266;
  background-color: #f5f6f8;
  border-radius: 14px;
}

.service__pet.is-active {
  color: #fff;
  background-color: var(--el-color-primary);
}

/* ---------------- 服务卡片 ---------------- */
.service__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.service__card {
  display: flex;
  padding: 10px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.service__thumb {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  margin-right: 10px;
  overflow: hidden;
  background-color: #f5f6f8;
  border-radius: 10px;
}

.service__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.service__thumb--plain {
  height: 96px;
}

.service__thumb-tip {
  font-size: 11px;
  color: #c0c4cc;
}

.service__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.service__name {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service__tags {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}

.service__tag {
  padding: 1px 6px;
  font-size: 11px;
  color: #409eff;
  background-color: rgba(64, 158, 255, 0.1);
  border-radius: 4px;
}

.service__desc {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.4;
  color: #909399;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.service__bottom {
  display: flex;
  align-items: center;
  margin-top: auto;
  padding-top: 8px;
}

.service__price {
  font-size: 16px;
  font-weight: 600;
  color: #f56c6c;
}

.service__duration {
  margin-left: 8px;
  font-size: 11px;
  color: #909399;
}

/* 右下角「加入预约车」 */
.service__cart {
  margin-left: auto;
  padding: 0 12px;
}

/* 骨架屏 */
.service__card--skeleton {
  box-shadow: none;
}

.service__line {
  height: 12px;
  margin-bottom: 8px;
  background-color: #f0f2f5;
  border-radius: 6px;
}

.service__line--short {
  width: 60%;
}

/* ---------------- 加载更多 ---------------- */
.service__sentinel {
  padding: 14px 0;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
