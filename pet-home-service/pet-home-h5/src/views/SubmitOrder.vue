<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { listUserAddresses } from '@/api/address'
import { listUserBeauticians } from '@/api/beautician'
import { listCart } from '@/api/cart'
import { submitOrder } from '@/api/order'
import { listUserPets } from '@/api/pet'
import { getUserServiceDetail } from '@/api/service'
import { formatPrice } from '@/utils/format'

/**
 * 提交预约订单
 *
 * <p>接口：POST /api/user/order/submit，请求
 * { addressId, petId, beauticianId, appointmentDate, timeSlot, items:[{serviceId,quantity}], remark }</p>
 *
 * <p>两个入口：</p>
 * <ul>
 *   <li>服务详情页「立即预约」→ ?serviceId=xx（单服务，数量 1）；</li>
 *   <li>预约车「去结算」→ ?serviceIds=a,b（数量以预约车为准）。</li>
 * </ul>
 *
 * <p>失败提示（如「该时段不可约」「该时段已被预约」）由 request 拦截器统一弹出，
 * 本页停留在原处，方便用户换时段重试。</p>
 */

/** 固定时段，与后端 TimeSlot 枚举保持一致 */
const TIME_SLOTS = ['10:00-12:00', '12:00-14:00', '14:00-16:00', '16:00-18:00', '18:00-20:00']

/** 备注长度上限，与后端 OrderSubmitRequest 一致 */
const MAX_REMARK_LENGTH = 200

const route = useRoute()
const router = useRouter()

/** 入口 A：单个服务 id */
const singleServiceId = computed(() => (route.query.serviceId ? Number(route.query.serviceId) : null))

/** 入口 B：预约车结算带入的选中服务 id 列表 */
const cartServiceIds = computed(() =>
  String(route.query.serviceIds || '')
    .split(',')
    .map((id) => Number(id.trim()))
    .filter((id) => Number.isFinite(id) && id > 0)
)

const loading = ref(true)
const submitting = ref(false)

const addresses = ref([])
const pets = ref([])
const beauticians = ref([])

/** 下单服务项：{ serviceId, name, price, quantity } */
const items = ref([])

const form = ref({
  addressId: null,
  petId: null,
  beauticianId: null,
  appointmentDate: '',
  timeSlot: '',
  remark: ''
})

/** 地址 / 宠物选择弹层 */
const addressDrawer = ref(false)
const petDrawer = ref(false)

const totalAmount = computed(() =>
  items.value.reduce((sum, item) => sum + Number(item.price || 0) * item.quantity, 0)
)

const currentAddress = computed(
  () => addresses.value.find((item) => item.id === form.value.addressId) || null
)
const currentPet = computed(() => pets.value.find((item) => item.id === form.value.petId) || null)
const currentBeautician = computed(
  () => beauticians.value.find((item) => item.id === form.value.beauticianId) || null
)

/** 不可选择今天之前的日期（后端限制预约日期不早于今天） */
function disabledDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  // 并行加载，单个失败不影响其它区块（提示由拦截器统一弹出）
  await Promise.allSettled([loadAddresses(), loadPets(), loadBeauticians(), loadItems()])
  loading.value = false
}

async function loadAddresses() {
  const list = (await listUserAddresses()) || []
  addresses.value = list
  if (list.length && !form.value.addressId) {
    // 默认选中默认地址（后端已把默认地址排最前）
    const preferred = list.find((item) => item.isDefault === 1) || list[0]
    form.value.addressId = preferred.id
  }
}

async function loadPets() {
  const list = (await listUserPets()) || []
  pets.value = list
  // 只有一只宠物时自动选中；多只时交由用户选择
  if (list.length === 1 && !form.value.petId) {
    form.value.petId = list[0].id
  }
}

async function loadBeauticians() {
  const list = (await listUserBeauticians()) || []
  beauticians.value = list
  // 默认选中评分最高的美容师（列表已按评分排序），可自行更换
  if (list.length && !form.value.beauticianId) {
    form.value.beauticianId = list[0].id
  }
}

async function loadItems() {
  if (cartServiceIds.value.length) {
    // 预约车结算：服务项与数量以预约车为准
    const cart = (await listCart()) || []
    items.value = cart
      .filter((row) => cartServiceIds.value.includes(row.serviceId))
      .map((row) => ({
        serviceId: row.serviceId,
        name: row.name,
        price: row.price,
        quantity: Number(row.quantity) || 1
      }))
    return
  }

  if (singleServiceId.value) {
    const detail = await getUserServiceDetail(singleServiceId.value)
    if (detail) {
      items.value = [
        { serviceId: detail.id, name: detail.name, price: detail.price, quantity: 1 }
      ]
    }
  }
}

function selectAddress(address) {
  form.value.addressId = address.id
  addressDrawer.value = false
}

function selectPet(pet) {
  form.value.petId = pet.id
  petDrawer.value = false
}

function goServiceList() {
  router.push('/service')
}

/**
 * 提交预约
 */
async function handleSubmit() {
  if (!items.value.length) {
    ElMessage.warning('服务清单为空，请先选购服务')
    return
  }
  if (!form.value.addressId) {
    ElMessage.warning('请选择服务地址')
    return
  }
  if (!form.value.petId) {
    ElMessage.warning('请选择要服务的宠物')
    return
  }
  if (!form.value.appointmentDate) {
    ElMessage.warning('请选择预约日期')
    return
  }
  if (!form.value.timeSlot) {
    ElMessage.warning('请选择预约时段')
    return
  }
  if (!form.value.beauticianId) {
    ElMessage.warning('请选择美容师')
    return
  }

  submitting.value = true
  try {
    const data = await submitOrder({
      addressId: form.value.addressId,
      petId: form.value.petId,
      beauticianId: form.value.beauticianId,
      appointmentDate: form.value.appointmentDate,
      timeSlot: form.value.timeSlot,
      items: items.value.map((item) => ({
        serviceId: item.serviceId,
        quantity: item.quantity
      })),
      remark: form.value.remark.trim() || null
    })

    ElMessage.success(`预约提交成功，订单号 ${data.orderNumber}`)
    // 下单成功后后端已自动清空预约车，直接进「我的订单」
    router.replace('/order')
  } catch (e) {
    // 如「该时段已被预约」：提示已由拦截器弹出，留在本页便于更换时段
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="submit h5-page">
    <!-- 加载骨架 -->
    <div v-if="loading" class="submit__skeleton">
      <div v-for="n in 3" :key="n" class="h5-card">
        <div class="submit__line"></div>
        <div class="submit__line submit__line--short"></div>
      </div>
    </div>

    <template v-else>
      <!-- 1. 服务地址 -->
      <div class="h5-card submit__card h5-tap" @click="addressDrawer = true">
        <div class="submit__card-head">
          <span class="submit__card-title">服务地址</span>
          <el-icon color="#c0c4cc"><ArrowRight /></el-icon>
        </div>

        <div v-if="currentAddress" class="submit__address">
          <p class="submit__address-line">
            <span class="submit__address-name">{{ currentAddress.consignee }}</span>
            <span class="submit__address-phone">{{ currentAddress.phone }}</span>
            <span v-if="currentAddress.isDefault === 1" class="submit__badge">默认</span>
          </p>
          <p class="submit__address-detail">{{ currentAddress.fullAddress }}</p>
        </div>
        <p v-else class="submit__empty">还没有服务地址，点击选择</p>
      </div>

      <!-- 2. 宠物 -->
      <div class="h5-card submit__card h5-tap" @click="petDrawer = true">
        <div class="submit__card-head">
          <span class="submit__card-title">服务宠物</span>
          <el-icon color="#c0c4cc"><ArrowRight /></el-icon>
        </div>

        <div v-if="currentPet" class="submit__pet">
          <span class="submit__pet-name">{{ currentPet.name }}</span>
          <span v-if="currentPet.breed" class="submit__pet-breed">{{ currentPet.breed }}</span>
        </div>
        <p v-else class="submit__empty">还没有宠物档案，点击选择</p>
      </div>

      <!-- 3. 美容师 -->
      <div class="h5-card submit__card">
        <div class="submit__card-head">
          <span class="submit__card-title">美容师</span>
          <span class="submit__hint">默认评分最高，可自行更换</span>
        </div>

        <el-select v-model="form.beauticianId" placeholder="请选择美容师" style="width: 100%">
          <el-option
            v-for="item in beauticians"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          >
            <span>{{ item.name }}</span>
            <span class="submit__option-score">★ {{ item.score ?? '--' }}</span>
          </el-option>
        </el-select>
        <p v-if="!beauticians.length" class="submit__empty">暂无可选美容师</p>
      </div>

      <!-- 4. 预约时间 -->
      <div class="h5-card submit__card">
        <div class="submit__card-head">
          <span class="submit__card-title">预约时间</span>
        </div>

        <el-date-picker
          v-model="form.appointmentDate"
          class="submit__date"
          type="date"
          placeholder="请选择预约日期"
          value-format="YYYY-MM-DD"
          :disabled-date="disabledDate"
        />

        <div class="submit__slots">
          <span
            v-for="slot in TIME_SLOTS"
            :key="slot"
            class="submit__slot h5-tap"
            :class="{ 'is-active': form.timeSlot === slot }"
            @click="form.timeSlot = slot"
          >
            {{ slot }}
          </span>
        </div>
        <p class="submit__hint submit__hint--block">
          时段是否可约以提交结果为准，若已被预约可换个时段再试
        </p>
      </div>

      <!-- 5. 服务清单 -->
      <div class="h5-card submit__card">
        <div class="submit__card-head">
          <span class="submit__card-title">服务清单</span>
          <span class="submit__hint">共 {{ items.length }} 项</span>
        </div>

        <div v-if="items.length" class="submit__items">
          <div v-for="item in items" :key="item.serviceId" class="submit__item">
            <span class="submit__item-name">{{ item.name }}</span>
            <span class="submit__item-qty">×{{ item.quantity }}</span>
            <span class="submit__item-amount">
              {{ formatPrice(Number(item.price || 0) * item.quantity) }}
            </span>
          </div>
        </div>

        <div v-else class="submit__empty-block">
          <p class="submit__empty">服务清单为空</p>
          <el-button size="small" round @click="goServiceList">去选服务</el-button>
        </div>
      </div>

      <!-- 6. 备注 -->
      <div class="h5-card submit__card">
        <div class="submit__card-head">
          <span class="submit__card-title">备注</span>
        </div>
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          resize="none"
          :maxlength="MAX_REMARK_LENGTH"
          show-word-limit
          placeholder="宠物性格、上门注意事项等（选填）"
        />
      </div>

      <!-- 7. 底部固定栏 -->
      <div class="submit__bar">
        <div class="submit__total">
          <span class="submit__total-label">合计</span>
          <span class="submit__total-amount">{{ formatPrice(totalAmount) }}</span>
        </div>
        <el-button
          class="submit__confirm"
          type="primary"
          round
          :loading="submitting"
          @click="handleSubmit"
        >
          提交预约
        </el-button>
      </div>
    </template>

    <!-- 地址选择弹层 -->
    <el-drawer v-model="addressDrawer" title="选择服务地址" direction="btt" size="60%">
      <div v-if="addresses.length" class="submit__drawer">
        <div
          v-for="item in addresses"
          :key="item.id"
          class="submit__drawer-item h5-tap"
          :class="{ 'is-active': form.addressId === item.id }"
          @click="selectAddress(item)"
        >
          <div class="submit__address">
            <p class="submit__address-line">
              <span class="submit__address-name">{{ item.consignee }}</span>
              <span class="submit__address-phone">{{ item.phone }}</span>
              <span v-if="item.isDefault === 1" class="submit__badge">默认</span>
            </p>
            <p class="submit__address-detail">{{ item.fullAddress }}</p>
          </div>
          <el-icon v-if="form.addressId === item.id" color="#409eff"><Select /></el-icon>
        </div>
      </div>
      <el-empty v-else :image-size="80" description="暂无地址" />
    </el-drawer>

    <!-- 宠物选择弹层 -->
    <el-drawer v-model="petDrawer" title="选择宠物" direction="btt" size="50%">
      <div v-if="pets.length" class="submit__drawer">
        <div
          v-for="item in pets"
          :key="item.id"
          class="submit__drawer-item h5-tap"
          :class="{ 'is-active': form.petId === item.id }"
          @click="selectPet(item)"
        >
          <div>
            <p class="submit__pet-name">{{ item.name }}</p>
            <p class="submit__pet-breed">{{ item.breed || '品种未知' }}</p>
          </div>
          <el-icon v-if="form.petId === item.id" color="#409eff"><Select /></el-icon>
        </div>
      </div>
      <el-empty v-else :image-size="80" description="暂无宠物档案" />
    </el-drawer>
  </div>
</template>

<style scoped>
/* 给底部固定栏留出空间 */
.submit {
  padding-bottom: 72px;
}

/* ---------------- 卡片通用 ---------------- */
.submit__card {
  padding: 12px;
}

.submit__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.submit__card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.submit__hint {
  font-size: 12px;
  font-weight: 400;
  color: #c0c4cc;
}

.submit__hint--block {
  display: block;
  margin-top: 8px;
  line-height: 1.5;
}

.submit__empty {
  font-size: 13px;
  color: #909399;
}

.submit__empty-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
}

/* ---------------- 地址 ---------------- */
.submit__address-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.submit__address-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.submit__address-phone {
  font-size: 13px;
  color: #606266;
}

.submit__badge {
  padding: 0 6px;
  font-size: 11px;
  color: #409eff;
  background-color: rgba(64, 158, 255, 0.1);
  border-radius: 4px;
}

.submit__address-detail {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.5;
  color: #606266;
}

/* ---------------- 宠物 ---------------- */
.submit__pet {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.submit__pet-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.submit__pet-breed {
  font-size: 13px;
  color: #909399;
}

/* ---------------- 美容师 ---------------- */
.submit__option-score {
  float: right;
  color: #e6a23c;
  font-size: 12px;
}

/* ---------------- 预约时间 ---------------- */
.submit__date {
  width: 100%;
}

.submit__slots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.submit__slot {
  padding: 6px 12px;
  font-size: 13px;
  color: #606266;
  background-color: #f5f6f8;
  border-radius: 16px;
}

.submit__slot.is-active {
  color: #fff;
  background-color: var(--el-color-primary);
}

/* ---------------- 服务清单 ---------------- */
.submit__items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.submit__item {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.submit__item-name {
  flex: 1;
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.submit__item-qty {
  margin-left: 8px;
  color: #909399;
}

.submit__item-amount {
  width: 76px;
  margin-left: 8px;
  font-weight: 600;
  color: #f56c6c;
  text-align: right;
}

/* ---------------- 底部固定栏 ---------------- */
.submit__bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px calc(10px + var(--safe-area-bottom));
  background-color: #fff;
  border-top: 1px solid #f0f0f0;
}

.submit__total {
  display: flex;
  align-items: baseline;
}

.submit__total-label {
  font-size: 12px;
  color: #909399;
}

.submit__total-amount {
  margin-left: 4px;
  font-size: 18px;
  font-weight: 600;
  color: #f56c6c;
}

.submit__confirm {
  flex-shrink: 0;
  height: 40px;
  padding: 0 24px;
}

/* ---------------- 选择弹层 ---------------- */
.submit__drawer {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.submit__drawer-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
}

.submit__drawer-item.is-active {
  border-color: var(--el-color-primary);
  background-color: rgba(64, 158, 255, 0.06);
}

/* ---------------- 骨架屏 ---------------- */
.submit__line {
  height: 13px;
  margin-bottom: 10px;
  background-color: #f0f2f5;
  border-radius: 6px;
}

.submit__line--short {
  width: 55%;
}
</style>
