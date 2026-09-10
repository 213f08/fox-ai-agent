<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import TopBar from './components/TopBar.vue'
import Sidebar from './components/Sidebar.vue'
import WelcomeView from './components/WelcomeView.vue'
import ChatView from './components/ChatView.vue'
import { useChatStore } from './stores/useChatStore'

const store = useChatStore()
// store.activeSession 是 computed ref；只有 setup 顶层 ref 在 template 中会自动 unwrap，
// 嵌套属性访问（store.activeSession）不会 unwrap，会拿到 ref 对象本身导致渲染异常
const activeSession = store.activeSession

// 桌面端：侧栏折叠（宽度收到 0）
const collapsed = ref(false)
// 移动端：抽屉是否展开。与 collapsed 分开存，
// 这样桌面折叠状态不会污染移动端，窗口来回缩放时体验不会错乱。
const drawerOpen = ref(false)
// 当前是否处于移动端布局（与 global.css 的 --bp-mobile 保持一致：820px）
const isMobile = ref(false)

let mq = null

function syncViewport(e) {
  const matched = e ? e.matches : mq.matches
  if (matched === isMobile.value) return
  isMobile.value = matched
  // 回到桌面布局时收起抽屉，避免下次再缩回移动端时抽屉莫名是打开的
  if (!matched) drawerOpen.value = false
}

onMounted(() => {
  mq = window.matchMedia('(max-width: 820px)')
  syncViewport()
  // 旧版 Safari(<14) 只有已废弃的 addListener，这里做兼容
  if (mq.addEventListener) mq.addEventListener('change', syncViewport)
  else mq.addListener(syncViewport)
})

onUnmounted(() => {
  if (!mq) return
  if (mq.removeEventListener) mq.removeEventListener('change', syncViewport)
  else mq.removeListener(syncViewport)
})

/** 侧栏当前是否可见：桌面看 collapsed 取反，移动端看抽屉状态 */
const sidebarOpen = computed(() => (isMobile.value ? drawerOpen.value : !collapsed.value))

function toggleSidebar() {
  if (isMobile.value) drawerOpen.value = !drawerOpen.value
  else collapsed.value = !collapsed.value
}

function closeDrawer() {
  drawerOpen.value = false
}
</script>

<template>
  <div class="app-shell" :class="{ 'is-collapsed': collapsed, 'is-mobile': isMobile }">
    <TopBar :open="sidebarOpen" @toggle="toggleSidebar" />

    <div class="app-body">
      <Sidebar
        :collapsed="collapsed"
        :mobile="isMobile"
        :open="drawerOpen"
        @close="closeDrawer"
      />

      <!-- 移动端抽屉遮罩：点击侧栏以外的任何地方都收起侧栏 -->
      <transition name="mask-fade">
        <div v-if="isMobile && drawerOpen" class="drawer-mask" @click="closeDrawer"></div>
      </transition>

      <main class="app-main">
        <WelcomeView v-if="!activeSession?.messages?.length" />
        <ChatView v-else />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.app-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg);
}

/* 移动端抽屉遮罩。z-index 低于侧栏(60)、高于内容，遮住顶栏，
   因此点顶栏区域也能关抽屉；重开抽屉靠侧栏内的入口按钮。 */
.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(15, 23, 42, 0.42);
  -webkit-backdrop-filter: blur(2px);
  backdrop-filter: blur(2px);
}

.mask-fade-enter-active,
.mask-fade-leave-active {
  transition: opacity var(--transition);
}
.mask-fade-enter-from,
.mask-fade-leave-to {
  opacity: 0;
}
</style>
