<script setup>
/**
 * 宠物档案
 *
 * <p>本页为骨架结构，待对接接口：</p>
 * <ul>
 *   <li>GET /api/user/pet/list —— 宠物列表</li>
 *   <li>GET /api/user/pet/{id} —— 宠物详情</li>
 *   <li>POST /api/user/pet/add —— 新增宠物</li>
 *   <li>PUT /api/user/pet/update —— 修改宠物</li>
 *   <li>DELETE /api/user/pet/{id} —— 删除宠物</li>
 * </ul>
 */
</script>

<template>
  <div class="pet h5-page">
    <div class="h5-card">
      <div class="h5-placeholder">待接入 GET /api/user/pet/list</div>
    </div>

    <el-button class="pet__add" size="large" type="primary" plain>
      <el-icon><Plus /></el-icon>
      新增宠物
    </el-button>
  </div>
</template>

<style scoped>
.pet__add {
  width: 100%;
}
</style>
