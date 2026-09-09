<script setup>
import { computed } from 'vue'
import { useChatStore, MODES } from '../stores/useChatStore'

defineProps({
  collapsed: { type: Boolean, default: false }
})
defineEmits(['toggle'])

const store = useChatStore()
const badgeText = computed(() => {
  const m = MODES[store.state.mode] || MODES.manus
  return store.state.mode === 'diet' ? '饮食健康·小养' : '全能智能体'
})
</script>

<template>
  <header class="topbar">
    <div class="topbar-left">
      <button class="icon-btn" :title="collapsed ? '展开侧栏' : '折叠侧栏'" @click="$emit('toggle')">
        <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>

      <div class="logo">
        <img class="logo-mark" src="/logo-fox.png" alt="Fox AI" />
        <span class="logo-text">Fox AI</span>
      </div>
    </div>

    <div class="topbar-center">
      <span class="mode-badge">
        <span class="dot"></span>
        {{ badgeText }}
      </span>
    </div>

    <div class="topbar-right">
      <button class="btn btn-primary" @click="store.createSession()">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
          <path d="M12 5v14M5 12h14" />
        </svg>
        新建对话
      </button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border);
  position: relative;
  z-index: 20;
}

.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 200px;
}

.topbar-right {
  justify-content: flex-end;
}

.logo {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-left: 4px;
}

.logo-mark {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  background: transparent;
  box-shadow: 0 4px 12px rgba(47, 107, 255, 0.32);
}

.logo-text {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 15px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: var(--primary);
  background: rgba(47, 107, 255, 0.08);
  border: 1px solid rgba(47, 107, 255, 0.14);
}

.mode-badge .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 0 0 3px rgba(47, 107, 255, 0.16);
}
</style>
