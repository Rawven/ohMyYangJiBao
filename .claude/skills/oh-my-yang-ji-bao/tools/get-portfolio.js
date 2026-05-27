// 我的持仓 — 从本地文件 ~/.ohmyyangjibao/holdings.json 读取
// 通过 add-holding.js / remove-holding.js 管理
import { readHoldings } from './api.js'

export default async function getPortfolio() {
  const holdings = readHoldings()
  // 尝试获取实时净值
  const enriched = await Promise.all(holdings.map(async h => {
    try {
      const { getRealtimeNav } = await import('./api.js')
      const rt = await getRealtimeNav(h.fundCode)
      const currentNav = rt.nav || h.costNav
      const marketValue = h.shares * currentNav
      const costValue = h.shares * h.costNav
      return {
        ...h,
        currentNav,
        marketValue: Math.round(marketValue * 100) / 100,
        costValue: Math.round(costValue * 100) / 100,
        profit: Math.round((marketValue - costValue) * 100) / 100,
        profitRate: costValue > 0 ? Math.round((marketValue - costValue) / costValue * 10000) / 100 : 0
      }
    } catch {
      return { ...h, currentNav: h.costNav, marketValue: 0, costValue: 0, profit: 0, profitRate: 0 }
    }
  }))
  return enriched
}

if (import.meta.url === process.argv[1]) {
  const result = await getPortfolio()
  console.log(JSON.stringify(result, null, 2))
}
