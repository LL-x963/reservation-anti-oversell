<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { addToCart } from '@/api/cart'
import { getUserServiceDetail } from '@/api/service'
import { formatDuration, formatPrice } from '@/utils/format'

/**
 * 服务详情
 *
 * <p>接口：GET /api/user/service/{id}</p>
 * <p>返回字段：id / categoryId / name / price / serviceDuration（分钟）/ petType /
 * suitWeight / description / image</p>
 * <p>服务不存在或已下架时后端返回 code=0「服务不存在或已下架」（拦截器已提示），
 * 这里再渲染一个兜底空态。</p>
 */

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const loading = ref(true)
const failed = ref(false)
const adding = ref(false)

/** 路由参数中的服务 id */
const serviceId = computed(() => route.params.id)

/**
 * 轮播图数据：后端目前只返回单张主图，包成数组便于后续扩展为多图
 */
const images = computed(() => (detail.value?.image ? [detail.value.image] : []))

onMounted(loadDetail)

async function loadDetail() {
  loading.value = true
  failed.value = false
  try {
    detail.value = await getUserServiceDetail(serviceId.value)
  } catch (e) {
    // 失败提示由 request 拦截器统一弹出
    detail.value = null
    failed.value = true
  } finally {
    loading.value = false
  }
}

/** 加入预约车（数量固定 1，详情页不提供数量选择） */
async function handleAddCart() {
  if (adding.value || !detail.value) {
    return
  }
  adding.value = true
  try {
    await addToCart(detail.value.id, 1)
    ElMessage.success('已加入预约车')
  } catch (e) {
    // 失败提示由 request 拦截器统一弹出
  } finally {
    adding.value = false
  }
}

/** 立即预约：携带服务 id 跳提交预约页 */
function handleReserve() {
  router.push({ path: '/submit-order', query: { serviceId: serviceId.value } })
}
</script>

<template>
  <div class="detail">
    <!-- 加载骨架 -->
    <div v-if="loading" class="detail__skeleton">
      <div class="detail__banner detail__banner--plain"></div>
      <div class="h5-card">
        <div class="detail__line"></div>
        <div class="detail__line detail__line--short"></div>
      </div>
    </div>

    <!-- 兜底：服务不存在或已下架 -->
    <div v-else-if="failed || !detail" class="h5-page">
      <el-empty :image-size="90" description="服务不存在或已下架">
        <el-button size="small" round @click="router.replace('/service')">去看看其它服务</el-button>
      </el-empty>
    </div>

    <template v-else>
      <div class="detail__content">
        <!-- 1. 顶部轮播图（当前后端只有单图，多图时自动变成轮播） -->
        <el-carousel
          class="detail__banner"
          height="240px"
          :autoplay="images.length > 1"
          :arrow="images.length > 1 ? 'hover' : 'never'"
          :indicator-position="images.length > 1 ? '' : 'none'"
        >
          <el-carousel-item v-for="(url, index) in images" :key="index">
            <img class="detail__banner-img" :src="url" :alt="detail.name" />
          </el-carousel-item>
          <el-carousel-item v-if="!images.length">
            <div class="detail__banner-empty">暂无图片</div>
          </el-carousel-item>
        </el-carousel>

        <div class="h5-page">
          <!-- 2. 服务信息 -->
          <div class="h5-card">
            <h1 class="detail__name">{{ detail.name }}</h1>

            <div class="detail__price-row">
              <span class="detail__price">{{ formatPrice(detail.price) }}</span>
              <span class="detail__duration">{{ formatDuration(detail.serviceDuration) }}</span>
            </div>

            <div class="detail__rows">
              <div class="detail__row">
                <span class="detail__label">适配宠物</span>
                <span class="detail__value">{{ detail.petType || '通用' }}</span>
              </div>
              <div class="detail__row">
                <span class="detail__label">适配体重</span>
                <span class="detail__value">{{ detail.suitWeight || '不限' }}</span>
              </div>
            </div>
          </div>

          <!-- 3. 服务描述 -->
          <div class="h5-card">
            <h2 class="detail__section-title">服务介绍</h2>
            <p class="detail__desc">{{ detail.description || '暂无详细介绍' }}</p>
          </div>
        </div>
      </div>

      <!-- 4. 底部固定操作栏 -->
      <div class="detail__bar">
        <el-button
          class="detail__cart"
          size="large"
          :loading="adding"
          @click="handleAddCart"
        >
          加入预约车
        </el-button>
        <el-button class="detail__reserve" type="primary" size="large" @click="handleReserve">
          立即预约
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 内容区给底部固定操作栏留出高度 */
.detail__content {
  padding-bottom: calc(70px + var(--safe-area-bottom));
}

/* ---------------- 轮播图 ---------------- */
.detail__banner {
  background-color: #f5f6f8;
}

.detail__banner--plain {
  height: 240px;
}

.detail__banner-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail__banner-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 13px;
  color: #c0c4cc;
}

/* 骨架 */
.detail__line {
  height: 14px;
  margin-bottom: 10px;
  background-color: #f0f2f5;
  border-radius: 6px;
}

.detail__line--short {
  width: 55%;
}

/* ---------------- 服务信息 ---------------- */
.detail__name {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  color: #303133;
}

.detail__price-row {
  display: flex;
  align-items: baseline;
  margin-top: 8px;
}

.detail__price {
  font-size: 22px;
  font-weight: 600;
  color: #f56c6c;
}

.detail__duration {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.detail__rows {
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px solid #f5f6f8;
}

.detail__row {
  display: flex;
  align-items: center;
  min-height: 30px;
  font-size: 13px;
}

.detail__label {
  width: 72px;
  color: #909399;
}

.detail__value {
  color: #303133;
}

/* ---------------- 服务描述 ---------------- */
.detail__section-title {
  margin-bottom: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.detail__desc {
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
  /* 保留后端描述里的换行 */
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------------- 底部固定操作栏 ---------------- */
.detail__bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 50;
  display: flex;
  gap: 10px;
  padding: 10px 12px calc(10px + var(--safe-area-bottom));
  background-color: #fff;
  border-top: 1px solid #f0f0f0;
}

.detail__cart {
  flex: 1;
  height: 46px;
  font-size: 15px;
}

.detail__reserve {
  flex: 1.2;
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 23px;
}
</style>
