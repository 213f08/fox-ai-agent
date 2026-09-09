<script setup>
import { useChatStore } from '../stores/useChatStore'

defineProps({
  collapsed: { type: Boolean, default: false }
})

const store = useChatStore()
// 注意：store 是普通对象，其 computed 属性（modeSessions）在模板里不会自动解包，
// 必须在这里解成顶层绑定，否则 v-for 会迭代到 ref 内部字段导致取 id 崩溃
const modeSessions = store.modeSessions

// 侧边栏入口：左侧「助手切换」区
const modes = [
  {
    key: 'manus',
    name: '全能助手',
    desc: '搜索·抓取·工具',
    icon: 'spark'
  },
  {
    key: 'diet',
    name: '饮食健康·小养',
    desc: '减脂·控糖·营养',
    icon: 'leaf'
  }
]

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
      <!-- 助手切换 -->
      <div class="mode-switch">
        <button
          v-for="m in modes"
          :key="m.key"
          class="mode-btn"
          :class="{ active: store.state.mode === m.key }"
          @click="store.setMode(m.key)"
        >
          <span class="m-icon">
            <svg v-if="m.icon === 'spark'" viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1" />
              <circle cx="12" cy="12" r="3.2" />
            </svg>
            <svg v-else viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 21c-4.5-2.6-7-6-7-10a7 7 0 0 1 14 0c0 4-2.5 7.4-7 10z" />
              <path d="M12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6z" />
            </svg>
          </span>
          <span class="m-body">
            <span class="m-name">{{ m.name }}</span>
            <span class="m-desc">{{ m.desc }}</span>
          </span>
          <span v-if="store.state.mode === m.key" class="m-check">✓</span>
        </button>
      </div>

      <div class="sidebar-title">
        <span>最近对话</span>
      </div>

      <div class="session-list">
        <div
          v-for="s in modeSessions"
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

        <div v-if="!modeSessions.length" class="sidebar-empty">暂无对话，快开始吧</div>
      </div>

      <div class="sidebar-footer">
        <p class="brand">Fox AI智能体 · 小养</p>
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

/* ===== 助手切换 ===== */
.mode-switch {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 2px 0 14px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  text-align: left;
  border-radius: 11px;
  color: var(--text-1);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all var(--transition);
}
.mode-btn:hover {
  background: rgba(31, 79, 216, 0.06);
}
.mode-btn.active {
  background: linear-gradient(120deg, rgba(47, 107, 255, 0.1), rgba(47, 107, 255, 0.05));
  border-color: rgba(47, 107, 255, 0.22);
}

.m-icon {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: var(--primary);
  background: rgba(47, 107, 255, 0.1);
}
.mode-btn:not(.active) .m-icon {
  color: var(--text-2);
  background: rgba(31, 79, 216, 0.06);
}

.m-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.m-name {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.m-desc {
  font-size: 11.5px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.m-check {
  flex-shrink: 0;
  color: var(--primary);
  font-size: 13px;
  font-weight: 700;
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
