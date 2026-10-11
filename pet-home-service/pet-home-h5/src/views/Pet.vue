<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { addPet, deletePet, listUserPets, updatePet } from '@/api/pet'

/**
 * 宠物档案
 *
 * <p>接口：GET /user/pet/list、POST /user/pet/add、PUT /user/pet/update、DELETE /user/pet/{id}</p>
 * <ul>
 *   <li>宠物名必填（≤50），且同一用户名下不能重名（后端会提示「宠物名称已存在」）；</li>
 *   <li>生日不能晚于今天；体重 0.01~999.99kg 且最多 2 位小数；疫苗/备注 ≤255；</li>
 *   <li>删除：宠物存在未完成订单时后端会拒绝并说明数量。</li>
 * </ul>
 *
 * <p>宠物数据本身没有头像字段，卡片头像用宠物名首字生成。</p>
 */

/** 性别选项（与后端一致：1公 2母；0 未知不提供选择，历史数据按未知展示） */
const GENDERS = [
  { label: '公', value: 1 },
  { label: '母', value: 2 }
]

const pets = ref([])
const loading = ref(true)

const dialogVisible = ref(false)
const submitting = ref(false)
/** 正在编辑的宠物 id；null 表示新增 */
const editingId = ref(null)
const formRef = ref()

const form = reactive({
  name: '',
  breed: '',
  gender: 0,
  birthday: '',
  weight: null,
  vaccine: '',
  remark: ''
})

/** 表单校验：与后端 PetProfileAddRequest / UpdateRequest 保持一致 */
const rules = {
  name: [
    { required: true, message: '请输入宠物名', trigger: 'blur' },
    { max: 50, message: '宠物名长度不能超过 50', trigger: 'blur' }
  ],
  breed: [{ max: 50, message: '品种长度不能超过 50', trigger: 'blur' }],
  weight: [
    {
      type: 'number',
      min: 0.01,
      max: 999.99,
      message: '体重需在 0.01 ~ 999.99 kg 之间',
      trigger: 'blur'
    }
  ],
  vaccine: [{ max: 255, message: '疫苗情况长度不能超过 255', trigger: 'blur' }],
  remark: [{ max: 255, message: '备注长度不能超过 255', trigger: 'blur' }]
}

const dialogTitle = ref('新增宠物')

onMounted(loadPets)

async function loadPets() {
  loading.value = true
  try {
    pets.value = (await listUserPets()) || []
  } catch (e) {
    pets.value = []
  } finally {
    loading.value = false
  }
}

/** 生日不能晚于今天（与后端 @PastOrPresent 一致） */
function disabledDate(date) {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return date.getTime() > today.getTime()
}

function genderText(gender) {
  if (gender === 1) {
    return '公'
  }
  if (gender === 2) {
    return '母'
  }
  return '性别未知'
}

/**
 * 由生日计算年龄：满 1 岁显示「N岁M个月」，不足 1 岁显示「M个月」
 */
function ageText(birthday) {
  if (!birthday) {
    return '年龄未知'
  }
  const birth = new Date(String(birthday).replace(/-/g, '/'))
  if (Number.isNaN(birth.getTime())) {
    return '年龄未知'
  }
  const now = new Date()
  let months =
    (now.getFullYear() - birth.getFullYear()) * 12 + (now.getMonth() - birth.getMonth())
  if (now.getDate() < birth.getDate()) {
    months -= 1
  }
  if (months < 0) {
    return '年龄未知'
  }
  const years = Math.floor(months / 12)
  const rest = months % 12
  if (years > 0) {
    return rest > 0 ? `${years}岁${rest}个月` : `${years}岁`
  }
  return rest > 0 ? `${rest}个月` : '不足1个月'
}

/** 卡片头像占位：取宠物名首字 */
function avatarText(name) {
  return (name || '宠').trim().charAt(0)
}

function resetForm() {
  form.name = ''
  form.breed = ''
  form.gender = 0
  form.birthday = ''
  form.weight = null
  form.vaccine = ''
  form.remark = ''
  formRef.value?.clearValidate()
}

/** 新增 */
function openAdd() {
  editingId.value = null
  dialogTitle.value = '新增宠物'
  resetForm()
  dialogVisible.value = true
}

/** 编辑 */
function openEdit(pet) {
  editingId.value = pet.id
  dialogTitle.value = '编辑宠物'
  form.name = pet.name || ''
  form.breed = pet.breed || ''
  form.gender = pet.gender == null ? 0 : pet.gender
  form.birthday = pet.birthday || ''
  form.weight = pet.weight == null ? null : Number(pet.weight)
  form.vaccine = pet.vaccine || ''
  form.remark = pet.remark || ''
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

/** 提交（新增 / 修改） */
async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  const payload = {
    name: form.name.trim(),
    breed: form.breed.trim() || null,
    gender: form.gender,
    birthday: form.birthday || null,
    weight: form.weight === null || form.weight === '' ? null : Number(form.weight),
    vaccine: form.vaccine.trim() || null,
    remark: form.remark.trim() || null
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await updatePet({ id: editingId.value, ...payload })
      ElMessage.success('宠物信息已更新')
    } else {
      await addPet(payload)
      ElMessage.success('宠物添加成功')
    }
    dialogVisible.value = false
    await loadPets()
  } catch (e) {
    // 重名等错误提示由 request 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

/** 删除（先确认；有未完成订单时后端会拒绝并说明原因） */
async function handleDelete(pet) {
  try {
    await ElMessageBox.confirm(`确定删除宠物「${pet.name}」吗？`, '删除宠物', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  try {
    await deletePet(pet.id)
    ElMessage.success('宠物已删除')
    await loadPets()
  } catch (e) {
    // 提示由 request 拦截器统一弹出
  }
}
</script>

<template>
  <div class="pet h5-page">
    <!-- 1. 顶部新增按钮 -->
    <el-button class="pet__add" type="primary" round size="large" @click="openAdd">
      <el-icon><Plus /></el-icon>
      新增宠物
    </el-button>

    <!-- 加载骨架 -->
    <div v-if="loading" class="pet__list">
      <div v-for="n in 2" :key="n" class="pet__card pet__card--skeleton">
        <div class="pet__avatar pet__avatar--plain"></div>
        <div class="pet__info">
          <div class="pet__line"></div>
          <div class="pet__line pet__line--short"></div>
        </div>
      </div>
    </div>

    <!-- 5. 空状态 -->
    <el-empty v-else-if="!pets.length" :image-size="90" description="还没有宠物档案，先添加一只吧">
      <el-button type="primary" size="small" round @click="openAdd">新增宠物</el-button>
    </el-empty>

    <!-- 2. 宠物卡片列表 -->
    <div v-else class="pet__list">
      <article v-for="pet in pets" :key="pet.id" class="pet__card">
        <div class="pet__avatar">{{ avatarText(pet.name) }}</div>

        <div class="pet__info">
          <h3 class="pet__name">{{ pet.name }}</h3>
          <p class="pet__meta">
            <span class="pet__tag">{{ pet.breed || '品种未知' }}</span>
            <span class="pet__tag" :class="`pet__tag--gender${pet.gender || 0}`">
              {{ genderText(pet.gender) }}
            </span>
            <span class="pet__tag pet__tag--plain">{{ ageText(pet.birthday) }}</span>
          </p>
          <p v-if="pet.weight" class="pet__weight">体重 {{ pet.weight }} kg</p>
          <p v-if="pet.vaccine" class="pet__extra">疫苗：{{ pet.vaccine }}</p>
          <p v-if="pet.remark" class="pet__extra">{{ pet.remark }}</p>
        </div>

        <!-- 操作 -->
        <div class="pet__actions">
          <el-button size="small" round @click="openEdit(pet)">编辑</el-button>
          <el-button size="small" type="danger" plain round @click="handleDelete(pet)">删除</el-button>
        </div>
      </article>
    </div>

    <!-- 3. 新增 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="88%">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="76px" label-position="left">
        <el-form-item label="宠物名" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="请输入宠物名" clearable />
        </el-form-item>

        <el-form-item label="品种" prop="breed">
          <el-input v-model="form.breed" maxlength="50" placeholder="如：英国短毛猫" clearable />
        </el-form-item>

        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio v-for="item in GENDERS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="生日" prop="birthday">
          <el-date-picker
            v-model="form.birthday"
            type="date"
            placeholder="请选择生日"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledDate"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="体重" prop="weight">
          <el-input-number
            v-model="form.weight"
            :min="0.01"
            :max="999.99"
            :precision="2"
            :step="0.1"
            :controls="false"
            placeholder="单位 kg"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="疫苗情况" prop="vaccine">
          <el-input
            v-model="form.vaccine"
            maxlength="255"
            placeholder="如：已完成三联疫苗"
            clearable
          />
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            resize="none"
            maxlength="255"
            show-word-limit
            placeholder="宠物性格、注意事项等"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button round @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" round :loading="submitting" @click="handleSubmit">
          {{ editingId ? '保存修改' : '确认新增' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pet__add {
  width: 100%;
  height: 46px;
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
}

/* ---------------- 宠物卡片 ---------------- */
.pet__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pet__card {
  display: flex;
  align-items: flex-start;
  padding: 12px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

/* 头像占位：姓名首字（宠物数据没有头像字段） */
.pet__avatar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  margin-right: 10px;
  font-size: 20px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #79bbff);
  border-radius: 50%;
}

.pet__avatar--plain {
  background: #f0f2f5;
}

.pet__info {
  flex: 1;
  min-width: 0;
}

.pet__name {
  overflow: hidden;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pet__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.pet__tag {
  padding: 1px 8px;
  font-size: 11px;
  color: #409eff;
  background-color: rgba(64, 158, 255, 0.1);
  border-radius: 4px;
}

.pet__tag--gender1 {
  color: #409eff;
  background-color: rgba(64, 158, 255, 0.1);
}

.pet__tag--gender2 {
  color: #f56c6c;
  background-color: rgba(245, 108, 108, 0.1);
}

.pet__tag--plain {
  color: #909399;
  background-color: #f5f6f8;
}

.pet__weight,
.pet__extra {
  margin-top: 6px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 卡片右侧操作 */
.pet__actions {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 6px;
  margin-left: 8px;
}

.pet__actions :deep(.el-button) {
  margin-left: 0;
}

/* 骨架 */
.pet__card--skeleton {
  box-shadow: none;
}

.pet__line {
  height: 13px;
  margin-bottom: 10px;
  background-color: #f0f2f5;
  border-radius: 6px;
}

.pet__line--short {
  width: 55%;
}
</style>
