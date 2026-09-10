// ============================================================
// Cloudflare Pages Functions：把 /api/* 反代到 Fox 后端（微信云托管）
// 作用等同 Nginx 反代：前端同源请求 /api → 本函数转发到后端，
// 避免浏览器跨域；SSE 流式响应原样透传（不缓冲）。
// 部署后在 Pages 项目设置里无需任何额外配置，构建输出 dist 即可。
// ============================================================

// 你的后端地址（微信云托管公网域名），如需指向其他环境改这里
const BACKEND = 'https://fox-ai-agent-backend-309816-9-1483077047.sh.run.tcloudbase.com'

/**
 * 处理 /api/* 的所有方法（GET/POST/OPTIONS…），透传请求与响应。
 * SSE / 长连接：直接返回上游 Response.body 流，不聚合缓冲。
 */
export async function onRequest(context) {
  const { request, params } = context

  // 取出 /api/ 之后的路径（[[path]] 捕获多段）
  const path = Array.isArray(params.path) ? params.path.join('/') : (params.path || '')

  const url = new URL(request.url)
  const target = `${BACKEND}/api/${path}${url.search}`

  // 复制请求头，去掉 hop-by-hop 与 Cloudflare 专用头
  const headers = new Headers(request.headers)
  for (const h of [
    'host', 'cf-connecting-ip', 'cf-ray', 'cf-visitor', 'cf-ipcountry',
    'cf-worker', 'connection', 'keep-alive', 'transfer-encoding', 'upgrade'
  ]) {
    headers.delete(h)
  }
  // 后端按域名路由，显式带上目标 Host
  headers.set('Host', new URL(BACKEND).host)

  // 转发（GET/HEAD 无 body）
  const isBodyless = request.method === 'GET' || request.method === 'HEAD'
  const upstream = await fetch(target, {
    method: request.method,
    headers,
    body: isBodyless ? undefined : request.body,
    redirect: 'manual'
  })

  // 透传响应头（去掉 hop-by-hop，保留 content-type 等）
  const respHeaders = new Headers(upstream.headers)
  for (const h of ['connection', 'keep-alive', 'transfer-encoding', 'upgrade']) {
    respHeaders.delete(h)
  }

  // 直接流式返回上游 body（SSE 每帧即时到达，不缓冲）
  return new Response(upstream.body, {
    status: upstream.status,
    headers: respHeaders
  })
}
