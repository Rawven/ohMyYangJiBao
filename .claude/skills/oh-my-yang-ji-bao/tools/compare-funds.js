// 对比多只基金的核心指标和前三大持仓
import { api, parseArgs } from './api.js'

export default async function compareFunds(codesStr) {
  if (!codesStr) throw new Error('基金代码不能为空')
  return api(`/api/funds/compare?codes=${encodeURIComponent(codesStr)}`)
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.codes) { console.error('用法: bun run compare-funds.js --codes=110011,005844'); process.exit(1) }
  const result = await compareFunds(args.codes)
  console.log(JSON.stringify(result, null, 2))
}
