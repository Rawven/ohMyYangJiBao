// 对比多只基金 — 同时获取多只基金详情+持仓
import { isMainModule,  parseArgs, showHelp } from './api.mjs'
import getFundDetail from './get-fund-detail.mjs'
import getFundHoldings from './get-fund-holdings.mjs'
const USAGE = 'fund compare <code1,code2,...>'
const HELP_DESC = '多只基金详情+持仓对比'


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
  if (!args.codes || args.help) { showHelp(USAGE, HELP_DESC) }
  const result = await compareFunds(args.codes)
  console.log(JSON.stringify(result, null, 2))
}
