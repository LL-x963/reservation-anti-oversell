<script setup>
/**
 * 业务页占位组件
 *
 * 骨架阶段除首页外都用它占位，并标注该模块后端已就绪的接口前缀，方便后续按模块实现。
 */
defineProps({
  /** 页面标题 */
  title: { type: String, required: true },
  /** 模块说明 */
  description: { type: String, default: '' },
  /** 后端接口前缀（已实现，待前端接入） */
  prefix: { type: String, default: '' }
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="placeholder-header">
          <span class="placeholder-title">{{ title }}</span>
          <el-tag type="info" effect="plain" size="small">待开发</el-tag>
        </div>
      </template>

      <el-empty :description="description || '该模块页面尚未实现，当前为占位页'">
        <div v-if="prefix" class="placeholder-tip">
          后端接口已就绪，前缀：
          <el-text type="primary">{{ prefix }}</el-text>
        </div>
      </el-empty>
    </el-card>
  </div>
</template>

<style scoped>
.placeholder-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.placeholder-title {
  font-size: 15px;
  font-weight: 600;
}

.placeholder-tip {
  font-size: 13px;
  color: #909399;
}
</style>
