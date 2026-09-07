<script setup>
import { useChatStore } from '../stores/useChatStore'

defineProps({
  collapsed: { type: Boolean, default: false }
})

const store = useChatStore()

function fmtTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) {
    return d.toTimeString().slice(0, 5)
  }
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>

<template>
  <aside class="sidebar" :class="{ collapsed }">
    <div class="sidebar-inner">
      <div class="sidebar-title">
        <span>最近对话</span>
      </div>

      <div class="session-list">
        <div
          v-for="s in store.state.sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === store.state.activeId }"
          @click="store.activate(s.id)"
        >
          <svg class="s-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
          <span class="s-title">{{ s.title }}</span>
          <span class="s-time">{{ fmtTime(s.createdAt) }}</span>
          <button class="s-del" title="删除会话" @click.stop="store.removeSession(s.id)">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
            </svg>
          </button>
        </div>

        <div v-if="!store.state.sessions.length" class="sidebar-empty">暂无对话</div>
      </div>

      <div class="sidebar-footer">
        <p class="brand">Fox AI · 智能体百宝箱</p>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 264px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid var(--border);
  transition: width var(--transition);
  overflow: hidden;
}

.sidebar.collapsed {
  width: 0;
  border-right-color: transparent;
}

.sidebar-inner {
  width: 264px;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px 12px 14px;
}

.sidebar-title {
  padding: 4px 10px 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-2);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-right: 2px;
}

.session-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 10px;
  border-radius: 10px;
  cursor: pointer;
  color: var(--text-1);
  transition: background var(--transition);
  user-select: none;
}

.session-item:hover {
  background: rgba(31, 79, 216, 0.06);
}

.session-item.active {
  background: rgba(47, 107, 255, 0.1);
  color: var(--primary);
}

.s-icon {
  flex-shrink: 0;
  color: var(--text-3);
}
.session-item.active .s-icon {
  color: var(--primary);
}

.s-title {
  flex: 1;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.s-time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-3);
}

.s-del {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  color: var(--text-3);
  opacity: 0;
  transition: all var(--transition);
}
.session-item:hover .s-del {
  opacity: 1;
}
.s-del:hover {
  color: var(--danger);
  background: rgba(239, 68, 68, 0.1);
}

.sidebar-empty {
  padding: 20px 10px;
  text-align: center;
  font-size: 13px;
  color: var(--text-3);
}

.sidebar-footer {
  padding: 12px 10px 4px;
  border-top: 1px solid var(--border);
}
.sidebar-footer .brand {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-2);
}
.sidebar-footer .disclaimer {
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-3);
}
</style>
