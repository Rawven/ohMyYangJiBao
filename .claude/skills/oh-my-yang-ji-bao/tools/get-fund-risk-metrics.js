// 获取基金风险指标（最大回撤、波动率、胜率）
import { parseArgs } from './api.js'
import getNavHistory from './get-nav-history.js'

export default async function getFundRiskMetrics(code) {
  if (!code) throw new Error('基金代码不能为空')
  const navList = await getNavHistory(code)
  if (!navList?.length) return { fundCode: code, message: '暂无净值数据' }

  navList.sort((a, b) => a.date.localeCompare(b.date))

  // 最大回撤
  let maxDrawdown = 0, peakNav = navList[0].nav, peakDate = navList[0].date, troughDate = peakDate
  for (const h of navList) {
    if (h.nav > peakNav) { peakNav = h.nav; peakDate = h.date }
    else { const dd = (h.nav - peakNav) / peakNav * 100; if (dd < maxDrawdown) { maxDrawdown = dd; troughDate = h.date } }
  }

  // 波动率
  const recent = navList.slice(-60)
  const dr = []
  for (let i = 1; i < recent.length; i++) {
    if (recent[i - 1].nav > 0) dr.push((recent[i].nav - recent[i - 1].nav) / recent[i - 1].nav)
  }
  let vol = 0
  if (dr.length >= 20) {
    const mean = dr.reduce((a, b) => a + b, 0) / dr.length
    vol = Math.sqrt(dr.reduce((a, b) => a + (b - mean) ** 2, 0) / dr.length) * Math.sqrt(252) * 100
  }

  // 胜率
  let up = 0, down = 0
  for (let i = 1; i < navList.length; i++) {
    if (navList[i].nav >= navList[i - 1].nav) up++; else down++
  }

  return {
    fundCode: code,
    dataRange: `${navList[0].date} 至 ${navList.at(-1).date}`,
    dataDays: navList.length,
    maxDrawdown: Math.round(maxDrawdown * 100) / 100,
    peakNav, peakDate, troughDate,
    annualizedVolatility: Math.round(vol * 100) / 100,
    winRate: (up + down > 0 ? Math.round(up / (up + down) * 1000) / 10 : 0) + '%',
    upDays: up, downDays: down
  }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run get-fund-risk-metrics.js --code=110011'); process.exit(1) }
  const result = await getFundRiskMetrics(args.code)
  console.log(JSON.stringify(result, null, 2))
}
