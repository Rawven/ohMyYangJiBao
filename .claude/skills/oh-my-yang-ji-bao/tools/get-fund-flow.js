// 基金资金流向 — 从天天基金规模变化数据抓取
import { isMainModule,  fetchUrl, extractByPattern, extractAllByPattern } from './api.js'
import { isMainModule,  getFundList } from './api.js'

export default async function getFundFlow() {
  // 获取热门基金列表，尝试获取规模变化
  const list = await getFundList()
  const topFunds = list.filter(f => f.type.includes('混合') || f.type.includes('股票')).slice(0, 20)

  const result = []
  for (const fund of topFunds) {
    try {
      const url = `https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=gmbd&code=${fund.code}&rt=${Date.now()}`
      const text = await (await fetchUrl(url)).text()
      // 提取最新规模变化
      const rows = text.match(/<tr[^>]*>([\s\S]*?)<\/tr>/g) || []
      for (const row of rows.slice(0, 2)) {
        const cells = row.match(/<td[^>]*>([\s\S]*?)<\/td>/g) || []
        if (cells.length >= 4) {
          const netSubscribe = parseFloat(cells[1]?.replace(/<[^>]+>/g, '').replace(',', ''))
          const scaleChange = cells[3]?.replace(/<[^>]+>/g, '').trim()
          if (!isNaN(netSubscribe)) {
            result.push({
              fundCode: fund.code,
              fundName: fund.name,
              fundType: fund.type,
              institutionRatio: null,
              personalRatio: null,
              netSubscribe,
              scaleChangeRate: scaleChange
            })
            break
          }
        }
      }
    } catch {}
  }

  // 按净申购排序
  result.sort((a, b) => Math.abs(b.netSubscribe || 0) - Math.abs(a.netSubscribe || 0))
  return result.slice(0, 15)
}

if (isMainModule(import.meta.url)) {
  const result = await getFundFlow()
  console.log(JSON.stringify(result, null, 2))
}
