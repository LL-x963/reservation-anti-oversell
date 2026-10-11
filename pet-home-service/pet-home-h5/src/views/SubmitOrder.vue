<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 提交预约订单
 *
 * <p>本页为骨架结构，待对接接口：POST /api/user/order/submit。</p>
 *
 * <p>接入时请以 OrderSubmitRequest 的字段为准（本页表单为占位结构，尚未提交）；
 * 可预约时段来自后端排班，需额外拉取，避免提交后报「该时段不可约」。</p>
 */
const route = useRoute()

/** 从上一页带过来的服务 id */
const serviceId = ref(route.query.serviceId || '')

const form = ref({
  petId: '',
  appointmentDate: '',
  timeSlot: '',
  remark: ''
})
</script>

<template>
  <div class="submit h5-page">
    <div class="h5-card">
      <div class="submit__label">服务</div>
      <div class="h5-placeholder">服务信息待接入（当前 serviceId={{ serviceId }}）</div>
    </div>

    <div class="h5-card">
      <div class="submit__label">预约信息</div>
      <el-form label-width="72px" label-position="left">
        <el-form-item label="宠物">
          <el-select v-model="form.petId" placeholder="请选择宠物" style="width: 100%">
            <el-option label="宠物列表待接入 GET /api/user/pet/list" value="" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约日期">
          <el-date-picker
            v-model="form.appointmentDate"
            type="date"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="时段">
          <el-select v-model="form.timeSlot" placeholder="请选择时段" style="width: 100%">
            <el-option label="可预约时段待接入（后端排班）" value="" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="宠物性格、注意事项等" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 底部提交栏 -->
    <div class="submit__footer">
      <div class="submit__amount">
        合计
        <span class="submit__price">￥{{ 0 }}</span>
      </div>
      <el-button type="primary" round disabled>提交预约</el-button>
    </div>
  </div>
</template>

<style scoped>
.submit__label {
  margin-bottom: 8px;
  font-size: 15px;
  font-weight: 600;
}

.submit__footer {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background-color: #fff;
  border-radius: 10px;
  box-shadow: 0 -1px 6px rgba(0, 0, 0, 0.05);
}

.submit__amount {
  color: #606266;
  font-size: 13px;
}

.submit__price {
  margin-left: 4px;
  color: #f56c6c;
  font-size: 18px;
  font-weight: 600;
}
</style>
