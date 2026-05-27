// 一键基金分析 — 并行获取详情+持仓+业绩+经理+费率+风险
import { isMainModule, parseArgs, showHelp } from './api.mjs'
import getFundDetail from './get-fund-detail.mjs'
import getFundHoldings from './get-fund-holdings.mjs'
import getFundPerformance from './get-fund-performance.mjs'
import getFundManager from './get-fund-manager.mjs'
import getFundFees from './get-fund-fees.mjs'
import getFundRiskMetrics from './get-fund-risk-metrics.mjs'
const USAGE = 'fund analyze <code>'
const HELP_DESC = '一键基金分析：净值+持仓+业绩+经理+费率+风险'


export default async function analyzeFund(code) {
  if (!code) throw new Error('基金代码不能为空')

  const [detail, holdings, perf, manager, fees, risk] = await Promise.allSettled([
    getFundDetail(code),
    getFundHoldings(code),
    getFundPerformance(code),
    getFundManager(code),
    getFundFees(code),
    getFundRiskMetrics(code),
  ])

  const ok = (r) => r.status === 'fulfilled' ? r.value : { error: r.reason?.message }

  return {
    code,
    detail: ok(detail),
    holdings: ok(holdings),
    performance: ok(perf),
    manager: ok(manager),
    fees: ok(fees),
    risk: ok(risk),
  }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (!args.code || args.help) { showHelp(USAGE, HELP_DESC) }
  const result = await analyzeFund(args.code)
  console.log(JSON.stringify(result, null, 2))
}
