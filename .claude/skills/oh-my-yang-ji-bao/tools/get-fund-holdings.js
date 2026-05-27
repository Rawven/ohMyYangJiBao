// 获取基金前十大持仓股票
import { api, parseArgs } from './api.js'

export default async function getFundHoldings(code) {
  if (!code) throw new Error('基金代码不能为空')
  return api(`/api/funds/${code.trim()}/holdings`)
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run get-fund-holdings.js --code=110011'); process.exit(1) }
  const result = await getFundHoldings(args.code)
  console.log(JSON.stringify(result, null, 2))
}
