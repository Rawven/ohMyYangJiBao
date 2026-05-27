// 对比多只基金 — 同时获取多只基金详情+持仓
import { parseArgs } from './api.js'
import getFundDetail from './get-fund-detail.js'
import getFundHoldings from './get-fund-holdings.js'

export default async function compareFunds(codesStr) {
  if (!codesStr) throw new Error('基金代码不能为空')
  const codes = codesStr.split(',').map(c => c.trim()).filter(Boolean)

  const results = await Promise.allSettled(codes.map(async code => {
    const detail = await getFundDetail(code)
    let holdings = []
    try { const h = await getFundHoldings(code); holdings = (h.holdings || []).slice(0, 3) } catch {}
    return { ...detail, topHoldings: holdings }
  }))

  return results.map(r => r.status === 'fulfilled' ? r.value : { code: 'error', error: r.reason?.message })
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.codes) { console.error('用法: bun run compare-funds.js --codes=110011,005844'); process.exit(1) }
  const result = await compareFunds(args.codes)
  console.log(JSON.stringify(result, null, 2))
}
