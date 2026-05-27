// 共享 API 工具 — 所有工具通过此文件调用后端
const API_BASE = process.env.API_BASE || 'http://localhost:8080'

export async function api(path, opts = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...opts.headers },
    ...opts
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const body = await res.json()
  if (body.code !== 200) throw new Error(body.message || 'API Error')
  return body.data
}

export function pct(v, d = 2) {
  return v != null ? (v >= 0 ? '+' : '') + v.toFixed(d) + '%' : '--'
}

export function money(v) {
  return v != null ? '¥' + v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) : '--'
}

export function parseArgs(prefix = '') {
  const args = {}
  for (const arg of process.argv.slice(2)) {
    const [k, v] = arg.replace(/^--/, '').split('=')
    if (k.startsWith(prefix)) args[k.replace(prefix, '')] = v
  }
  return args
}
