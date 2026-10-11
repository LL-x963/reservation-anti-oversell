<script setup>
import { computed, nextTick, ref, watch } from 'vue'

import { askKnowledge } from '@/api/knowledge'

/**
 * 全局悬浮 AI 问答组件（养宠百科）
 *
 * <p>接口：POST /api/user/knowledge/ask —— { question, conversationHistory, petId }
 * 返回 { answer, references: [{ docId, title, content }] }；RAG 不可用时后端返回
 * code=0 / bizCode=600，本组件在消息流内给出降级提示（请求传 silent，不重复弹全局提示）。</p>
 */

/**
 * 后端（RAG 服务）会在答案末尾固定拼接这段免责声明。
 * 前端统一用灰色小字展示，故先剥离，避免同一条回答出现两遍免责声明。
 */
const BACKEND_DISCLAIMER = '以上内容仅供科普参考，若宠物出现不适或症状严重，请及时前往宠物医院就医。'

/** 每条 AI 回答末尾展示的免责声明（灰色小字） */
const DISCLAIMER = '以上内容仅供科普参考，若宠物出现不适请及时就医'

/** 接口失败 / 超时的降级提示 */
const DEGRADE_TIP = '知识库服务暂时繁忙，请稍后再试'

/** 空会话时的欢迎语 */
const WELCOME = '你好，我是宠享到家 AI 养宠助手。可以问我宠物洗澡频率、日常喂食、疾病预防等问题。'

/** 单条问题最大长度（与后端 KnowledgeAskRequest 的校验保持一致） */
const MAX_QUESTION_LENGTH = 1000

/** 携带的历史轮数：后端只取最近 3 轮，1 轮 = 一问一答，故取最后 6 条 */
const HISTORY_MESSAGE_LIMIT = 6

const props = defineProps({
  /** 关联宠物 id（可选）：传了后端会把宠物信息一并交给模型 */
  petId: { type: [Number, String], default: null }
})

/** 弹窗显隐 */
const visible = ref(false)
/** 输入框内容 */
const question = ref('')
/** 请求进行中 */
const sending = ref(false)

const scrollerRef = ref()
const inputRef = ref()

/**
 * 会话消息（仅保存在内存中）
 *
 * <p>需求是「关闭弹窗不丢失、刷新页面清空」：组件关闭时只切换显示状态（v-show）不销毁，
 * 内存即可满足前者；若改用 sessionStorage，刷新后仍会恢复，反而违反后者。</p>
 */
const messages = ref([])

/** 消息自增 id，用于 v-for 的 key */
let messageSeq = 0

const canSend = computed(() => !sending.value && question.value.trim().length > 0)

function open() {
  visible.value = true
  scrollToBottom()
  // 移动端自动聚焦会同时唤起键盘，等弹窗过渡开始后再聚焦体验更自然
  nextTick(() => inputRef.value?.focus?.())
}

function close() {
  visible.value = false
}

function toggle() {
  visible.value ? close() : open()
}

/**
 * 组装历史上下文：剔除失败消息（降级提示不是模型产出，不应回灌给模型）
 */
function buildHistory() {
  return messages.value
    .filter((item) => !item.failed && (item.role === 'user' || item.role === 'assistant'))
    .slice(-HISTORY_MESSAGE_LIMIT)
    .map((item) => ({ role: item.role, content: item.content }))
}

/**
 * 输入框回车发送
 *
 * <p>中文输入法在候选态的回车用于选词，此时不发送；Shift + Enter 保留换行。</p>
 */
function handleInputKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }
  if (event.isComposing || event.keyCode === 229) {
    return
  }
  event.preventDefault()
  send()
}

async function send() {
  const text = question.value.trim()
  if (!text || sending.value) {
    return
  }

  // 历史必须在压入当前问题之前组装
  const history = buildHistory()
  messages.value.push({ id: ++messageSeq, role: 'user', content: text })
  question.value = ''
  sending.value = true
  scrollToBottom()

  try {
    const data = await askKnowledge(
      {
        question: text,
        conversationHistory: history,
        petId: props.petId ? Number(props.petId) : null
      },
      // 失败态在消息流内展示，禁用全局 toast，避免一次失败弹两个提示
      { silent: true }
    )

    const answer = stripBackendDisclaimer(data?.answer)
    if (!answer) {
      messages.value.push({
        id: ++messageSeq,
        role: 'assistant',
        content: DEGRADE_TIP,
        failed: true,
        references: []
      })
      return
    }

    messages.value.push({
      id: ++messageSeq,
      role: 'assistant',
      content: answer,
      references: Array.isArray(data?.references) ? data.references : []
    })
  } catch (e) {
    // 接口失败或超时：降级提示留在聊天记录里，用户可直接重发
    messages.value.push({
      id: ++messageSeq,
      role: 'assistant',
      content: DEGRADE_TIP,
      failed: true,
      references: []
    })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

/**
 * 去掉后端答案末尾的免责声明（见 BACKEND_DISCLAIMER 说明）
 */
function stripBackendDisclaimer(answer) {
  const text = (answer || '').trim()
  if (!text) {
    return ''
  }
  return text.endsWith(BACKEND_DISCLAIMER)
    ? text.slice(0, text.length - BACKEND_DISCLAIMER.length).trim()
    : text
}

/**
 * 滚动到底部（等 DOM 更新后再滚，保证新消息与 loading 都可见）
 */
function scrollToBottom() {
  nextTick(() => {
    const el = scrollerRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

/** 新消息或 loading 出现时自动滚到底部 */
watch([() => messages.value.length, sending], scrollToBottom)
</script>

<template>
  <!-- 右下角悬浮按钮 -->
  <div class="chat__fab h5-tap" @click="toggle">
    <el-icon :size="24"><ChatDotRound /></el-icon>
    <span class="chat__fab-badge">AI</span>
  </div>

  <!-- 遮罩：点击关闭 -->
  <transition name="chat-fade">
    <div v-show="visible" class="chat__mask" @click="close"></div>
  </transition>

  <!-- 聊天弹窗（移动端底部弹层，占满大部分屏幕） -->
  <transition name="chat-slide">
    <section v-show="visible" class="chat__panel">
      <!-- 顶部标题栏 -->
      <header class="chat__header">
        <span class="chat__title">养宠百科</span>
        <el-icon class="chat__close h5-tap" @click="close"><Close /></el-icon>
      </header>

      <!-- 聊天内容区 -->
      <div ref="scrollerRef" class="chat__body">
        <div v-if="!messages.length" class="chat__welcome">{{ WELCOME }}</div>

        <div
          v-for="item in messages"
          :key="item.id"
          class="chat__row"
          :class="`chat__row--${item.role}`"
        >
          <div class="chat__bubble" :class="{ 'chat__bubble--failed': item.failed }">
            <p class="chat__text">{{ item.content }}</p>

            <!-- 参考来源折叠面板 -->
            <el-collapse v-if="item.references && item.references.length" class="chat__refs">
              <el-collapse-item :title="`参考来源（${item.references.length}）`" name="refs">
                <div
                  v-for="(ref, index) in item.references"
                  :key="ref.docId ?? index"
                  class="chat__ref"
                >
                  <p class="chat__ref-title">{{ ref.title || `文档 ${ref.docId}` }}</p>
                  <p class="chat__ref-content">{{ ref.content }}</p>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>

          <!-- 免责声明：仅在正常回答下展示 -->
          <p v-if="item.role === 'assistant' && !item.failed" class="chat__disclaimer">
            {{ DISCLAIMER }}
          </p>
        </div>

        <!-- 发送中 -->
        <div v-if="sending" class="chat__row chat__row--assistant">
          <div class="chat__bubble chat__bubble--typing">
            <span class="chat__dot"></span>
            <span class="chat__dot"></span>
            <span class="chat__dot"></span>
            <span class="chat__typing-text">正在思考…</span>
          </div>
        </div>
      </div>

      <!-- 底部输入框 -->
      <footer class="chat__footer">
        <el-input
          ref="inputRef"
          v-model="question"
          type="textarea"
          class="chat__input"
          :autosize="{ minRows: 1, maxRows: 3 }"
          :maxlength="MAX_QUESTION_LENGTH"
          resize="none"
          placeholder="描述你的问题，例如：猫咪多久洗一次澡？"
          @keydown="handleInputKeydown"
        />
        <el-button
          class="chat__send"
          type="primary"
          :disabled="!canSend"
          :loading="sending"
          @click="send"
        >
          发送
        </el-button>
      </footer>
    </section>
  </transition>
</template>

<style scoped>
/* ---------------- 悬浮按钮 ---------------- */
.chat__fab {
  position: fixed;
  right: 12px;
  /* 悬浮在底部导航之上，并避开底部安全区域 */
  bottom: calc(var(--h5-tabbar-height) + var(--safe-area-bottom) + 16px);
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #79bbff);
  border-radius: 50%;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.45);
}

.chat__fab-badge {
  position: absolute;
  right: 2px;
  bottom: 2px;
  padding: 0 4px;
  font-size: 10px;
  line-height: 14px;
  background-color: #ff8c1a;
  border-radius: 6px;
}

/* ---------------- 遮罩与弹窗 ---------------- */
.chat__mask {
  position: fixed;
  inset: 0;
  z-index: 900;
  background-color: rgba(0, 0, 0, 0.45);
}

.chat__panel {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 901;
  display: flex;
  flex-direction: column;
  /* dvh 跟随可视视口：移动端键盘弹出时弹窗自动上移，不会被键盘遮住 */
  height: 78vh;
  height: 78dvh;
  max-height: 720px;
  padding-top: var(--safe-area-top);
  overflow: hidden;
  background-color: #f5f6f8;
  border-radius: 16px 16px 0 0;
}

/* ---------------- 顶部标题栏 ---------------- */
.chat__header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  height: var(--h5-navbar-height);
  padding: 0 14px;
  background-color: #fff;
  border-bottom: 1px solid #f0f0f0;
}

.chat__title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.chat__close {
  padding: 4px;
  font-size: 20px;
  color: #909399;
}

/* ---------------- 聊天内容区 ---------------- */
.chat__body {
  flex: 1;
  padding: 12px;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.chat__welcome {
  padding: 14px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  background-color: #fff;
  border-radius: 10px;
}

.chat__row {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}

.chat__row--user {
  align-items: flex-end;
}

.chat__row--assistant {
  align-items: flex-start;
}

.chat__bubble {
  max-width: 84%;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.6;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.chat__row--user .chat__bubble {
  color: #fff;
  background: linear-gradient(135deg, #409eff, #66b1ff);
}

.chat__bubble--failed {
  color: #e6a23c;
  background-color: #fdf6ec;
}

.chat__text {
  /* 保留后端答案中的换行与缩进 */
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------------- 参考来源折叠面板 ---------------- */
.chat__refs {
  margin-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.chat__refs :deep(.el-collapse-item__header) {
  height: 32px;
  font-size: 12px;
  color: #409eff;
  border-bottom: none;
}

.chat__refs :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.chat__refs :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}

.chat__ref {
  padding: 8px 10px;
  margin-top: 6px;
  font-size: 12px;
  background-color: #f7f8fa;
  border-radius: 8px;
}

.chat__ref-title {
  margin-bottom: 4px;
  font-weight: 600;
  color: #303133;
}

.chat__ref-content {
  color: #909399;
  line-height: 1.5;
}

/* ---------------- 免责声明 ---------------- */
.chat__disclaimer {
  max-width: 84%;
  margin-top: 4px;
  padding: 0 2px;
  color: #b1b3b8;
  font-size: 11px;
  line-height: 1.4;
}

/* ---------------- 发送中动画 ---------------- */
.chat__bubble--typing {
  display: flex;
  align-items: center;
  color: #909399;
  font-size: 13px;
}

.chat__dot {
  width: 6px;
  height: 6px;
  margin-right: 4px;
  background-color: #c0c4cc;
  border-radius: 50%;
  animation: chat-blink 1.2s infinite ease-in-out;
}

.chat__dot:nth-child(2) {
  animation-delay: 0.2s;
}

.chat__dot:nth-child(3) {
  animation-delay: 0.4s;
}

.chat__typing-text {
  margin-left: 4px;
}

@keyframes chat-blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }

  40% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

/* ---------------- 底部输入区 ---------------- */
.chat__footer {
  display: flex;
  flex-shrink: 0;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 12px calc(10px + var(--safe-area-bottom));
  background-color: #fff;
  border-top: 1px solid #f0f0f0;
}

.chat__input {
  flex: 1;
}

.chat__input :deep(.el-textarea__inner) {
  padding: 8px 10px;
  font-size: 14px;
  line-height: 1.5;
  border-radius: 10px;
}

.chat__send {
  flex-shrink: 0;
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
}

/* ---------------- 过渡动画 ---------------- */
.chat-fade-enter-active,
.chat-fade-leave-active {
  transition: opacity 0.25s ease;
}

.chat-fade-enter-from,
.chat-fade-leave-to {
  opacity: 0;
}

.chat-slide-enter-active,
.chat-slide-leave-active {
  transition: transform 0.28s cubic-bezier(0.32, 0.72, 0, 1);
}

.chat-slide-enter-from,
.chat-slide-leave-to {
  transform: translateY(100%);
}
</style>
