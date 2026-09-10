<script setup>
import { computed } from 'vue'
import { useChatStore } from '../stores/useChatStore'

defineProps({
  // 侧栏当前是否可见（桌面 = 未折叠，移动端 = 抽屉展开）
  open: { type: Boolean, default: true }
})
defineEmits(['toggle'])

const store = useChatStore()
const badgeText = computed(() => {
  return store.state.mode === 'diet' ? '饮食健康·小养' : '全能智能体'
})
</script>

<template>
  <header class="topbar">
    <div class="topbar-left">
      <button
        class="icon-btn"
        :title="open ? '收起侧栏' : '展开侧栏'"
        :aria-label="open ? '收起侧栏' : '展开侧栏'"
        :aria-expanded="open"
        @click="$emit('toggle')"
      >
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
      <button class="btn btn-primary new-chat" title="新建对话" @click="store.createSession()">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
          <path d="M12 5v14M5 12h14" />
        </svg>
        <span class="btn-label">新建对话</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  height: calc(60px + var(--sat));
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: var(--sat) calc(18px + var(--sar)) 0 calc(18px + var(--sal));
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
  white-space: nowrap;
  color: var(--primary);
  background: rgba(47, 107, 255, 0.08);
  border: 1px solid rgba(47, 107, 255, 0.14);
}

.mode-badge .dot {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 0 0 3px rgba(47, 107, 255, 0.16);
}

/* ===== 移动端 ===== */
@media (max-width: 820px) {
  .topbar {
    height: calc(54px + var(--sat));
    gap: 6px;
    padding: var(--sat) calc(10px + var(--sar)) 0 calc(10px + var(--sal));
  }

  /* 桌面靠左右各 200px 的最小宽度把模式徽章顶到居中，
     移动端必须放开，否则总宽 400px 起步直接把屏幕撑爆 */
  .topbar-left,
  .topbar-right {
    min-width: 0;
    flex: 0 0 auto;
    gap: 4px;
  }

  .logo {
    gap: 0;
    margin-left: 0;
  }
  /* 只留 Logo 图标，省下约 55px */
  .logo-text {
    display: none;
  }
  .logo-mark {
    width: 28px;
    height: 28px;
  }

  .topbar-center {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    justify-content: center;
  }

  .mode-badge {
    gap: 6px;
    padding: 5px 11px;
    font-size: 12px;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* 新建对话收成圆形图标按钮，省下约 70px */
  .new-chat {
    width: 34px;
    height: 34px;
    padding: 0;
    border-radius: 50%;
    justify-content: center;
  }
  .new-chat .btn-label {
    display: none;
  }
}

/* 超窄屏（iPhone SE / 小屏安卓）：进一步压缩模式徽章 */
@media (max-width: 380px) {
  .mode-badge {
    font-size: 11px;
    padding: 4px 9px;
    gap: 5px;
  }
}
</style>
