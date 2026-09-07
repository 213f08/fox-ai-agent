<script setup>
import { ref, nextTick } from 'vue'

defineProps({
  sending: { type: Boolean, default: false }
})
const emit = defineEmits(['submit'])

const text = ref('')
const ta = ref(null)
const focused = ref(false)

function autoResize() {
  const el = ta.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function resetHeight() {
  const el = ta.value
  if (el) el.style.height = 'auto'
}

function onSubmit() {
  const v = text.value.trim()
  if (!v) return
  emit('submit', v)
  text.value = ''
  nextTick(resetHeight)
}

function onKeydown(e) {
  // Enter 发送，Shift+Enter 换行；中文输入法组词中的 Enter 不触发
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    onSubmit()
  }
}
</script>

<template>
  <div class="chat-input">
    <div class="input-wrap" :class="{ focused }">
      <textarea
        ref="ta"
        v-model="text"
        rows="1"
        :disabled="sending"
        placeholder="问问 Fox AI，联网搜索、抓网页、生成报告…"
        @input="autoResize"
        @keydown="onKeydown"
        @focus="focused = true"
        @blur="focused = false"
      ></textarea>
      <button
        class="send-btn"
        :disabled="sending || !text.trim()"
        title="发送"
        @click="onSubmit"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 2 11 13M22 2l-7 20-4-9-9-4z" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-input {
  width: 100%;
}

.input-wrap {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 10px 10px 20px;
  background: #fff;
  border: 1.5px solid var(--border);
  border-radius: 24px;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition);
}

.input-wrap.focused {
  border-color: var(--primary);
  box-shadow: 0 0 0 4px rgba(47, 107, 255, 0.12), var(--shadow-md);
}

textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 1.6;
  max-height: 160px;
  padding: 6px 0;
  background: transparent;
}
textarea::placeholder {
  color: var(--text-3);
}

.send-btn {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--primary-3), var(--primary));
  box-shadow: 0 6px 14px rgba(47, 107, 255, 0.32);
  transition: all var(--transition);
}
.send-btn:disabled {
  background: var(--border);
  color: var(--text-3);
  box-shadow: none;
  cursor: not-allowed;
}
.send-btn:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(47, 107, 255, 0.4);
}
</style>
