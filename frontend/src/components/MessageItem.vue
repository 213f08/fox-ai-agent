<script setup>
defineProps({
  message: { type: Object, required: true },
  isStreaming: { type: Boolean, default: false }
})
</script>

<template>
  <div class="msg-row" :class="message.role">
    <div v-if="message.role === 'assistant'" class="avatar">
      <span>F</span>
    </div>

    <div class="bubble" :class="message.role">
      <div v-if="message.role === 'assistant' && !message.content && isStreaming" class="typing">
        <span></span><span></span><span></span>
      </div>
      <div v-else class="content">
        {{ message.content }}
        <span v-if="isStreaming && message.role === 'assistant'" class="stream-cursor"></span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  animation: float-in 0.3s var(--transition) both;
}

.msg-row.user {
  justify-content: flex-end;
}

.avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 17px;
  background: linear-gradient(135deg, var(--primary-3), var(--primary-2));
  box-shadow: 0 4px 12px rgba(47, 107, 255, 0.28);
}

.bubble {
  max-width: min(720px, 78%);
  border-radius: 16px;
  line-height: 1.7;
}

.bubble.user {
  padding: 11px 16px;
  color: #fff;
  background: linear-gradient(135deg, var(--primary-3), var(--primary));
  border-top-right-radius: 6px;
  box-shadow: 0 6px 16px rgba(47, 107, 255, 0.24);
}

.bubble.assistant {
  padding: 13px 18px;
  background: #fff;
  border: 1px solid var(--border);
  border-top-left-radius: 6px;
  box-shadow: var(--shadow-sm);
}

.content {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 15px;
}

.typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 0;
}
.typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary-2);
  animation: bounce 1.2s infinite ease-in-out;
}
.typing span:nth-child(2) {
  animation-delay: 0.15s;
}
.typing span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-6px);
    opacity: 1;
  }
}
</style>
