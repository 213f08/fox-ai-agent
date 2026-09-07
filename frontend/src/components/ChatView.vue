<script setup>
import { ref, watch, nextTick } from 'vue'
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
</script>

<template>
  <div class="chat-view">
    <div ref="scrollBox" class="msg-scroll">
      <div class="msg-inner">
        <MessageItem
          v-for="(m, i) in activeSession.messages"
          :key="i"
          :message="m"
          :is-streaming="
            store.state.sending &&
            m.role === 'assistant' &&
            i === activeSession.messages.length - 1
          "
        />
      </div>
    </div>

    <div class="chat-input-bar">
      <ChatInput :sending="store.state.sending" @submit="store.send" />
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
  padding: 28px 24px 12px;
}

.msg-inner {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chat-input-bar {
  flex-shrink: 0;
  padding: 12px 24px 20px;
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
</style>
