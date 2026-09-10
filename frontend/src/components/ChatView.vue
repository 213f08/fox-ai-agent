<script setup>
import { ref, watch, nextTick, computed } from 'vue'
import MessageItem from './MessageItem.vue'
import ChatInput from './ChatInput.vue'
import { useChatStore } from '../stores/useChatStore'

const store = useChatStore()
const activeSession = store.activeSession
const scrollBox = ref(null)

function scrollToBottom() {
  const el = scrollBox.value
  if (!el) return
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 140
  if (nearBottom) {
    el.scrollTop = el.scrollHeight
  }
}

// 流式增量期间持续跟随滚动
watch(
  () => activeSession.value?.messages,
  () => nextTick(scrollToBottom),
  { deep: true }
)

/** 把时间戳格式化为“今天 / 昨天 / YYYY-MM-DD”分组标签 */
function dayLabel(ts) {
  const d = new Date(ts || Date.now())
  const today = new Date()
  const startToday = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const startThat = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const diffDays = Math.round((startToday - startThat) / 86400000)
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * 渲染列表：按“天”插入日期分隔条，实现消息列表分组。
 * 每个 msg 项携带原始 index，供撤回时定位。
 */
const rendered = computed(() => {
  const msgs = activeSession.value?.messages || []
  const out = []
  let lastKey = null
  msgs.forEach((m, i) => {
    const d = new Date(m.ts || Date.now())
    const key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
    if (key !== lastKey) {
      out.push({ type: 'divider', label: dayLabel(m.ts || Date.now()) })
      lastKey = key
    }
    out.push({ type: 'msg', message: m, index: i })
  })
  return out
})

const lastIndex = computed(() => (activeSession.value?.messages.length || 0) - 1)
function isStreaming(index) {
  return store.state.sending && index === lastIndex.value
}
</script>

<template>
  <div class="chat-view">
    <div ref="scrollBox" class="msg-scroll">
      <div class="msg-inner">
        <template v-for="(item, idx) in rendered" :key="item.type === 'divider' ? 'div-' + idx : 'msg-' + item.index">
          <div v-if="item.type === 'divider'" class="date-divider">
            <span>{{ item.label }}</span>
          </div>
          <MessageItem
            v-else
            :message="item.message"
            :index="item.index"
            :is-streaming="isStreaming(item.index)"
            :interactive="!store.state.sending"
            @recall="store.recall"
          />
        </template>
      </div>
    </div>

    <div class="chat-input-bar">
      <ChatInput :sending="store.state.sending" @submit="store.send" @stop="store.stop" />
      <p class="disclaimer">内容由 AI 生成，请注意甄别准确性</p>
    </div>
  </div>
</template>

<style scoped>
.chat-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.msg-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px 10px;
  /* 列表滚到底继续滑动时不带动整页 */
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.msg-inner {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 13px;
}

/* 按天分组分隔条：今天 / 昨天 / 日期 */
.date-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 2px 0;
}
.date-divider span {
  padding: 3px 14px;
  font-size: 12px;
  color: var(--text-3);
  background: rgba(31, 79, 216, 0.06);
  border-radius: 999px;
  letter-spacing: 0.3px;
}

.chat-input-bar {
  flex-shrink: 0;
  padding: 10px 20px 16px;
}
.chat-input-bar .chat-input {
  max-width: 860px;
  margin: 0 auto;
}

.disclaimer {
  max-width: 860px;
  margin: 10px auto 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-3);
}

/* ===== 移动端 ===== */
@media (max-width: 820px) {
  .msg-scroll {
    padding: 12px calc(12px + var(--sar)) 6px calc(12px + var(--sal));
  }

  /* 窄屏消息之间收紧一点，一屏能多看到一条 */
  .msg-inner {
    gap: 11px;
  }

  /* 底部补上 iPhone 手势条的高度，否则发送键会被压在横条下面点不到 */
  .chat-input-bar {
    padding: 8px calc(12px + var(--sar)) calc(12px + var(--sab)) calc(12px + var(--sal));
  }

  .disclaimer {
    margin-top: 8px;
    font-size: 11px;
  }
}
</style>
