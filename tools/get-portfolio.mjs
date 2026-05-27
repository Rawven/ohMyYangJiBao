// 我的持仓 — 从本地文件 ~/.ohmyyangjibao/holdings.json 读取/管理
import { parseArgs, isMainModule, readHoldings, writeHoldings, showHelp } from './api.mjs'
const USAGE = 'fund portfolio [--add=code --shares= --cost=]'
const HELP_DESC = '持仓管理：查看/添加(--add)/删除(--remove)'


export default async function getPortfolio() {
  const holdings = readHoldings()
  const enriched = await Promise.all(holdings.map(async h => {
    try {
      const { getRealtimeNav } = await import('./api.mjs')
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

async function resolveName(code) {
  try {
    const { getFundList } = await import('./api.mjs')
    const list = await getFundList()
    const f = list.find(i => i.code === code)
    return f ? f.name : code
  } catch { return code }
}

async function addHolding(code, shares, cost) {
  const holdings = readHoldings()
  const existing = holdings.find(h => h.fundCode === code)
  if (existing) {
    const totalShares = existing.shares + shares
    const totalCost = existing.shares * existing.costNav + shares * cost
    existing.shares = totalShares
    existing.costNav = Math.round(totalCost / totalShares * 10000) / 10000
  } else {
    const name = await resolveName(code)
    holdings.push({ fundCode: code, shares, costNav: cost, fundName: name })
  }
  writeHoldings(holdings)
  console.log(JSON.stringify({ success: true, code, shares, cost, total: holdings.length }, null, 2))
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (args.help) { showHelp(USAGE, HELP_DESC) }

  if (args.add) {
    if (!args.shares || !args.cost) {
      console.error('请指定 --shares=份额 和 --cost=成本价')
      console.error('示例: node tools/get-portfolio.mjs --add=001856 --shares=1000 --cost=2.5')
      process.exit(1)
    }
    await addHolding(args.add, Number(args.shares), Number(args.cost))
  } else {
    const result = await getPortfolio()
    console.log(JSON.stringify(result, null, 2))
  }
}
