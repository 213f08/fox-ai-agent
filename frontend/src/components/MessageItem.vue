<script setup>
import { ref, computed } from 'vue'
import { useChatStore } from '../stores/useChatStore'

const props = defineProps({
  message: { type: Object, required: true },
  isStreaming: { type: Boolean, default: false },
  index: { type: Number, required: true },
  // 是否允许操作（生成中禁用，避免破坏进行中的消息结构）
  interactive: { type: Boolean, default: true }
})
const emit = defineEmits(['recall'])

const store = useChatStore()
const stepsOpen = ref(false)
const copied = ref(false)
const steps = computed(() => props.message.steps || [])
// “思考中”：流式进行中，且还没产出面向用户的最终回答
const thinking = computed(() => props.isStreaming && !props.message.content)
// 重新编辑行：原文作为可编辑初始内容
const editText = ref(props.message.content || '')

// 复制消息文本到剪贴板（含降级方案）
function copyText() {
  const text = props.message.content || ''
  if (!text) return
  const mark = () => {
    copied.value = true
    setTimeout(() => (copied.value = false), 1200)
  }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(mark).catch(mark)
  } else {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
    } catch (_) {
      /* noop */
    }
    document.body.removeChild(ta)
    mark()
  }
}

// 已撤回 → 就地变可编辑行
function startReEdit() {
  store.startReEdit(props.index)
}
// 确认：把编辑后的文本作为新消息发送（移除可编辑行）
function submitEdit() {
  store.submitReEdit(props.index, editText.value)
}
// 取消：可编辑行还原为“已撤回”
function cancelEdit() {
  store.cancelReEdit(props.index)
}
</script>

<template>
  <div class="msg-row" :class="message.role">
    <!-- 已撤回内联提示：保留撤回前原文，提供“重新编辑” -->
    <div v-if="message.role === 'recalled'" class="recalled-card">
      <svg class="recalled-ico" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3 7v6h6" />
        <path d="M3 13a9 9 0 1 0 3-7.7L3 8" />
      </svg>
      <span class="recalled-label">已撤回</span>
      <span class="recalled-text">{{ message.content }}</span>
      <button class="recalled-edit" @click="startReEdit">重新编辑</button>
    </div>

    <!-- 重新编辑行：右对齐，撤回的原文就在这一行内，可直接改完发送 -->
    <div v-else-if="message.role === 'reedit'" class="reedit-row">
      <div class="reedit-box">
        <textarea
          ref="rta"
          v-model="editText"
          class="reedit-textarea"
          rows="2"
          placeholder="修改后发送…"
          @keydown.enter.exact.prevent="submitEdit"
        ></textarea>
        <div class="reedit-actions">
          <button class="reedit-cancel" @click="cancelEdit">取消</button>
          <button class="reedit-send" :disabled="!editText.trim()" @click="submitEdit">发送</button>
        </div>
      </div>
    </div>

    <template v-else>
      <div v-if="message.role === 'assistant'" class="avatar">
        <img src="/logo-fox.png" alt="" />
      </div>

      <div class="bubble" :class="message.role">
        <!-- 同级操作：复制 / 撤回，悬浮整行消息时出现 -->
        <div v-if="interactive" class="msg-actions" :class="message.role">
          <button class="act-btn" :class="{ ok: copied }" title="复制" @click="copyText">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="9" y="9" width="11" height="11" rx="2" />
              <path d="M5 15V5a2 2 0 0 1 2-2h10" />
            </svg>
            {{ copied ? '已复制' : '复制' }}
          </button>
          <button class="act-btn recall" title="撤回" @click="emit('recall', index)">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 14 4 9l5-5" />
              <path d="M4 9h11a5 5 0 0 1 0 10h-1" />
            </svg>
            撤回
          </button>
        </div>

        <!-- 过程步骤（工具执行 / RAG 知识库检索）：默认折叠，避免刷屏 -->
        <div v-if="steps.length" class="steps">
          <button class="steps-toggle" @click="stepsOpen = !stepsOpen">
            <span v-if="thinking" class="spinner"></span>
            <span v-else class="steps-check">✓</span>
            <span class="steps-text">
              {{ thinking ? '思考中…' : `完成 ${steps.length} 个过程步骤` }}
            </span>
            <span class="steps-arrow" :class="{ open: stepsOpen }">▾</span>
          </button>
          <div v-show="stepsOpen" class="steps-body">
            <div v-for="(s, i) in steps" :key="i" class="step-item">{{ s }}</div>
          </div>
        </div>

        <!-- 面向用户的最终回答 -->
        <div v-if="message.content" class="content">
          {{ message.content }}
          <span v-if="isStreaming" class="stream-cursor"></span>
        </div>

        <!-- 既无回答也无工具步骤时：三点加载动画 -->
        <div v-if="!message.content && !steps.length && isStreaming" class="typing">
          <span></span><span></span><span></span>
        </div>
        <div v-if="!message.content && !steps.length && !isStreaming" class="content empty-tip">
          （无内容）
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  animation: float-in 0.3s var(--transition) both;
}

.msg-row.user {
  justify-content: flex-end;
}

.avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  background: transparent;
  box-shadow: 0 4px 12px rgba(47, 107, 255, 0.28);
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.bubble {
  position: relative;
  max-width: min(720px, 78%);
  border-radius: 16px;
  line-height: 1.6;
  /* column flex：让 .msg-actions 在触屏下能用 order 排到内容之后。
     桌面端 .msg-actions 是 absolute 脱离文档流，不受 order 影响，视觉无变化。 */
  display: flex;
  flex-direction: column;
}

/* 同级操作按钮组：复制 / 撤回，悬浮整行消息时出现在气泡上角。
   注意：不能依赖 .bubble:hover —— 按钮定位在气泡盒模型之外，
   鼠标移向按钮时会先离开气泡导致按钮消失、无法点击。改用 .msg-row:hover，
   鼠标在气泡/间隙/按钮上都属于行内，按钮保持可见、可点。 */
.msg-actions {
  position: absolute;
  top: -12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  pointer-events: none;
  transform: translateY(2px);
  transition: all var(--transition);
  z-index: 5;
}
.msg-row.user .msg-actions {
  right: 0;
}
.msg-row.assistant .msg-actions {
  left: 0;
}
.msg-row:hover .msg-actions {
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
}
.act-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 9px;
  font-size: 11.5px;
  border-radius: 999px;
  color: var(--text-2);
  background: #fff;
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition);
}
.act-btn:hover {
  color: var(--primary);
  border-color: var(--primary);
}
.act-btn.recall:hover {
  color: var(--danger);
  border-color: var(--danger);
}
.act-btn.ok {
  color: var(--success);
  border-color: var(--success);
}

/* 已撤回内联提示：保留撤回前原文，提供“重新编辑”回填到输入框 */
.recalled-card {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: min(720px, 90%);
  margin: 0 auto;
  padding: 6px 12px;
  font-size: 12.5px;
  color: var(--text-2);
  background: #f7f9ff;
  border: 1px dashed rgba(47, 107, 255, 0.22);
  border-radius: 10px;
}
.recalled-ico {
  flex-shrink: 0;
  color: var(--text-3);
}
.recalled-label {
  flex-shrink: 0;
  color: var(--text-3);
  font-weight: 500;
}
.recalled-text {
  flex: 1;
  min-width: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.recalled-edit {
  flex-shrink: 0;
  color: var(--primary);
  font-size: 12.5px;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid rgba(47, 107, 255, 0.25);
  transition: all var(--transition);
}
.recalled-edit:hover {
  background: rgba(47, 107, 255, 0.08);
}

/* 重新编辑行：右对齐（与用户消息一致），撤回原文就在这一行内。
   用浅色编辑卡 + 深色文字，避免蓝底白字的沉重感与可读性差。 */
.reedit-row {
  display: flex;
  justify-content: flex-end;
  width: 100%;
}
.reedit-box {
  width: min(720px, 78%);
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 9px 13px;
  color: var(--text-1);
  background: linear-gradient(135deg, rgba(47, 107, 255, 0.06), rgba(47, 107, 255, 0.02));
  border: 1px solid rgba(47, 107, 255, 0.28);
  border-top-right-radius: 6px;
  border-radius: 14px;
  box-shadow: var(--shadow-sm);
}
.reedit-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--text-1);
  font-size: 14px;
  line-height: 1.55;
  font-family: inherit;
}
.reedit-textarea::placeholder {
  color: var(--text-3);
}
.reedit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}
.reedit-cancel,
.reedit-send {
  padding: 4px 14px;
  font-size: 13px;
  border-radius: 999px;
  transition: all var(--transition);
}
.reedit-cancel {
  color: var(--text-2);
  border: 1px solid var(--border);
}
.reedit-cancel:hover {
  background: rgba(31, 79, 216, 0.06);
  color: var(--text-1);
}
.reedit-send {
  color: #fff;
  background: linear-gradient(120deg, var(--primary-3), var(--primary));
  font-weight: 500;
}
.reedit-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.reedit-send:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(47, 107, 255, 0.3);
}

.bubble.user {
  padding: 9px 14px;
  color: #fff;
  background: linear-gradient(135deg, var(--primary-3), var(--primary));
  border-top-right-radius: 6px;
  box-shadow: 0 4px 12px rgba(47, 107, 255, 0.18);
}

.bubble.assistant {
  padding: 10px 15px;
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

.empty-tip {
  color: var(--text-3);
}

/* ===== 工具执行过程（可折叠） ===== */
.steps {
  margin-bottom: 9px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #fafbff;
  overflow: hidden;
}

.steps-toggle {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 7px 11px;
  font-size: 12.5px;
  color: var(--text-2);
  text-align: left;
  transition: background var(--transition);
}
.steps-toggle:hover {
  background: rgba(31, 79, 216, 0.05);
}

.steps-text {
  flex: 1;
}

.steps-check {
  color: var(--success);
  font-weight: 700;
}

.steps-arrow {
  transition: transform var(--transition);
  font-size: 11px;
}
.steps-arrow.open {
  transform: rotate(180deg);
}

/* 思考中：旋转指示器 */
.spinner {
  width: 12px;
  height: 12px;
  flex-shrink: 0;
  border: 2px solid rgba(47, 107, 255, 0.25);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.steps-body {
  padding: 2px 11px 10px;
  border-top: 1px dashed var(--border);
  max-height: 260px;
  overflow: auto;
}

.step-item {
  margin-top: 8px;
  padding: 8px 10px;
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--text-2);
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Consolas', 'Menlo', 'Courier New', monospace;
  max-height: 200px;
  overflow: auto;
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

/* ===== 触屏设备：操作按钮常驻 =====
   桌面靠 .msg-row:hover 显示复制/撤回，触屏没有 hover，
   原本 opacity:0 + pointer-events:none 让按钮彻底不可达。
   这里改成静态排在气泡内容下方，始终可见可点。
   同时覆盖窄屏窗口：那种宽度下也不该依赖 hover。 */
@media (hover: none), (max-width: 820px) {
  .msg-actions {
    position: static;
    order: 5;
    opacity: 1;
    pointer-events: auto;
    transform: none;
    margin-top: 8px;
    padding-top: 7px;
    border-top: 1px dashed rgba(148, 163, 184, 0.4);
  }
  /* order:5 排在 .steps / .content 之后；左右对齐沿用气泡自身的方向 */
  .msg-row.user .msg-actions {
    justify-content: flex-end;
  }
  .msg-row.assistant .msg-actions {
    justify-content: flex-start;
  }

  /* 手指目标要够大：内边距撑到约 30px 高、点击区域含间隔 */
  .act-btn {
    padding: 5px 13px;
    font-size: 12px;
  }
  .act-btn:active {
    color: var(--primary);
    border-color: var(--primary);
    background: rgba(47, 107, 255, 0.08);
  }
  .act-btn.recall:active {
    color: var(--danger);
    border-color: var(--danger);
    background: rgba(239, 68, 68, 0.08);
  }

  /* 用户气泡是蓝底白字，分隔线和按钮要换配色才不糊 */
  .bubble.user .msg-actions {
    border-top-color: rgba(255, 255, 255, 0.32);
  }

  .recalled-edit:active {
    background: rgba(47, 107, 255, 0.12);
  }
  .reedit-cancel:active {
    background: rgba(31, 79, 216, 0.1);
  }
  .reedit-send:not(:disabled):active {
    transform: scale(0.97);
  }
}

/* ===== 移动端布局 ===== */
@media (max-width: 820px) {
  .msg-row {
    gap: 8px;
  }

  .avatar {
    width: 32px;
    height: 32px;
  }

  /* 助手侧要给头像 32px + 间距 8px 让位，否则气泡被挤出屏幕产生横向滚动 */
  .bubble.assistant {
    max-width: calc(100% - 42px);
    padding: 9px 12px;
  }
  .bubble.user {
    max-width: 88%;
    padding: 8px 13px;
  }

  .content {
    font-size: 15px;
  }

  /* 撤回提示卡：窄屏优先保证「重新编辑」按钮可见，原文压到 1 行 */
  .recalled-card {
    max-width: 100%;
    gap: 6px;
    padding: 6px 10px;
    font-size: 12px;
  }
  .recalled-text {
    -webkit-line-clamp: 1;
  }

  /* 重新编辑框占满可用宽度，手机上不再缩在右侧 78% */
  .reedit-box {
    width: 100%;
  }

  /* 过程步骤里的内容较长，压低高度避免撑满整屏 */
  .steps-body {
    max-height: 200px;
  }
  .step-item {
    font-size: 11px;
    max-height: 150px;
  }

  /* 「已复制」态在触屏下 1.2s 后自动还原，给个视觉过渡更明显 */
  .act-btn.ok {
    background: rgba(16, 185, 129, 0.1);
  }
}
</style>
