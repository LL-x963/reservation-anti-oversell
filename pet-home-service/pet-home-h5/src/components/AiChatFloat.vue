<script setup>
import { ElMessage } from 'element-plus'

/**
 * 全局悬浮 AI 问答入口（占位组件）
 *
 * <p>后续替换为真实问答面板时对接以下接口：</p>
 * <ul>
 *   <li>POST /api/user/knowledge/ask —— 提问（支持携带宠物信息、最近 3 轮历史）</li>
 *   <li>GET  /api/user/knowledge/history/page —— 历史问答分页</li>
 *   <li>DELETE /api/user/knowledge/history/{id} —— 删除单条历史</li>
 * </ul>
 */
function openChat() {
  ElMessage.info('AI 问答功能开发中，敬请期待')
}
</script>

<template>
  <div class="ai-float h5-tap" @click="openChat">
    <el-icon :size="22"><ChatDotRound /></el-icon>
    <span class="ai-float__text">AI</span>
  </div>
</template>

<style scoped>
.ai-float {
  position: fixed;
  right: 12px;
  /* 悬浮在底部导航之上，并避开底部安全区域 */
  bottom: calc(var(--h5-tabbar-height) + var(--safe-area-bottom) + 16px);
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #79bbff);
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
}

.ai-float__text {
  position: absolute;
  right: 4px;
  bottom: 2px;
  padding: 0 4px;
  font-size: 10px;
  background-color: #ff8c1a;
  border-radius: 6px;
}
</style>
