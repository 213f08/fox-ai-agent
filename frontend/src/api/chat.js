import axios from 'axios'

// 预置 Axios 实例（供后续普通 sync 等请求扩展；当前对话全部走流式 fetch）
const http = axios.create({
  baseURL: '/api',
  timeout: 60000
})

export default http

/**
 * 调用全能智能体 /ai/manus/chat（GET + SseEmitter），
 * 用 fetch + ReadableStream 手写解析 SSE，逐段回调文本。
 *
 * 为什么不用 EventSource：
 *  - 服务端 complete() 后 EventSource 会自动重连，导致重复调用同一智能体；
 *  - fetch 可精确控制连接生命周期，且能兼容非标准事件块。
 *
 * @param {string} message 用户问题
 * @param {string} chatId   会话 id（后端仅做必填参数，无跨请求上下文）
 * @param {{onChunk?: (text:string)=>void, onDone?: ()=>void, onError?: (e:Error)=>void}} handlers
 */
export async function fetchSseChat(message, chatId, { onChunk, onDone, onError } = {}) {
  const url = `/api/ai/manus/chat?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`

  // 超时保护：AI 生成可能较慢，但若连接挂起（后端半开/卡死），
  // sending 会永久卡在 true、输入框被禁用。强制超时断开，保证状态能复位。
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 180_000)

  let reader = null
  try {
    const res = await fetch(url, {
      method: 'GET',
      headers: { Accept: 'text/event-stream' },
      signal: controller.signal
    })

    if (!res.ok || !res.body) {
      throw new Error(`请求失败：HTTP ${res.status}`)
    }

    reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    // 处理一行：只取 data: 前缀，忽略注释(:)、空行、event:、retry: 等
    const flushLine = (raw) => {
      const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw
      if (!line.startsWith('data:')) return
      const data = line.slice(5).trimStart()
      if (data && onChunk) onChunk(data)
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      // 最后一段可能不完整，留待下次拼接
      buffer = lines.pop() ?? ''
      for (const raw of lines) flushLine(raw)
    }

    // 冲刷 decoder 与 buffer 剩余内容
    const rest = decoder.decode()
    if (rest) buffer += rest
    for (const raw of buffer.split('\n')) flushLine(raw)

    if (onDone) onDone()
  } catch (e) {
    const reason = e && e.name === 'AbortError'
      ? '请求超时（超过 180 秒无响应），请重试'
      : (e && e.message) || String(e)
    if (onError) onError(new Error(reason))
  } finally {
    clearTimeout(timeoutId)
    if (reader) {
      try {
        reader.releaseLock()
      } catch (_) {
        /* noop */
      }
    }
  }
}
