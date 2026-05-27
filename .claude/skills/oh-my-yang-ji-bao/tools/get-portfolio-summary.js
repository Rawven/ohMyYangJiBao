// 获取持仓盈亏汇总（总市值/成本/盈亏/收益趋势/分布）
import { api } from './api.js'

export default async function getPortfolioSummary() {
  return api('/api/analysis')
}

if (import.meta.url === process.argv[1]) {
  const result = await getPortfolioSummary()
  console.log(JSON.stringify(result, null, 2))
}
