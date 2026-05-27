// 热门基金 — 从基金列表中随机取一批（无后端时无法按涨跌幅排序）
// 如需真实排行，可调用天天基金页面爬取
import { parseArgs } from './api.js'
import { getFundList } from './api.js'

export default async function getTopFunds(type, topN = 20) {
  let list = await getFundList()
  if (type) list = list.filter(f => f.type.includes(type))
  // 随机打乱取 topN（因为没有实时数据时无法按涨幅排序）
  const shuffled = [...list].sort(() => Math.random() - 0.5)
  const items = shuffled.slice(0, Math.min(topN, 50)).map(f => ({
    code: f.code, name: f.name, type: f.type
  }))
  return { total: list.length, items }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  const result = await getTopFunds(args.type, Number(args.topN) || 20)
  console.log(JSON.stringify(result, null, 2))
}
