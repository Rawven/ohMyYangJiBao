// 基金排行 — 从天天基金行情中心爬取涨幅榜
import { parseArgs, fetchUrl, extractByPattern } from './api.js'

export default async function getFundRankings({ type, orderBy = 'dayIncrease', orderDir = 'desc', topN = 20 } = {}) {
  // 使用天天基金排行页面
  const url = 'https://fund.eastmoney.com/data/fundranking.html'
  const html = await (await fetchUrl(url)).text()

  // 尝试从页面提取 fundData 的 JSON
  const match = html.match(/var fundData\s*=\s*(\{[\s\S]*?\});/)
  let items = []

  if (match) {
    try {
      const data = JSON.parse(match[1])
      const list = data?.data?.list || []
      items = list.slice(0, Math.min(topN, 50)).map(item => {
        const cols = item.split(',')
        return { code: cols[0], name: cols[1], type: cols[2], nav: Number(cols[3]) || 0, dayIncrease: Number(cols[5]) || 0 }
      })
    } catch {}
  }

  // 兜底：用基金列表随机
  if (items.length === 0) {
    const { getFundList } = await import('./api.js')
    const list = await getFundList()
    const filtered = type ? list.filter(f => f.type.includes(type)) : list
    items = filtered.slice(0, Math.min(topN, 50)).map(f => ({ code: f.code, name: f.name, type: f.type }))
  }

  // 按指定排序
  if (orderBy === 'nav') {
    items.sort((a, b) => orderDir === 'desc' ? (b.nav - a.nav) : (a.nav - b.nav))
  } else {
    items.sort((a, b) => orderDir === 'desc' ? ((b.dayIncrease || 0) - (a.dayIncrease || 0)) : ((a.dayIncrease || 0) - (b.dayIncrease || 0)))
  }

  const ranked = items.map((item, i) => ({ ...item, rank: i + 1 }))
  return { total: ranked.length, orderBy, orderDir, items: ranked }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  const result = await getFundRankings(args)
  console.log(JSON.stringify(result, null, 2))
}
