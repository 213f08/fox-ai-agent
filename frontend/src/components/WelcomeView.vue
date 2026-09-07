<script setup>
import ChatInput from './ChatInput.vue'
import { useChatStore } from '../stores/useChatStore'

const store = useChatStore()

const prompts = [
  {
    icon: 'search',
    title: '联网搜索',
    desc: '查最新资讯与版本动态',
    q: '用联网搜索查一下：Spring AI 最新稳定版本是多少？有哪些新特性？'
  },
  {
    icon: 'web',
    title: '抓取网页',
    desc: '提取并总结网页内容',
    q: '抓取并总结 https://spring.io 首页的主要内容'
  },
  {
    icon: 'doc',
    title: '生成报告',
    desc: '一键产出 PDF 文档',
    q: '帮我生成一份关于「AI 智能体」的 PDF 报告'
  },
  {
    icon: 'image',
    title: '图片搜索',
    desc: '搜索相关图片素材',
    q: '搜索几张北京故宫的图片看看'
  }
]

function send(text) {
  store.send(text)
}
</script>

<template>
  <div class="welcome">
    <div class="hero">
      <div class="brand-emoji">🦊</div>
      <h1 class="brand-title brand-gradient">Fox AI 智能体百宝箱</h1>
      <p class="brand-sub">一个框，解决你的搜索、抓取、报告与图片需求</p>

      <div class="prompt-grid">
        <button
          v-for="p in prompts"
          :key="p.title"
          class="prompt-card float-in"
          @click="send(p.q)"
        >
          <span class="pc-icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <template v-if="p.icon === 'search'">
                <circle cx="11" cy="11" r="7" />
                <path d="M21 21l-4.3-4.3" />
              </template>
              <template v-else-if="p.icon === 'web'">
                <circle cx="12" cy="12" r="9" />
                <path d="M3 12h18M12 3a15 15 0 0 1 0 18M12 3a15 15 0 0 0 0 18" />
              </template>
              <template v-else-if="p.icon === 'doc'">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <path d="M14 2v6h6M8 13h8M8 17h5" />
              </template>
              <template v-else>
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <path d="M21 15l-5-5L5 21" />
              </template>
            </svg>
          </span>
          <span class="pc-body">
            <span class="pc-title">{{ p.title }}</span>
            <span class="pc-desc">{{ p.desc }}</span>
          </span>
        </button>
      </div>
    </div>

    <div class="welcome-input">
      <ChatInput :sending="store.state.sending" @submit="send" />
      <p class="disclaimer">内容由 AI 生成，请注意甄别准确性</p>
    </div>
  </div>
</template>

<style scoped>
.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 24px 28px;
  overflow-y: auto;
}

.hero {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 860px;
}

.brand-emoji {
  font-size: 44px;
  line-height: 1;
  margin-bottom: 14px;
  filter: drop-shadow(0 6px 16px rgba(47, 107, 255, 0.25));
}

.brand-title {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-align: center;
}

.brand-sub {
  margin-top: 12px;
  font-size: 16px;
  color: var(--text-2);
  text-align: center;
}

.prompt-grid {
  margin-top: 40px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
  width: 100%;
}

.prompt-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 20px;
  text-align: left;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition);
}
.prompt-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-lg);
  border-color: rgba(47, 107, 255, 0.28);
}

.pc-icon {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: var(--primary);
  background: rgba(47, 107, 255, 0.1);
}

.pc-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.pc-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}
.pc-desc {
  font-size: 13px;
  color: var(--text-2);
}

.welcome-input {
  width: 100%;
  max-width: 860px;
  margin-top: 28px;
}

.disclaimer {
  margin-top: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--text-3);
}

@media (max-width: 640px) {
  .prompt-grid {
    grid-template-columns: 1fr;
  }
  .brand-title {
    font-size: 24px;
  }
}
</style>
