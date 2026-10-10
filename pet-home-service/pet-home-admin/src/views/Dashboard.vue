<script setup>
/**
 * 后台首页
 *
 * 数据来源（全部为现成接口，无后端改动）：
 *   今日营业额     GET /admin/stat/revenue?date=今天&type=day      → totalAmount
 *   今日订单数     GET /admin/order/page?appointmentDate=今天      → total（口径：今日预约，含全部状态）
 *   美容师总数/在职 GET /admin/beautician/page（可选 status=1）      → total
 *   服务项目总数/上架 GET /admin/service/page（可选 status=1）       → total
 *   本周营业额趋势  GET /admin/stat/revenue?date=今天&type=week      → 周一~周日 7 个点
 *   热门服务 Top5  GET /admin/stat/serviceRank                     → 取前 5
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { Goods, Money, Tickets, UserFilled } from '@element-plus/icons-vue'

import { pageBeauticians } from '@/api/beautician'
import { pageOrders } from '@/api/order'
import { pageServiceItems } from '@/api/serviceItem'
import { getRevenue, getServiceRank } from '@/api/stat'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatPercent, todayString } from '@/utils/format'

// 图表依赖 echarts（工程内已安装，如需在新环境还原依赖执行：npm install echarts）
// 采用按需引入，仅注册折线图 + 直角坐标系 + 提示框 + Canvas 渲染器
echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const userStore = useUserStore()

const today = todayString()

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/** 0~1 比率 → el-progress 需要的 0~100 数值 */
function ratioPercent(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return 0
  }
  return Math.min(100, Math.max(0, Number((number * 100).toFixed(2))))
}

/** yyyy-MM-dd → 周几（本地时区解析，避免 new Date 字符串按 UTC 处理） */
function weekdayLabel(dateText) {
  const parts = String(dateText || '').split('-').map(Number)
  if (parts.length !== 3 || parts.some((item) => Number.isNaN(item))) {
    return dateText
  }
  return WEEKDAY_LABELS[new Date(parts[0], parts[1] - 1, parts[2]).getDay()]
}

// ---------------- 欢迎语 ----------------

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() =>
  new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })
)

// ---------------- 四个数据卡片 ----------------

const statsLoading = ref(false)

const stats = reactive({
  todayRevenue: 0,
  todayOrders: 0,
  beauticianTotal: 0,
  beauticianOnDuty: 0,
  serviceTotal: 0,
  serviceOnSale: 0
})

const metrics = computed(() => [
  {
    key: 'todayRevenue',
    title: '今日营业额',
    value: Number(stats.todayRevenue || 0),
    precision: 2,
    prefix: '￥',
    icon: Money,
    color: '#409eff',
    hint: '仅计入已完成订单',
    path: '/stat'
  },
  {
    key: 'todayOrders',
    title: '今日订单数',
    value: Number(stats.todayOrders || 0),
    suffix: '单',
    icon: Tickets,
    color: '#67c23a',
    hint: `按预约日期统计（${today}）`,
    path: '/order'
  },
  {
    key: 'beauticianTotal',
    title: '美容师总数',
    value: Number(stats.beauticianTotal || 0),
    suffix: '人',
    icon: UserFilled,
    color: '#e6a23c',
    hint: `在职 ${stats.beauticianOnDuty} 人`,
    path: '/beautician'
  },
  {
    key: 'serviceTotal',
    title: '服务项目总数',
    value: Number(stats.serviceTotal || 0),
    suffix: '个',
    icon: Goods,
    color: '#8a6de9',
    hint: `上架 ${stats.serviceOnSale} 个`,
    path: '/service'
  }
])

/**
 * 并行加载四项统计；单项失败不影响其它卡片（错误提示由 request 拦截器统一弹出）
 */
async function loadStats() {
  statsLoading.value = true
  try {
    const [revenue, orders, beauticianAll, beauticianDuty, serviceAll, serviceOnSale] =
      await Promise.allSettled([
        // 今日营业额
        getRevenue({ date: today, type: 'day' }),
        // 今日预约订单数（只要 total，pageSize 取 1 即可）
        pageOrders({ page: 1, pageSize: 1, appointmentDate: today }),
        pageBeauticians({ page: 1, pageSize: 1 }),
        pageBeauticians({ page: 1, pageSize: 1, status: 1 }),
        pageServiceItems({ page: 1, pageSize: 1 }),
        pageServiceItems({ page: 1, pageSize: 1, status: 1 })
      ])

    if (revenue.status === 'fulfilled') {
      stats.todayRevenue = revenue.value?.totalAmount || 0
    }
    if (orders.status === 'fulfilled') {
      stats.todayOrders = Number(orders.value?.total || 0)
    }
    if (beauticianAll.status === 'fulfilled') {
      stats.beauticianTotal = Number(beauticianAll.value?.total || 0)
    }
    if (beauticianDuty.status === 'fulfilled') {
      stats.beauticianOnDuty = Number(beauticianDuty.value?.total || 0)
    }
    if (serviceAll.status === 'fulfilled') {
      stats.serviceTotal = Number(serviceAll.value?.total || 0)
    }
    if (serviceOnSale.status === 'fulfilled') {
      stats.serviceOnSale = Number(serviceOnSale.value?.total || 0)
    }
  } finally {
    statsLoading.value = false
  }
}

// ---------------- 本周营业额趋势 ----------------

const chartLoading = ref(false)
const weekRevenue = ref({ dates: [], revenues: [], totalAmount: 0 })

const hasChartData = computed(() => (weekRevenue.value?.dates || []).length > 0)

const chartRef = ref(null)
/** echarts 实例（非响应式，避免被 Vue 代理影响性能） */
let chart = null

function renderChart() {
  if (!chartRef.value) {
    return
  }
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const dates = weekRevenue.value?.dates || []
  const revenues = (weekRevenue.value?.revenues || []).map((item) => Number(item || 0))

  chart.setOption(
    {
      grid: { top: 30, left: 8, right: 20, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        formatter: (params) => {
          const item = params?.[0]
          if (!item) {
            return ''
          }
          const date = dates[item.dataIndex] || ''
          return `${weekdayLabel(date)} ${date}<br/>营业额：￥${formatAmount(item.value)}`
        }
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: dates.map(weekdayLabel),
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        axisLabel: { formatter: (value) => `￥${value}` }
      },
      series: [
        {
          name: '营业额',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          lineStyle: { width: 3, color: '#409eff' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(64, 158, 255, 0.28)' },
                { offset: 1, color: 'rgba(64, 158, 255, 0.02)' }
              ]
            }
          },
          // 今天用橙色圆点高亮，便于一眼定位
          data: revenues.map((value, index) => ({
            value,
            symbolSize: dates[index] === today ? 10 : 7,
            itemStyle: { color: dates[index] === today ? '#e6a23c' : '#409eff' }
          }))
        }
      ]
    },
    true
  )
  chart.resize()
}

async function loadWeekRevenue() {
  chartLoading.value = true
  try {
    const data = await getRevenue({ date: today, type: 'week' })
    weekRevenue.value = data || { dates: [], revenues: [], totalAmount: 0 }
    renderChart()
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    chartLoading.value = false
  }
}

// ---------------- 热门服务 Top5 ----------------

const rankLoading = ref(false)
const hotServices = ref([])

async function loadHotServices() {
  rankLoading.value = true
  try {
    const data = await getServiceRank()
    // 后端已按销量降序返回 Top10，这里取前 5
    hotServices.value = (Array.isArray(data) ? data : []).slice(0, 5)
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    rankLoading.value = false
  }
}

/** 排名前三高亮 */
function rankClass(index) {
  return index < 3 ? `is-top${index + 1}` : ''
}

// ---------------- 生命周期 ----------------

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  // 顺带刷新一次管理员信息（顶部导航栏姓名/头像）
  userStore.loadProfile().catch(() => {})
  loadStats()
  loadWeekRevenue()
  loadHotServices()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page-container">
    <!-- 欢迎语 -->
    <div class="welcome-bar">
      <div>
        <div class="welcome-title">{{ greeting }}，{{ userStore.adminName }}</div>
        <div class="welcome-subtitle">{{ todayText }} · 今日经营概览</div>
      </div>
    </div>

    <!-- 四个数据卡片 -->
    <el-row :gutter="16">
      <el-col v-for="item in metrics" :key="item.key" :xs="24" :sm="12" :lg="6" class="metric-col">
        <el-card
          v-loading="statsLoading"
          shadow="never"
          class="metric-card"
          @click="router.push(item.path)"
        >
          <div class="metric-body">
            <div
              class="metric-icon"
              :style="{ color: item.color, backgroundColor: `${item.color}1a` }"
            >
              <el-icon :size="22"><component :is="item.icon" /></el-icon>
            </div>

            <el-statistic
              class="metric-statistic"
              :value="item.value"
              :precision="item.precision || 0"
              :prefix="item.prefix"
              :suffix="item.suffix"
            >
              <template #title>
                <span class="metric-title">{{ item.title }}</span>
              </template>
            </el-statistic>
          </div>

          <div class="metric-hint">{{ item.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势 + 热门服务 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="16" class="panel-col">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">本周营业额趋势</span>
              <span class="card-extra">
                本周合计 <b>￥{{ formatAmount(weekRevenue.totalAmount) }}</b>
              </span>
            </div>
          </template>

          <div v-loading="chartLoading" class="chart-wrapper">
            <div ref="chartRef" class="chart"></div>
            <div v-if="!hasChartData && !chartLoading" class="chart-empty">暂无营业额数据</div>
          </div>

          <div class="chart-tip">
            统计口径：仅计入「已完成」订单（含待评价），按支付时间归入自然日；橙色圆点为今天。
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8" class="panel-col">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">热门服务 Top5</span>
              <el-button link type="primary" @click="router.push('/stat')">更多</el-button>
            </div>
          </template>

          <div v-loading="rankLoading" class="rank-list">
            <template v-if="hotServices.length">
              <div v-for="(item, index) in hotServices" :key="item.serviceId" class="rank-item">
                <span class="rank-badge" :class="rankClass(index)">{{ index + 1 }}</span>

                <div class="rank-body">
                  <div class="rank-name" :title="item.name">{{ item.name }}</div>
                  <el-progress
                    :percentage="ratioPercent(item.ratio)"
                    :stroke-width="8"
                    :show-text="false"
                  />
                </div>

                <div class="rank-value">
                  <div class="rank-sales">{{ item.sales }} 单</div>
                  <div class="rank-ratio">{{ formatPercent(item.ratio) }}</div>
                </div>
              </div>
            </template>

            <el-empty v-else-if="!rankLoading" description="暂无销量数据" :image-size="70" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.welcome-bar {
  margin-bottom: 16px;
}

.welcome-title {
  font-size: 18px;
  font-weight: 600;
}

.welcome-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

/* ---------------- 数据卡片 ---------------- */

.metric-col {
  margin-bottom: 16px;
}

.metric-card {
  cursor: pointer;
  transition: all 0.2s;
}

.metric-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.metric-body {
  display: flex;
  align-items: center;
  gap: 14px;
}

.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  border-radius: 10px;
}

.metric-statistic {
  flex: 1;
  min-width: 0;
}

.metric-title {
  font-size: 13px;
  color: #909399;
}

.metric-hint {
  margin-top: 12px;
  padding-top: 10px;
  font-size: 12px;
  color: #909399;
  border-top: 1px dashed #ebeef5;
}

/* ---------------- 趋势图 / 热门服务 ---------------- */

.panel-col {
  margin-bottom: 16px;
}

.panel-card {
  height: 100%;
}

.card-header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
}

.card-extra {
  font-size: 13px;
  color: #909399;
}

.card-extra b {
  font-size: 15px;
  color: #409eff;
}

.chart-wrapper {
  position: relative;
  min-height: 320px;
}

.chart {
  width: 100%;
  height: 320px;
}

.chart-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  font-size: 13px;
  color: #909399;
  transform: translate(-50%, -50%);
}

.chart-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.rank-list {
  min-height: 320px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid #f5f7fa;
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  font-size: 12px;
  color: #909399;
  background-color: #f4f4f5;
  border-radius: 50%;
}

.rank-badge.is-top1 {
  color: #7a5000;
  background-color: #ffd04b;
}

.rank-badge.is-top2 {
  color: #5c5c5c;
  background-color: #dcdcdc;
}

.rank-badge.is-top3 {
  color: #6b3f13;
  background-color: #e6b98a;
}

.rank-body {
  flex: 1;
  min-width: 0;
}

.rank-name {
  margin-bottom: 4px;
  overflow: hidden;
  font-size: 13px;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-value {
  flex-shrink: 0;
  text-align: right;
}

.rank-sales {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.rank-ratio {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
</style>
