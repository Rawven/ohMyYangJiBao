// 获取近期表现最好的基金列表
import { api, parseArgs } from './api.js'

export default async function getTopFunds(type, topN = 20) {
  const q = new URLSearchParams({ page: '1', size: String(Math.min(topN, 50)) })
  if (type) q.set('type', type)
  return api(`/api/funds/screener?${q}`)
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  const result = await getTopFunds(args.type, args.topN)
  console.log(JSON.stringify(result, null, 2))
}
