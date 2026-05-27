// 搜索基金列表 — 支持关键字、类型、公司、净值范围、涨跌幅范围筛选
import { api, parseArgs } from './api.js'

export default async function searchFunds(params = {}) {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v != null && v !== '') q.set(k, v)
  }
  if (!q.has('page')) q.set('page', '1')
  if (!q.has('size')) q.set('size', '20')
  return api(`/api/funds?${q}`)
}

// 命令行入口
const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  const result = await searchFunds(args)
  console.log(JSON.stringify(result, null, 2))
}
