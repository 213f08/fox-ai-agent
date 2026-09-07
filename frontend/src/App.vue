<script setup>
import { ref } from 'vue'
import TopBar from './components/TopBar.vue'
import Sidebar from './components/Sidebar.vue'
import WelcomeView from './components/WelcomeView.vue'
import ChatView from './components/ChatView.vue'
import { useChatStore } from './stores/useChatStore'

const store = useChatStore()
// store.activeSession 是 computed ref；只有 setup 顶层 ref 在 template 中会自动 unwrap，
// 嵌套属性访问（store.activeSession）不会 unwrap，会拿到 ref 对象本身导致渲染异常
const activeSession = store.activeSession
const collapsed = ref(false)
</script>

<template>
  <div class="app-shell" :class="{ 'is-collapsed': collapsed }">
    <TopBar :collapsed="collapsed" @toggle="collapsed = !collapsed" />

    <div class="app-body">
      <Sidebar :collapsed="collapsed" />

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
</style>
