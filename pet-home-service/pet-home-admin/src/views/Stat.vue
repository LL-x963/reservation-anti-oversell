<script setup>
/**
 * 数据统计页面
 *
 * 图表依赖 echarts（工程内已安装，如需在新环境还原依赖执行）：
 *   npm install echarts
 *
 * 这里采用**按需引入**（echarts/core + 仅柱状图/直角坐标系/提示框/Canvas 渲染器），
 * 相比 `import * as echarts from 'echarts'` 全量引入，打包产物体积小很多。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

import { getBeauticianRank, getRepurchase, getRevenue, getServiceRank } from '@/api/stat'
import { formatAmount, formatDecimal, formatPercent, todayString } from '@/utils/format'

// 注册用到的 echarts 模块
echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

/** 统计粒度选项 */
const REVENUE_TYPES = [
  { label: '日', value: 'day', hint: '当天' },
  { label: '周', value: 'week', hint: '所在自然周（周一至周日）' },
  { label: '月', value: 'month', hint: '所在自然月' }
]

/** 0~1 比率 → el-progress 需要的 0~100 数值 */
function ratioPercent(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return 0
  }
  return Math.min(100, Math.max(0, Number((number * 100).toFixed(2))))
}

/** 排名前三高亮 */
function rankClass(index) {
  return index < 3 ? `is-top${index + 1}` : ''
}

// ---------------- 1. 营业额统计 ----------------

const revenueLoading = ref(false)
const revenue = ref({ dates: [], revenues: [], totalAmount: 0 })

const revenueQuery = reactive({
  type: 'day',
  // 基准日期：不传即今天，这里显式带上便于用户回看历史
  date: todayString()
})

const currentType = computed(
  () => REVENUE_TYPES.find((item) => item.value === revenueQuery.type) || REVENUE_TYPES[0]
)

/** 区间说明：单日直接显示日期，多日显示「起 ~ 止」 */
const revenueRangeText = computed(() => {
  const dates = revenue.value?.dates || []
  if (!dates.length) {
    return '暂无数据'
  }
  return dates.length === 1 ? dates[0] : `${dates[0]} ~ ${dates[dates.length - 1]}`
})

const hasRevenueData = computed(() => (revenue.value?.dates || []).length > 0)

const chartRef = ref(null)
/** echarts 实例（非响应式，避免被 Vue 代理影响性能） */
let chart = null

/**
 * 渲染 / 更新柱状图
 */
function renderChart() {
  if (!chartRef.value) {
    return
  }
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const dates = revenue.value?.dates || []
  const revenues = (revenue.value?.revenues || []).map((item) => Number(item || 0))

  chart.setOption(
    {
      grid: { top: 28, left: 8, right: 16, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params) => {
          const item = params?.[0]
          return item ? `${item.name}<br/>营业额：￥${formatAmount(item.value)}` : ''
        }
      },
      xAxis: {
        type: 'category',
        data: dates,
        axisTick: { alignWithLabel: true },
        // 只显示 MM-DD，月模式下 30+ 个标签也不会过挤（echarts 会自动抽稀）
        axisLabel: { formatter: (value) => String(value).slice(5) }
      },
      yAxis: {
        type: 'value',
        axisLabel: { formatter: (value) => `￥${value}` }
      },
      series: [
        {
          name: '营业额',
          type: 'bar',
          barMaxWidth: 28,
          itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
          data: revenues
        }
      ]
    },
    // 切换粒度时数据长度变化较大，不用合并旧配置，避免残留上一个区间的坐标轴
    true
  )
  chart.resize()
}

async function loadRevenue() {
  revenueLoading.value = true
  try {
    const data = await getRevenue({
      date: revenueQuery.date || undefined,
      type: revenueQuery.type
    })
    revenue.value = data || { dates: [], revenues: [], totalAmount: 0 }
    await nextTick()
    renderChart()
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    revenueLoading.value = false
  }
}

// 切换日 / 周 / 月 或 换基准日期后重新查询
watch(() => [revenueQuery.type, revenueQuery.date], loadRevenue)

// ---------------- 2. 服务销量排行 ----------------

const serviceLoading = ref(false)
const serviceRank = ref([])

async function loadServiceRank() {
  serviceLoading.value = true
  try {
    const data = await getServiceRank()
    serviceRank.value = Array.isArray(data) ? data : []
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    serviceLoading.value = false
  }
}

// ---------------- 3. 美容师业绩排行 ----------------

const beauticianLoading = ref(false)
const beauticianRank = ref([])

async function loadBeauticianRank() {
  beauticianLoading.value = true
  try {
    const data = await getBeauticianRank()
    beauticianRank.value = Array.isArray(data) ? data : []
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    beauticianLoading.value = false
  }
}

// ---------------- 4. 用户复购率 ----------------

const repurchaseLoading = ref(false)
const repurchase = ref({ repeatUsers: 0, totalUsers: 0, repurchaseRate: 0 })

async function loadRepurchase() {
  repurchaseLoading.value = true
  try {
    const data = await getRepurchase()
    repurchase.value = data || { repeatUsers: 0, totalUsers: 0, repurchaseRate: 0 }
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    repurchaseLoading.value = false
  }
}

// ---------------- 生命周期 ----------------

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  // 四个卡片数据互不依赖，并行加载
  loadRevenue()
  loadServiceRank()
  loadBeauticianRank()
  loadRepurchase()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page-container">
    <!-- 统计口径说明 -->
    <el-alert
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="统计口径：仅计入「已完成」订单（含待评价），营业额按支付时间归入自然日；排行与复购率为全量统计。"
    />

    <el-row :gutter="16">
      <!-- 1. 营业额统计 -->
      <el-col :xs="24" :lg="12" class="stat-col">
        <el-card shadow="never" class="stat-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">营业额统计</span>
              <div class="card-tools">
                <el-radio-group v-model="revenueQuery.type" size="small">
                  <el-radio-button
                    v-for="item in REVENUE_TYPES"
                    :key="item.value"
                    :value="item.value"
                  >
                    {{ item.label }}
                  </el-radio-button>
                </el-radio-group>
                <el-date-picker
                  v-model="revenueQuery.date"
                  type="date"
                  size="small"
                  :editable="false"
                  placeholder="选择基准日期"
                  value-format="YYYY-MM-DD"
                  format="YYYY-MM-DD"
                  :clearable="false"
                  style="width: 150px"
                />
              </div>
            </div>
          </template>

          <div class="revenue-total">
            <span class="revenue-label">{{ currentType.label }}营业额（{{ currentType.hint }}）</span>
            <span class="revenue-value">￥{{ formatAmount(revenue.totalAmount) }}</span>
            <span class="revenue-range">{{ revenueRangeText }}</span>
          </div>

          <div v-loading="revenueLoading" class="chart-wrapper">
            <div ref="chartRef" class="chart"></div>
            <div v-if="!hasRevenueData && !revenueLoading" class="chart-empty">暂无营业额数据</div>
          </div>
        </el-card>
      </el-col>

      <!-- 2. 服务销量排行 -->
      <el-col :xs="24" :lg="12" class="stat-col">
        <el-card shadow="never" class="stat-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">服务销量排行 Top10</span>
              <span class="card-hint">按销量降序</span>
            </div>
          </template>

          <el-table
            v-loading="serviceLoading"
            :data="serviceRank"
            border
            stripe
            height="352"
            empty-text="暂无销量数据"
          >
            <el-table-column label="排名" width="70" align="center">
              <template #default="{ $index }">
                <span class="rank-badge" :class="rankClass($index)">{{ $index + 1 }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="name" label="服务名称" min-width="150" show-overflow-tooltip />

            <el-table-column prop="sales" label="销量" width="90" align="center" />

            <el-table-column label="占比" min-width="180">
              <template #default="{ row }">
                <div class="ratio-cell">
                  <el-progress
                    :percentage="ratioPercent(row.ratio)"
                    :stroke-width="10"
                    :show-text="false"
                    class="ratio-bar"
                  />
                  <span class="ratio-text">{{ formatPercent(row.ratio) }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 3. 美容师业绩排行 -->
      <el-col :xs="24" :lg="12" class="stat-col">
        <el-card shadow="never" class="stat-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">美容师业绩排行</span>
              <span class="card-hint">按营业额降序</span>
            </div>
          </template>

          <el-table
            v-loading="beauticianLoading"
            :data="beauticianRank"
            border
            stripe
            height="352"
            empty-text="暂无业绩数据"
          >
            <el-table-column label="排名" width="70" align="center">
              <template #default="{ $index }">
                <span class="rank-badge" :class="rankClass($index)">{{ $index + 1 }}</span>
              </template>
            </el-table-column>

            <el-table-column label="姓名" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.name">{{ row.name }}</span>
                <span v-else class="text-muted">已删除美容师</span>
              </template>
            </el-table-column>

            <el-table-column prop="orderCount" label="接单量" width="90" align="center" />

            <el-table-column label="营业额" width="120" align="right">
              <template #default="{ row }">￥{{ formatAmount(row.amount) }}</template>
            </el-table-column>

            <el-table-column label="评分" width="110" align="center">
              <template #default="{ row }">
                <template v-if="row.score !== null && row.score !== undefined">
                  <span class="score-star">★</span>
                  <span>{{ formatDecimal(row.score, 2) }}</span>
                </template>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 4. 用户复购率 -->
      <el-col :xs="24" :lg="12" class="stat-col">
        <el-card shadow="never" class="stat-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">用户复购率</span>
              <span class="card-hint">复购 = 已完成订单 ≥ 2 笔</span>
            </div>
          </template>

          <div v-loading="repurchaseLoading" class="repurchase-body">
            <el-row :gutter="16">
              <el-col :span="8">
                <div class="metric">
                  <div class="metric-value">{{ repurchase.repeatUsers ?? 0 }}</div>
                  <div class="metric-label">复购用户数</div>
                </div>
              </el-col>
              <el-col :span="8">
                <div class="metric">
                  <div class="metric-value">{{ repurchase.totalUsers ?? 0 }}</div>
                  <div class="metric-label">总用户数</div>
                </div>
              </el-col>
              <el-col :span="8">
                <div class="metric">
                  <div class="metric-value highlight">
                    {{ formatPercent(repurchase.repurchaseRate) }}
                  </div>
                  <div class="metric-label">复购率</div>
                </div>
              </el-col>
            </el-row>

            <el-progress
              class="repurchase-bar"
              :percentage="ratioPercent(repurchase.repurchaseRate)"
              :stroke-width="14"
              :text-inside="true"
            />

            <div class="repurchase-hint">
              复购率 = 复购用户数 / 总用户数（总用户数指有过已完成订单的用户）
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 16px;
}

.stat-col {
  margin-bottom: 16px;
}

.stat-card {
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

.card-tools {
  display: flex;
  gap: 8px;
  align-items: center;
}

.card-hint {
  font-size: 12px;
  color: #909399;
}

/* ---------------- 营业额 ---------------- */

.revenue-total {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}

.revenue-label {
  font-size: 13px;
  color: #909399;
}

.revenue-value {
  font-size: 26px;
  font-weight: 600;
  color: #409eff;
}

.revenue-range {
  font-size: 12px;
  color: #909399;
}

.chart-wrapper {
  position: relative;
  min-height: 280px;
}

.chart {
  width: 100%;
  height: 280px;
}

.chart-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  font-size: 13px;
  color: #909399;
  transform: translate(-50%, -50%);
}

/* ---------------- 排行榜 ---------------- */

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
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

.ratio-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ratio-bar {
  flex: 1;
}

.ratio-text {
  width: 52px;
  font-size: 12px;
  color: #606266;
  text-align: right;
}

.score-star {
  margin-right: 2px;
  color: #ff9900;
}

.text-muted {
  font-size: 12px;
  color: #909399;
}

/* ---------------- 复购率 ---------------- */

.repurchase-body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
  min-height: 280px;
}

.metric {
  padding: 8px 0;
  text-align: center;
}

.metric-value {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.3;
  color: #303133;
}

.metric-value.highlight {
  color: #409eff;
}

.metric-label {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.repurchase-bar {
  margin: 22px 0 10px;
}

.repurchase-hint {
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
