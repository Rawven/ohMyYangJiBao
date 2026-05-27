// 分析持仓风险（集中度、数量、行业暴露）
import getPortfolio from './get-portfolio.js'

export default async function analyzePortfolioRisk() {
  const holdings = await getPortfolio()
  if (!holdings?.length) return { message: '当前没有持仓数据' }

  const totalValue = holdings.reduce((s, h) => s + (h.marketValue || 0), 0)
  if (totalValue <= 0) return { message: '持仓总市值为 0' }

  const distributions = holdings.map(h => ({
    fundCode: h.fundCode,
    fundName: h.fundName,
    ratio: Math.round(h.marketValue / totalValue * 10000) / 100
  }))

  const warnings = []
  for (const d of distributions) {
    if (d.ratio > 30) warnings.push({ type: '集中度风险', fundCode: d.fundCode, message: `占比 ${d.ratio}% > 30%`, suggestion: '建议适当减仓，控制在 20% 以内' })
    else if (d.ratio > 20) warnings.push({ type: '集中度关注', fundCode: d.fundCode, message: `占比 ${d.ratio}% 接近 20%`, suggestion: '关注后续变化，考虑分散配置' })
  }

  if (holdings.length === 1) warnings.push({ type: '持仓数量不足', message: '只有 1 只基金', suggestion: '建议持有 3-5 只不同风格基金分散风险' })
  else if (holdings.length > 8) warnings.push({ type: '持仓过多', message: `${holdings.length} 只基金`, suggestion: '建议精简到 5-8 只核心基金' })

  const riskLevel = warnings.length === 0 ? '低' : warnings.length <= 2 ? '中' : '高'
  const summary = warnings.length === 0 ? '持仓结构健康，风险较低'
    : warnings.length <= 2 ? '有少量风险点，建议参考预警调整'
    : '风险较高，建议重点关注集中度'

  return { totalFunds: holdings.length, totalValue: Math.round(totalValue * 100) / 100, riskLevel, summary, distributions, warnings }
}

if (import.meta.url === process.argv[1]) {
  const result = await analyzePortfolioRisk()
  console.log(JSON.stringify(result, null, 2))
}
