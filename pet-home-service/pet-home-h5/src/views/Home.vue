<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listUserCategories } from '@/api/category'
import { pageUserServices } from '@/api/service'
import { formatDuration, formatPrice } from '@/utils/format'

/**
 * 首页
 *
 * <p>对接接口：</p>
 * <ul>
 *   <li>GET /api/user/category/list → 启用分类数组 [{ id, name, sort }]</li>
 *   <li>GET /api/user/service/page → { records, total }，服务项字段：
 *       id / categoryId / name / price / serviceDuration / petType / suitWeight / image</li>
 * </ul>
 */

/** 热门服务展示条数（2 列网格，两行共 8 个） */
const HOT_PAGE_SIZE = 8

/** 推荐服务的候选取数（用于“为你推荐”区块） */
const RECOMMEND_PAGE_SIZE = 20

/**
 * 分类图标与配色
 *
 * <p>分类接口只返回 id / name / sort，不含图标，这里按下标循环取一套内置图标与配色；
 * 后续若后端补上 icon 字段，替换 categoryIcon() 即可。</p>
 */
const CATEGORY_ICONS = ['Sunny', 'MagicStick', 'Star', 'Document']
const CATEGORY_COLORS = ['#409eff', '#ff8c1a', '#67c23a', '#909399']

const router = useRouter()

const categories = ref([])
const hotServices = ref([])
const recommendServices = ref([])
const loading = ref(true)

onMounted(loadData)

async function loadData() {
  loading.value = true
  // 三个请求并行，单个失败不影响其它区块（错误提示由 request 拦截器统一弹出）
  await Promise.allSettled([loadCategories(), loadHotServices(), loadRecommendServices()])
  loading.value = false
}

async function loadCategories() {
  categories.value = (await listUserCategories()) || []
}

async function loadHotServices() {
  const data = await pageUserServices({ page: 1, pageSize: HOT_PAGE_SIZE })
  hotServices.value = data?.records || []
}

/**
 * “为你推荐”：先取第 2 页；服务总量不足一页（第 2 页为空）时回退为全部服务按价格升序，
 * 保证区块不会空白。
 */
async function loadRecommendServices() {
  const next = await pageUserServices({ page: 2, pageSize: HOT_PAGE_SIZE })
  const rest = next?.records || []
  if (rest.length) {
    recommendServices.value = rest
    return
  }
  const all = await pageUserServices({ page: 1, pageSize: RECOMMEND_PAGE_SIZE })
  recommendServices.value = sortByPriceAsc(all?.records || [])
}

function sortByPriceAsc(list) {
  return [...list].sort((a, b) => Number(a.price || 0) - Number(b.price || 0))
}

function categoryIcon(index) {
  return CATEGORY_ICONS[index % CATEGORY_ICONS.length]
}

function categoryColor(index) {
  return CATEGORY_COLORS[index % CATEGORY_COLORS.length]
}

/** 顶部搜索栏：跳服务列表（搜索页后续实现） */
function goSearch() {
  router.push('/service')
}

/** 点击分类：带 categoryId 跳服务列表筛选 */
function goCategory(category) {
  router.push({ path: '/service', query: { categoryId: category.id } })
}

/** 点击服务卡片：进详情页 */
function goDetail(item) {
  router.push(`/service/${item.id}`)
}
</script>

<template>
  <div class="home h5-page">
    <!-- 1. 顶部搜索栏（占位，点击进服务列表） -->
    <div class="home__search h5-tap" @click="goSearch">
      <el-icon class="home__search-icon"><Search /></el-icon>
      <span class="home__search-text">搜索服务名称，如「猫咪洗澡」</span>
    </div>

    <!-- 2. 分类导航：横向滚动不换行 -->
    <div v-if="categories.length" class="home__cats">
      <div
        v-for="(item, index) in categories"
        :key="item.id"
        class="home__cat h5-tap"
        @click="goCategory(item)"
      >
        <div
          class="home__cat-icon"
          :style="{ color: categoryColor(index), backgroundColor: `${categoryColor(index)}1a` }"
        >
          <el-icon :size="22"><component :is="categoryIcon(index)" /></el-icon>
        </div>
        <span class="home__cat-name">{{ item.name }}</span>
      </div>
    </div>

    <!-- 3. 热门服务：2 列网格 -->
    <section class="home__section">
      <div class="home__section-head">
        <h2 class="home__section-title">热门服务</h2>
        <span class="home__more h5-tap" @click="goSearch">
          全部
          <el-icon><ArrowRight /></el-icon>
        </span>
      </div>

      <!-- 加载骨架 -->
      <div v-if="loading" class="home__grid">
        <div v-for="n in 4" :key="n" class="home__card home__card--skeleton">
          <div class="home__img home__img--plain"></div>
          <div class="home__card-body">
            <div class="home__line"></div>
            <div class="home__line home__line--short"></div>
          </div>
        </div>
      </div>

      <!-- 服务卡片 -->
      <div v-else-if="hotServices.length" class="home__grid">
        <article
          v-for="item in hotServices"
          :key="item.id"
          class="home__card h5-tap"
          @click="goDetail(item)"
        >
          <div class="home__img">
            <img v-if="item.image" :src="item.image" :alt="item.name" loading="lazy" />
            <span v-else class="home__img-tip">暂无图片</span>
          </div>
          <div class="home__card-body">
            <h3 class="home__card-name">{{ item.name }}</h3>
            <div class="home__card-meta">
              <span class="home__price">{{ formatPrice(item.price) }}</span>
              <span class="home__duration">{{ formatDuration(item.serviceDuration) }}</span>
            </div>
          </div>
        </article>
      </div>

      <p v-else class="home__empty">暂无上架服务</p>
    </section>

    <!-- 4. 底部推荐服务列表 -->
    <section v-if="recommendServices.length" class="home__section">
      <div class="home__section-head">
        <h2 class="home__section-title">为你推荐</h2>
      </div>

      <div class="home__list">
        <article
          v-for="item in recommendServices"
          :key="item.id"
          class="home__list-item h5-tap"
          @click="goDetail(item)"
        >
          <div class="home__thumb">
            <img v-if="item.image" :src="item.image" :alt="item.name" loading="lazy" />
            <span v-else class="home__img-tip">暂无</span>
          </div>
          <div class="home__list-body">
            <h3 class="home__card-name">{{ item.name }}</h3>
            <p v-if="item.petType || item.suitWeight" class="home__list-desc">
              <template v-if="item.petType">适配 {{ item.petType }}</template>
              <template v-if="item.petType && item.suitWeight"> · </template>
              <template v-if="item.suitWeight">{{ item.suitWeight }}</template>
            </p>
            <div class="home__card-meta">
              <span class="home__price">{{ formatPrice(item.price) }}</span>
              <span class="home__duration">{{ formatDuration(item.serviceDuration) }}</span>
            </div>
          </div>
          <el-icon class="home__list-arrow" color="#c0c4cc"><ArrowRight /></el-icon>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ---------------- 顶部搜索栏 ---------------- */
.home__search {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 12px;
  margin-bottom: 12px;
  color: #a8abb2;
  font-size: 13px;
  background-color: #fff;
  border-radius: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.home__search-icon {
  margin-right: 6px;
  font-size: 16px;
  color: #c0c4cc;
}

/* ---------------- 分类导航（横向滚动） ---------------- */
.home__cats {
  display: flex;
  gap: 16px;
  /* 左右贴边滚动：抵消页面内边距后自行补回 */
  padding: 2px var(--h5-page-padding) 12px;
  margin: 0 calc(-1 * var(--h5-page-padding)) 4px;
  overflow-x: auto;
  white-space: nowrap;
  -webkit-overflow-scrolling: touch;
}

/* 隐藏横向滚动条（移动端视觉更干净） */
.home__cats::-webkit-scrollbar {
  display: none;
}

.home__cat {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  width: 60px;
}

.home__cat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
}

.home__cat-name {
  width: 100%;
  margin-top: 6px;
  overflow: hidden;
  font-size: 12px;
  color: #606266;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------------- 区块通用 ---------------- */
.home__section {
  margin-bottom: 14px;
}

.home__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.home__section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.home__more {
  display: flex;
  align-items: center;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.home__empty {
  padding: 24px 12px;
  color: #909399;
  font-size: 13px;
  text-align: center;
  background-color: #fff;
  border-radius: 12px;
}

/* ---------------- 热门服务网格（2 列） ---------------- */
.home__grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.home__card {
  overflow: hidden;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.home__img {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background-color: #f5f6f8;
}

.home__img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.home__img--plain {
  aspect-ratio: 4 / 3;
}

.home__img-tip {
  font-size: 11px;
  color: #c0c4cc;
}

.home__card-body {
  padding: 8px 10px 10px;
}

.home__card-name {
  display: -webkit-box;
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  color: #303133;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.home__card-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 6px;
}

.home__price {
  font-size: 15px;
  font-weight: 600;
  color: #f56c6c;
}

.home__duration {
  font-size: 11px;
  color: #909399;
}

/* 骨架屏 */
.home__card--skeleton {
  box-shadow: none;
}

.home__line {
  height: 12px;
  margin-bottom: 8px;
  background-color: #f0f2f5;
  border-radius: 6px;
}

.home__line--short {
  width: 60%;
}

/* ---------------- 推荐服务列表 ---------------- */
.home__list {
  overflow: hidden;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.home__list-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
}

.home__list-item + .home__list-item {
  border-top: 1px solid #f5f6f8;
}

.home__thumb {
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

.home__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.home__list-body {
  flex: 1;
  min-width: 0;
}

.home__list-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.home__list-arrow {
  flex: 0 0 auto;
  margin-left: 6px;
  font-size: 16px;
}
</style>
