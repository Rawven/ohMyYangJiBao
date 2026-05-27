// 持仓风险评估 — 集中度 + 数量 + 行业暴露
import getPortfolio from './get-portfolio.mjs'
import getFundHoldings from './get-fund-holdings.mjs'

// 按股票名猜行业（同原 Java 逻辑）
function guessIndustry(name) {
  if (!name) return '其他'
  if (/茅台|五粮液|白酒|食品|伊利|海天/.test(name)) return '食品饮料'
  if (/药|医疗|生物|CXO|恒瑞|迈瑞/.test(name)) return '医药生物'
  if (/半导体|芯片|电子|中兴|立讯|京东方/.test(name)) return '电子/半导体'
  if (/宁德|新能源|光伏|隆基|比亚迪|阳光/.test(name)) return '新能源'
  if (/银行|招行|保险|证券|平安|中信/.test(name)) return '金融'
  if (/腾讯|阿里|百度|美团|互联|软件|科大/.test(name)) return '互联网/科技'
  if (/地产|万科|保利/.test(name)) return '房地产'
  if (/军工|航/.test(name)) return '国防军工'
  if (/黄金|有色|钢铁|矿产/.test(name)) return '有色/资源'
  if (/汽车|长城|上汽|长安/.test(name)) return '汽车'
  if (/电力|能源|煤炭|中石油|中石化/.test(name)) return '能源/电力'
  return '其他'
}

export default async function analyzePortfolioRisk() {
  const holdings = await getPortfolio()
  if (!holdings?.length) return { message: '当前没有持仓数据' }

  const totalValue = holdings.reduce((s, h) => s + (h.marketValue || 0), 0)
  if (totalValue <= 0) return { message: '持仓总市值为 0' }

  const distributions = holdings.map(h => ({
    fundCode: h.fundCode, fundName: h.fundName,
    ratio: Math.round(h.marketValue / totalValue * 10000) / 100
  }))

  const warnings = []
  for (const d of distributions) {
    if (d.ratio > 30) warnings.push({ type: '集中度风险', fundCode: d.fundCode, message: `占比 ${d.ratio}% > 30%`, suggestion: '建议适当减仓，控制在 20% 以内' })
    else if (d.ratio > 20) warnings.push({ type: '集中度关注', fundCode: d.fundCode, message: `占比 ${d.ratio}%，接近 20% 警戒线`, suggestion: '关注变化，考虑分散配置' })
  }

  if (holdings.length === 1) warnings.push({ type: '持仓数量不足', message: '只有 1 只基金', suggestion: '建议持有 3-5 只不同风格基金' })
  else if (holdings.length > 8) warnings.push({ type: '持仓过多', message: `${holdings.length} 只基金`, suggestion: '建议精简到 5-8 只核心基金' })

  // 行业暴露分析
  const industryExposure = {}
  for (const h of holdings) {
    try {
      const fh = await getFundHoldings(h.fundCode)
      for (const stock of (fh.holdings || [])) {
        if (stock.holdRatio == null) continue
        const ind = guessIndustry(stock.stockName)
        const weight = stock.holdRatio * (h.marketValue / totalValue)
        industryExposure[ind] = (industryExposure[ind] || 0) + weight
      }
    } catch (e) { console.warn(`获取 ${h.fundCode} 持仓失败:`, e.message) }
  }

  const sortedIndustries = Object.entries(industryExposure)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([industry, exposure]) => ({ industry, exposure: Math.round(exposure * 100) / 100 }))

  if (sortedIndustries[0]?.exposure > 40) {
    warnings.push({ type: '行业集中度风险', message: `「${sortedIndustries[0].industry}」暴露 ${sortedIndustries[0].exposure}%，过于集中`, suggestion: '建议增加其他行业配置' })
  }

  const riskLevel = warnings.length === 0 ? '低' : warnings.length <= 2 ? '中' : '高'
  const summary = warnings.length === 0 ? '持仓结构健康，风险较低'
    : warnings.length <= 2 ? '有少量风险点，建议参考预警'
    : '风险较高，建议重点关注集中度'

  return { totalFunds: holdings.length, totalValue: Math.round(totalValue * 100) / 100, riskLevel, summary, distributions, industryExposure: sortedIndustries, warnings }
}

if (isMainModule(import.meta.url)) {
  const result = await analyzePortfolioRisk()
  console.log(JSON.stringify(result, null, 2))
}
