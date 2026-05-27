// 对比多只基金 — 同时获取多只基金详情+持仓
import { isMainModule,  parseArgs } from './api.mjs'
import getFundDetail from './get-fund-detail.mjs'
import getFundHoldings from './get-fund-holdings.mjs'

export default async function compareFunds(codesStr) {
  if (!codesStr) throw new Error('基金代码不能为空')
  const codes = codesStr.split(',').map(c => c.trim()).filter(Boolean)

  const results = await Promise.allSettled(codes.map(async code => {
    const detail = await getFundDetail(code)
    let holdings = []
    try { const h = await getFundHoldings(code); holdings = (h.holdings || []).slice(0, 3) } catch (e) { console.warn(`获取 ${code} 持仓失败:`, e.message) }
    return { ...detail, topHoldings: holdings }
  }))

  return results.map(r => r.status === 'fulfilled' ? r.value : { code: 'error', error: r.reason?.message })
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (!args.codes) { console.error('用法: bun run compare-funds.js --codes=110011,005844'); process.exit(1) }
  const result = await compareFunds(args.codes)
  console.log(JSON.stringify(result, null, 2))
}
