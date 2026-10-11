<script setup>
import { useRouter } from 'vue-router'

/**
 * 首页
 *
 * <p>本页为骨架结构，待对接接口：</p>
 * <ul>
 *   <li>GET /api/user/category/list —— 服务分类（护理 / 饮食 / 疾病预防等）</li>
 *   <li>GET /api/user/service/page —— 推荐服务列表（分页）</li>
 *   <li>GET /api/user/pet/list —— 我的宠物（用于快捷预约）</li>
 * </ul>
 */
const router = useRouter()

/** 功能快捷入口（分类数据接入后可改为接口返回） */
const quickEntries = [
  { key: 'wash', title: '上门洗澡', icon: 'Sunny', color: '#409eff' },
  { key: 'groom', title: '美容造型', icon: 'MagicStick', color: '#ff8c1a' },
  { key: 'care', title: '日常护理', icon: 'Star', color: '#67c23a' },
  { key: 'pet', title: '宠物档案', icon: 'Document', color: '#909399' }
]

function handleEntry(key) {
  if (key === 'pet') {
    router.push('/pet')
    return
  }
  router.push('/service')
}
</script>

<template>
  <div class="home h5-page">
    <!-- 顶部横幅 -->
    <div class="home__banner">
      <h2 class="home__banner-title">专业上门 · 安心到家</h2>
      <p class="home__banner-desc">持证美容师上门服务，全程可视化</p>
    </div>

    <!-- 快捷入口 -->
    <div class="h5-card home__quick">
      <div v-for="item in quickEntries" :key="item.key" class="home__quick-item h5-tap" @click="handleEntry(item.key)">
        <el-icon :size="24" :color="item.color"><component :is="item.icon" /></el-icon>
        <span class="home__quick-text">{{ item.title }}</span>
      </div>
    </div>

    <!-- 推荐服务 -->
    <div class="h5-card">
      <div class="home__section-title">
        <span>热门服务</span>
        <span class="home__more h5-tap" @click="router.push('/service')">
          全部
          <el-icon><ArrowRight /></el-icon>
        </span>
      </div>
      <div class="h5-placeholder">待接入 GET /api/user/service/page</div>
    </div>

    <!-- 我的宠物 -->
    <div class="h5-card">
      <div class="home__section-title">
        <span>我的宠物</span>
        <span class="home__more h5-tap" @click="router.push('/pet')">
          管理
          <el-icon><ArrowRight /></el-icon>
        </span>
      </div>
      <div class="h5-placeholder">待接入 GET /api/user/pet/list</div>
    </div>
  </div>
</template>

<style scoped>
.home__banner {
  padding: 18px 16px;
  margin-bottom: 12px;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #79bbff);
  border-radius: 10px;
}

.home__banner-title {
  font-size: 18px;
  font-weight: 600;
}

.home__banner-desc {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.9;
}

.home__quick {
  display: flex;
}

.home__quick-item {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
}

.home__quick-text {
  margin-top: 6px;
  color: #606266;
  font-size: 12px;
}

.home__section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 15px;
  font-weight: 600;
}

.home__more {
  display: flex;
  align-items: center;
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}
</style>
