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
      // 旧数据没有 steps 字段，补空数组，保证折叠面板能正常渲染
      messages: Array.isArray(s.messages)
          ? s.messages.map((m) => ({
              ...m,
              // 旧数据缺 steps / ts 时补齐，保证流式收尾与按天分组逻辑不报错
              steps: Array.isArray(m.steps) ? m.steps : [],
              ts: typeof m.ts === 'number' ? m.ts : (typeof s.createdAt === 'number' ? s.createdAt : now - i)
            }))
          : [],
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
    sending: false,
    // 当前流式请求的 AbortController 句柄；点击“停止生成”时调用 abort() 切断连接
    abortController: null
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
    session.messages.push({ role: 'user', content: text, ts: Date.now() })

    // 必须用 reactive 包裹：assistantMsg 在流式期间被多次修改，
    // 若用普通对象，修改不会触发 Vue 依赖更新，导致 UI 不显示 AI 回复内容。
    // content = 面向用户的最终回答；steps = 工具执行过程（前端折叠展示）
    const assistantMsg = reactive({ role: 'assistant', content: '', steps: [], ts: Date.now() })
    session.messages.push(assistantMsg)

    state.sending = true
    // 为本次请求创建 AbortController，供“停止生成”调用
    const ac = new AbortController()
    state.abortController = ac
    persist(state.sessions)

    try {
      await fetchSseChat(text, session.id, {
        signal: ac.signal,
        onChunk: (chunk) => {
          if (chunk.type === 'tool') {
            // 工具执行过程单独收集，不混入最终回答
            assistantMsg.steps.push(chunk.content)
          } else {
            assistantMsg.content += (assistantMsg.content ? '\n' : '') + chunk.content
          }
          persistSoon(state.sessions)
        },
        onDone: () => {
          if (!assistantMsg.content) assistantMsg.content = '（未收到有效回复）'
        },
        onAbort: () => {
          // 用户主动停止：若尚无任何内容，给一个占位提示，避免留白
          if (!assistantMsg.content && !assistantMsg.steps.length) {
            assistantMsg.content = '（已停止生成）'
          }
        },
        onError: (e) => {
          if (!assistantMsg.content) {
            assistantMsg.content = '请求出错：' + (e && e.message ? e.message : '网络异常，请确认后端已启动')
          }
        }
      })
    } finally {
      state.sending = false
      state.abortController = null
      // 收口：清掉未触发的防抖定时器并立即落盘
      if (persistTimer) {
        clearTimeout(persistTimer)
        persistTimer = null
      }
      persist(state.sessions)
    }
  }

  /** 主动终止当前流式生成（输入框“停止”按钮调用） */
  function stop() {
    if (!state.sending || !state.abortController) return
    state.abortController.abort()
    // finally 块会负责把 sending 复位并落盘
  }

  /**
   * 撤回消息：删除指定索引的消息，并在原位插入一条“已撤回”内联提示（含原文），
   * 数据留在消息列表里而非主输入框；需要继续修改时点击提示上的“重新编辑”再回填。
   * - 撤回用户消息时，其后的 AI 回复一并移除（避免“回答悬空”）。
   * - 流式生成中禁止撤回，防止破坏进行中的消息结构。
   */
  function recall(messageIndex) {
    if (state.sending) return
    const s = activeSession.value
    if (!s) return
    const msgs = s.messages
    if (messageIndex < 0 || messageIndex >= msgs.length) return

    const target = msgs[messageIndex]
    const recalledText = target.content || ''
    // 用户消息后若紧跟 AI 回复，一并移除
    let removeCount = 1
    if (target.role === 'user' && msgs[messageIndex + 1] && msgs[messageIndex + 1].role === 'assistant') {
      removeCount = 2
    }
    msgs.splice(messageIndex, removeCount)
    // 在原位插入“已撤回”内联提示，保留撤回前的原文
    msgs.splice(messageIndex, 0, {
      role: 'recalled',
      content: recalledText,
      steps: [],
      ts: Date.now()
    })
    persist(state.sessions)
  }

  /** 点击“重新编辑”：把已撤回条目就地变成一条右对齐的可编辑行（reedit），原文作为初始内容 */
  function startReEdit(messageIndex) {
    if (state.sending) return
    const s = activeSession.value
    if (!s) return
    const msgs = s.messages
    if (messageIndex < 0 || messageIndex >= msgs.length) return
    if (msgs[messageIndex].role !== 'recalled') return
    const content = msgs[messageIndex].content
    msgs.splice(messageIndex, 1, { role: 'reedit', content, steps: [], ts: Date.now() })
    persist(state.sessions)
  }

  /** 确认重新编辑：移除可编辑行，把修改后的文本作为新消息（追加到会话末尾）发送 */
  function submitReEdit(messageIndex, text) {
    if (state.sending) return
    const s = activeSession.value
    if (!s) return
    const msgs = s.messages
    if (messageIndex < 0 || messageIndex >= msgs.length) return
    if (msgs[messageIndex].role !== 'reedit') return
    msgs.splice(messageIndex, 1)
    persist(state.sessions)
    send(text)
  }

  /** 取消重新编辑：可编辑行还原为“已撤回”提示 */
  function cancelReEdit(messageIndex) {
    if (state.sending) return
    const s = activeSession.value
    if (!s) return
    const msgs = s.messages
    if (messageIndex < 0 || messageIndex >= msgs.length) return
    if (msgs[messageIndex].role !== 'reedit') return
    const content = msgs[messageIndex].content
    msgs.splice(messageIndex, 1, { role: 'recalled', content, steps: [], ts: Date.now() })
    persist(state.sessions)
  }

  singleton = {
    state,
    activeSession,
    createSession,
    removeSession,
    activate,
    send,
    stop,
    recall,
    startReEdit,
    submitReEdit,
    cancelReEdit
  }
  return singleton
}
