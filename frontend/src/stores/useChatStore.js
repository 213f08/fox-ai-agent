import { reactive, computed } from 'vue'
import { fetchSseChat } from '../api/chat'

const STORAGE_KEY = 'fox-ai-sessions'
const NEW_TITLE = '新的对话'

let singleton = null

/**
 * 数据清洗：补齐旧版/损坏会话的缺失字段。
 * 说明：localStorage 中历史数据可能缺少 messages 数组（旧版契约或写入中断），
 * 若不补全，渲染 `session.messages.length` 会抛 TypeError 导致整页白屏。
 */
function sanitizeSessions(arr) {
  if (!Array.isArray(arr)) return []
  const now = Date.now()
  return arr
    .filter((s) => s && typeof s === 'object')
    .map((s, i) => ({
      id: typeof s.id === 'string' && s.id ? s.id : makeId(),
      title: typeof s.title === 'string' && s.title ? s.title : NEW_TITLE,
      messages: Array.isArray(s.messages) ? s.messages : [],
      createdAt: typeof s.createdAt === 'number' ? s.createdAt : now - i
    }))
}

function loadSessions() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    return sanitizeSessions(JSON.parse(raw))
  } catch (_) {
    return []
  }
}

let persistTimer = null

function persist(sessions) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions))
  } catch (_) {
    /* 存储满等异常静默忽略 */
  }
}

/** 防抖写入：流式输出每个 chunk 都触发时，避免高频全量写 localStorage */
function persistSoon(sessions) {
  if (persistTimer) clearTimeout(persistTimer)
  persistTimer = setTimeout(() => {
    persistTimer = null
    persist(sessions)
  }, 300)
}

function makeId() {
  return 'chat-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8)
}

/**
 * 全局单例会话 store：本地会话 CRUD + 流式发送编排。
 * 数据契约：sessions: [{ id, title, messages: [{role, content}], createdAt }]
 */
export function useChatStore() {
  if (singleton) return singleton

  const initialSessions = loadSessions()
  const state = reactive({
    sessions: initialSessions,
    // 有历史会话时自动激活最近一个，避免刷新后误以为“数据丢了”回到欢迎页
    activeId: initialSessions.length ? initialSessions[0].id : '',
    sending: false
  })

  function createSession() {
    const id = makeId()
    state.sessions.unshift({ id, title: NEW_TITLE, messages: [], createdAt: Date.now() })
    state.activeId = id
    persist(state.sessions)
    return id
  }

  function removeSession(id) {
    const idx = state.sessions.findIndex((s) => s.id === id)
    if (idx < 0) return
    state.sessions.splice(idx, 1)
    if (state.activeId === id) {
      state.activeId = state.sessions.length ? state.sessions[0].id : ''
    }
    if (!state.sessions.length) {
      createSession()
    } else {
      persist(state.sessions)
    }
  }

  function activate(id) {
    if (state.sending) return
    if (state.sessions.some((s) => s.id === id)) state.activeId = id
  }

  const activeSession = computed(
    () => state.sessions.find((s) => s.id === state.activeId) || null
  )

  function setTitleIfEmpty(sessionId, firstUserText) {
    const s = state.sessions.find((x) => x.id === sessionId)
    if (s && (!s.title || s.title === NEW_TITLE)) {
      s.title = firstUserText.slice(0, 20)
      persist(state.sessions)
    }
  }

  async function send(message) {
    const text = (message || '').trim()
    if (!text || state.sending) return

    // 无激活会话（如首次打开停留在欢迎页）时，自动新建会话，避免“输入了没反应”
    let session = activeSession.value
    if (!session) {
      createSession()
      session = activeSession.value
    }

    setTitleIfEmpty(session.id, text)
    session.messages.push({ role: 'user', content: text })

    // 必须用 reactive 包裹：assistantMsg.content 在流式期间被多次修改，
    // 若用普通对象，修改不会触发 Vue 依赖更新，导致 UI 不显示 AI 回复内容
    const assistantMsg = reactive({ role: 'assistant', content: '' })
    session.messages.push(assistantMsg)

    state.sending = true
    persist(state.sessions)

    try {
      await fetchSseChat(text, session.id, {
        onChunk: (chunk) => {
          assistantMsg.content += (assistantMsg.content ? '\n' : '') + chunk
          persistSoon(state.sessions)
        },
        onDone: () => {
          if (!assistantMsg.content) assistantMsg.content = '（未收到有效回复）'
        },
        onError: (e) => {
          if (!assistantMsg.content) {
            assistantMsg.content = '请求出错：' + (e && e.message ? e.message : '网络异常，请确认后端已启动')
          }
        }
      })
    } finally {
      state.sending = false
      // 收口：清掉未触发的防抖定时器并立即落盘
      if (persistTimer) {
        clearTimeout(persistTimer)
        persistTimer = null
      }
      persist(state.sessions)
    }
  }

  singleton = {
    state,
    activeSession,
    createSession,
    removeSession,
    activate,
    send
  }
  return singleton
}
