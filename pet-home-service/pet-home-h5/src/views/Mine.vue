<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

import {
  createUserAddress,
  deleteUserAddress,
  listUserAddresses,
  setDefaultAddress,
  updateUserAddress
} from '@/api/address'
import { getProfile, logout } from '@/api/user'
import { clearAuth, getUserName, setUserName } from '@/utils/auth'

/**
 * 个人中心
 *
 * <p>已接通接口：GET /user/profile（用户信息，含头像）、POST /user/logout（退出登录）、
 * GET/POST/PUT/DELETE /user/address（地址增删改查与设为默认）。</p>
 */

const router = useRouter()

/** 用户信息（id / name / phone / sex / avatar） */
const profile = ref({})
/** 用户名：先用本地缓存展示，进页面后用 profile 刷新 */
const userName = ref(getUserName())

const loading = ref(true)
const addressDrawer = ref(false)
const addresses = ref([])
const aboutVisible = ref(false)

/** ---------------- 地址表单 ---------------- */
const addressFormRef = ref()
const addressFormVisible = ref(false)
const addressSaving = ref(false)
/** null 表示新增，否则为正在编辑的地址 id */
const editingId = ref(null)

const addressForm = reactive({
  consignee: '',
  phone: '',
  sex: 0,
  provinceName: '',
  cityName: '',
  districtName: '',
  detail: '',
  label: '',
  isDefault: false
})

const addressRules = {
  consignee: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

/** 应用版本（与 package.json 保持一致） */
const APP_VERSION = '1.0.0'

/** 功能菜单 */
const menus = [
  { key: 'pet', title: '宠物档案', icon: 'Document', action: () => router.push('/pet') },
  { key: 'address', title: '我的地址', icon: 'Location', action: openAddress },
  { key: 'order', title: '我的订单', icon: 'Tickets', action: () => router.push('/order') },
  {
    key: 'service',
    title: '联系客服',
    icon: 'Service',
    action: () => ElMessage.info('联系客服功能开发中，敬请期待')
  },
  { key: 'about', title: '关于我们', icon: 'InfoFilled', action: () => (aboutVisible.value = true) }
]

onMounted(loadProfile)

async function loadProfile() {
  loading.value = true
  try {
    const data = await getProfile()
    profile.value = data || {}
    userName.value = data?.name || userName.value
    setUserName(data?.name)
  } catch (e) {
    // 提示由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

/** 打开地址抽屉并加载列表 */
async function openAddress() {
  addressDrawer.value = true
  await refreshAddresses()
}

async function refreshAddresses() {
  try {
    addresses.value = (await listUserAddresses()) || []
  } catch (e) {
    addresses.value = []
  }
}

function openCreateAddress() {
  editingId.value = null
  Object.assign(addressForm, {
    consignee: profile.value.name || '',
    phone: profile.value.phone || '',
    sex: 0,
    provinceName: '',
    cityName: '',
    districtName: '',
    detail: '',
    label: '',
    // 第一个地址自动设为默认，与后端逻辑一致
    isDefault: addresses.value.length === 0
  })
  addressFormRef.value?.clearValidate()
  addressFormVisible.value = true
}

function openEditAddress(item) {
  editingId.value = item.id
  Object.assign(addressForm, {
    consignee: item.consignee || '',
    phone: item.phone || '',
    sex: 0,
    provinceName: item.provinceName || '',
    cityName: item.cityName || '',
    districtName: item.districtName || '',
    detail: item.detail || '',
    label: item.label || '',
    isDefault: item.isDefault === 1
  })
  addressFormRef.value?.clearValidate()
  addressFormVisible.value = true
}

async function submitAddress() {
  try {
    await addressFormRef.value.validate()
  } catch (e) {
    return
  }

  addressSaving.value = true
  try {
    if (editingId.value) {
      await updateUserAddress(editingId.value, { ...addressForm })
      ElMessage.success('地址已更新')
    } else {
      await createUserAddress({ ...addressForm })
      ElMessage.success('地址已新增')
    }
    addressFormVisible.value = false
    await refreshAddresses()
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    addressSaving.value = false
  }
}

async function handleSetDefault(item) {
  try {
    await setDefaultAddress(item.id)
    ElMessage.success('已设为默认地址')
    await refreshAddresses()
  } catch (e) {
    // 忽略
  }
}

async function handleDeleteAddress(item) {
  try {
    await ElMessageBox.confirm(`确定删除「${item.consignee} ${item.fullAddress}」吗？`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteUserAddress(item.id)
    ElMessage.success('地址已删除')
    await refreshAddresses()
  } catch (e) {
    // 忽略
  }
}

/** 退出登录：清除本地登录态并回登录页 */
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  try {
    // 后端吊销 token（即便失败，本地也要清理）
    await logout()
  } catch (e) {
    // 忽略：登录态已失效时同样允许退出
  }
  clearAuth()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="mine h5-page">
    <!-- 1. 顶部用户信息区 -->
    <div class="mine__profile">
      <el-avatar class="mine__avatar" :size="56" :src="profile.avatar || undefined">
        <el-icon :size="26"><UserFilled /></el-icon>
      </el-avatar>

      <div class="mine__info">
        <p class="mine__name">{{ loading ? '加载中…' : userName || '宠物家长' }}</p>
        <p class="mine__phone">{{ profile.phone || '未绑定手机号' }}</p>
      </div>
    </div>

    <!-- 2. 功能菜单 -->
    <div class="h5-card mine__menus">
      <div
        v-for="item in menus"
        :key="item.key"
        class="mine__menu h5-tap"
        @click="item.action()"
      >
        <span class="mine__menu-title">
          <el-icon :size="18" color="#409eff"><component :is="item.icon" /></el-icon>
          {{ item.title }}
        </span>
        <el-icon class="mine__menu-arrow" color="#c0c4cc"><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- 3. 退出登录 -->
    <el-button class="mine__logout" size="large" @click="handleLogout">退出登录</el-button>

    <!-- 我的地址 -->
    <el-drawer v-model="addressDrawer" title="我的地址" direction="btt" size="70%">
      <div class="mine__address-head">
        <span class="mine__address-count">共 {{ addresses.length }} 个地址</span>
        <el-button type="primary" size="small" round @click="openCreateAddress">
          <el-icon><Plus /></el-icon>
          新增地址
        </el-button>
      </div>

      <div v-if="addresses.length" class="mine__addresses">
        <div v-for="item in addresses" :key="item.id" class="mine__address">
          <p class="mine__address-line">
            <span class="mine__address-name">{{ item.consignee }}</span>
            <span class="mine__address-phone">{{ item.phone }}</span>
            <span v-if="item.isDefault === 1" class="mine__badge">默认</span>
            <span v-if="item.label" class="mine__badge mine__badge--label">{{ item.label }}</span>
          </p>
          <p class="mine__address-detail">{{ item.fullAddress }}</p>
          <p class="mine__address-actions">
            <span
              v-if="item.isDefault !== 1"
              class="mine__address-action h5-tap"
              @click="handleSetDefault(item)"
            >
              设为默认
            </span>
            <span class="mine__address-action h5-tap" @click="openEditAddress(item)">编辑</span>
            <span
              class="mine__address-action mine__address-action--danger h5-tap"
              @click="handleDeleteAddress(item)"
            >
              删除
            </span>
          </p>
        </div>
      </div>
      <el-empty v-else :image-size="80" description="暂无地址，点击上方新增" />
    </el-drawer>

    <!-- 新增 / 编辑地址 -->
    <el-dialog
      v-model="addressFormVisible"
      :title="editingId ? '编辑地址' : '新增地址'"
      width="92%"
      class="mine__address-dialog"
    >
      <el-form ref="addressFormRef" :model="addressForm" :rules="addressRules" label-width="72px">
        <el-form-item label="联系人" prop="consignee">
          <el-input v-model="addressForm.consignee" maxlength="50" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="addressForm.phone" type="tel" maxlength="11" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="所在地区">
          <div class="mine__region">
            <el-input v-model="addressForm.provinceName" maxlength="50" placeholder="省" />
            <el-input v-model="addressForm.cityName" maxlength="50" placeholder="市" />
            <el-input v-model="addressForm.districtName" maxlength="50" placeholder="区/县" />
          </div>
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input
            v-model="addressForm.detail"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="街道、门牌号等"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="addressForm.label" maxlength="50" placeholder="家 / 公司（选填）" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="addressForm.isDefault" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="addressFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="addressSaving" @click="submitAddress">保存</el-button>
      </template>
    </el-dialog>

    <!-- 关于我们 -->
    <el-dialog v-model="aboutVisible" title="关于我们" width="80%">
      <div class="mine__about">
        <div class="mine__about-logo">
          <el-icon :size="30"><HomeFilled /></el-icon>
        </div>
        <p class="mine__about-name">宠享到家</p>
        <p class="mine__about-desc">宠物上门服务平台</p>
        <p class="mine__about-version">版本 {{ APP_VERSION }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ---------------- 用户信息区 ---------------- */
.mine__profile {
  display: flex;
  align-items: center;
  padding: 18px 16px;
  margin-bottom: 12px;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #79bbff);
  border-radius: 10px;
}

.mine__avatar {
  flex: 0 0 auto;
  margin-right: 12px;
  color: #409eff;
  background-color: #fff;
}

.mine__info {
  flex: 1;
  min-width: 0;
}

.mine__name {
  overflow: hidden;
  font-size: 18px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mine__phone {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.9;
}

/* ---------------- 功能菜单 ---------------- */
.mine__menus {
  padding: 0 12px;
}

.mine__menu {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 50px;
  font-size: 14px;
  color: #303133;
}

.mine__menu + .mine__menu {
  border-top: 1px solid #f5f6f8;
}

.mine__menu-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mine__menu-arrow {
  font-size: 16px;
}

/* ---------------- 退出登录 ---------------- */
.mine__logout {
  width: 100%;
  height: 46px;
  margin-top: 24px;
  color: #f56c6c;
  border-color: #fbc4c4;
}

/* ---------------- 我的地址 ---------------- */
.mine__address-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.mine__address-count {
  font-size: 13px;
  color: #909399;
}

.mine__addresses {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mine__address {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
}

.mine__address-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mine__address-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.mine__address-phone {
  font-size: 13px;
  color: #606266;
}

.mine__badge {
  padding: 0 6px;
  font-size: 11px;
  color: #409eff;
  background-color: rgba(64, 158, 255, 0.1);
  border-radius: 4px;
}

.mine__badge--label {
  color: #909399;
  background-color: #f4f4f5;
}

.mine__address-detail {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: #606266;
}

.mine__address-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
}

.mine__address-action {
  font-size: 13px;
  color: #409eff;
}

.mine__address-action--danger {
  color: #f56c6c;
}

/* 地址表单：省市区三列 */
.mine__region {
  display: flex;
  gap: 8px;
  width: 100%;
}

.mine__region :deep(.el-input) {
  flex: 1;
}

/* ---------------- 关于我们 ---------------- */
.mine__about {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 6px 0 12px;
}

.mine__about-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #79bbff);
  border-radius: 16px;
}

.mine__about-name {
  margin-top: 12px;
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.mine__about-desc {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.mine__about-version {
  margin-top: 10px;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
