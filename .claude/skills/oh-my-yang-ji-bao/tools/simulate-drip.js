// 定投模拟 — 基于历史净值计算每月定投收益
import { parseArgs } from './api.js'
import getNavHistory from './get-nav-history.js'

export default async function simulateDrip(code, amount = 1000, months = 12) {
  if (!code) throw new Error('基金代码不能为空')
  months = Math.min(months, 60)

  const navList = await getNavHistory(code)
  if (!navList?.length) return { fundCode: code, message: '暂无净值数据' }

  navList.sort((a, b) => a.date.localeCompare(b.date))
  const startDate = navList[0].date
  let totalInvested = 0, totalShares = 0, count = 0

  for (let i = 0; i < months; i++) {
    const d = new Date(startDate)
    d.setMonth(d.getMonth() + i)
    const dateStr = d.toISOString().slice(0, 10)
    if (dateStr > navList.at(-1).date) break

    let navOnDate = null
    for (let j = navList.length - 1; j >= 0; j--) {
      if (navList[j].date <= dateStr) { navOnDate = navList[j]; break }
    }
    if (!navOnDate || navOnDate.nav <= 0) continue

    const shares = amount / navOnDate.nav
    totalInvested += amount
    totalShares += shares
    count++
  }

  if (count === 0) return { fundCode: code, message: '净值数据不足' }

  const latestNav = navList.at(-1).nav
  const marketValue = Math.round(totalShares * latestNav * 100) / 100
  const profit = Math.round((marketValue - totalInvested) * 100) / 100
  const profitRate = totalInvested > 0 ? Math.round(profit / totalInvested * 10000) / 100 : 0

  return {
    fundCode: code, monthlyAmount: amount, totalMonths: count,
    totalInvested, totalShares: Math.round(totalShares * 100) / 100,
    latestNav, marketValue, profit, profitRate,
    startDate, endDate: navList.at(-1).date
  }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run simulate-drip.js --code=110011 --amount=1000 --months=12'); process.exit(1) }
  const result = await simulateDrip(args.code, Number(args.amount) || 1000, Number(args.months) || 12)
  console.log(JSON.stringify(result, null, 2))
}
