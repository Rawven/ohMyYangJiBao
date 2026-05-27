// 持仓盈亏汇总
import getPortfolio from './get-portfolio.mjs'

export default async function getPortfolioSummary() {
  const holdings = await getPortfolio()
  if (!holdings?.length) return { totalMarketValue: 0, totalCost: 0, totalProfit: 0, totalProfitRate: 0, distribution: [] }

  const totalMarketValue = holdings.reduce((s, h) => s + (h.marketValue || 0), 0)
  const totalCost = holdings.reduce((s, h) => s + (h.costValue || 0), 0)
  const totalProfit = totalMarketValue - totalCost
  const totalProfitRate = totalCost > 0 ? Math.round(totalProfit / totalCost * 10000) / 100 : 0

  const distribution = holdings.map(h => ({
    fundName: h.fundName,
    value: h.marketValue || 0,
    percentage: totalMarketValue > 0 ? Math.round((h.marketValue / totalMarketValue) * 10000) / 100 : 0
  }))

  return { totalMarketValue, totalCost, totalProfit, totalProfitRate, distribution }
}

if (isMainModule(import.meta.url)) {
  const result = await getPortfolioSummary()
  console.log(JSON.stringify(result, null, 2))
}
