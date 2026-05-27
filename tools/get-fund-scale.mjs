// 基金规模变化排行 — 从天天基金规模数据抓取净申购/规模变动
import { parseArgs, isMainModule, fetchUrl, getFundList, showHelp } from './api.mjs'
const USAGE = 'fund scale'
const HELP_DESC = '基金规模变化 & 净申购排名'


export default async function getFundScale(topN = 30) {
  const list = await getFundList()
  const candidates = list.filter(f => f.type.includes('混合') || f.type.includes('股票')).slice(0, 50)

  // 并行请求规模数据
  const results = await Promise.allSettled(candidates.map(async fund => {
    const url = `https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=gmbd&code=${fund.code}&rt=${Date.now()}`
    const text = await (await fetchUrl(url)).text()
    const rows = text.match(/<tr[^>]*>([\s\S]*?)<\/tr>/g)
    if (!rows?.length) return null

    for (const row of rows.slice(0, 3)) {
      const cells = row.match(/<td[^>]*>([\s\S]*?)<\/td>/g)
      if (!cells || cells.length < 6) continue

      const period = cells[0]?.replace(/<[^>]+>/g, '').trim()
      const subscribe = parseFloat(cells[1]?.replace(/<[^>]+>/g, '').replace(/,/g, ''))
      const redeem = parseFloat(cells[2]?.replace(/<[^>]+>/g, '').replace(/,/g, ''))
      const totalScale = parseFloat(cells[4]?.replace(/<[^>]+>/g, '').replace(/,/g, ''))
      const changeRate = cells[5]?.replace(/<[^>]+>/g, '').trim()

      if (subscribe != null && !isNaN(subscribe)) {
        const netFlow = subscribe - (redeem || 0)
        return {
          fundCode: fund.code,
          fundName: fund.name,
          fundType: fund.type,
          period,
          subscription: subscribe,
          redemption: redeem || 0,
          netFlow: Math.round(netFlow * 100) / 100,
          totalScale: totalScale || null,
          scaleChangeRate: changeRate,
        }
      }
    }
    return null
  }))

  const items = results
    .map(r => r.status === 'fulfilled' ? r.value : null)
    .filter(Boolean)
    .sort((a, b) => Math.abs(b.netFlow || 0) - Math.abs(a.netFlow || 0))
    .slice(0, topN)

  return {
    date: new Date().toISOString().slice(0, 10),
    total: items.length,
    items,
  }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (args.help) { showHelp(USAGE, HELP_DESC) }
  const result = await getFundScale()
  console.log(JSON.stringify(result, null, 2))
}
