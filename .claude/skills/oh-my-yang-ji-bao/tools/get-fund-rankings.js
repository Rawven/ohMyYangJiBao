// 获取基金排行（按类型/涨跌幅排序）
import { api, parseArgs } from './api.js'

export default async function getFundRankings({ type, orderBy = 'dayIncrease', orderDir = 'desc', topN = 20 } = {}) {
  const q = new URLSearchParams({ page: '1', size: String(Math.min(topN, 50)) })
  if (type) q.set('type', type)
  if (orderBy) q.set('orderBy', orderBy)
  if (orderDir) q.set('orderDir', orderDir)
  return api(`/api/funds/screener?${q}`)
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  const result = await getFundRankings(args)
  console.log(JSON.stringify(result, null, 2))
}
