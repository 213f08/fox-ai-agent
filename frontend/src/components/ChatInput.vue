<script setup>
import { ref, computed, nextTick } from 'vue'
import { useChatStore, MODES } from '../stores/useChatStore'

defineProps({
  sending: { type: Boolean, default: false }
})
const emit = defineEmits(['submit', 'stop'])

const store = useChatStore()
// 占位文案跟随当前助手：全能助手 vs 饮食健康小养
const placeholder = computed(() => (MODES[store.state.mode] || MODES.manus).placeholder)

const text = ref('')
const ta = ref(null)
const focused = ref(false)

function autoResize() {
  const el = ta.value
  if (!el) return
  el.style.height = 'auto'
  // 移动端键盘弹出后留给输入框的高度更少，上限收到 120px
  const max = window.matchMedia('(max-width: 820px)').matches ? 120 : 160
  el.style.height = Math.min(el.scrollHeight, max) + 'px'
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
        :placeholder="placeholder"
        enterkeyhint="send"
        autocomplete="off"
        autocapitalize="off"
        spellcheck="false"
        @input="autoResize"
        @keydown="onKeydown"
        @focus="focused = true"
        @blur="focused = false"
      ></textarea>
      <button
        v-if="sending"
        class="stop-btn"
        title="停止生成"
        @click="$emit('stop')"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
          <rect x="6" y="6" width="12" height="12" rx="2" />
        </svg>
      </button>
      <button
        v-else
        class="send-btn"
        :disabled="!text.trim()"
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
  padding: 8px 12px 8px 18px;
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
  font-size: 14.5px;
  line-height: 1.6;
  max-height: 160px;
  padding: 4px 0;
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

/* 停止生成按钮：同位置替换发送键，红色方形表达“中断”语义 */
.stop-btn {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, #f0556a, var(--danger));
  box-shadow: 0 6px 14px rgba(239, 68, 68, 0.32);
  transition: all var(--transition);
}
.stop-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(239, 68, 68, 0.42);
}

/* ===== 移动端 / 触屏 ===== */
@media (max-width: 820px) {
  .input-wrap {
    gap: 6px;
    padding: 6px 8px 6px 14px;
    border-radius: 22px;
  }

  /* iOS Safari 会对 font-size < 16px 的输入框在聚焦时自动放大整个页面，
     导致界面被推开、要手动缩小。16px 是避免这个行为的硬性下限。 */
  textarea {
    font-size: 16px;
    line-height: 1.5;
  }

  /* 触屏手指目标放大到 42px */
  .send-btn,
  .stop-btn {
    width: 42px;
    height: 42px;
  }
}

/* 触屏没有真实 hover：把 hover 的位移换成按下反馈 */
@media (hover: none) {
  .send-btn:not(:disabled):hover,
  .stop-btn:hover {
    transform: none;
  }
  .send-btn:not(:disabled):active,
  .stop-btn:active {
    transform: scale(0.93);
  }
}
</style>
