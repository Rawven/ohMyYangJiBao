// 获取基金费率信息
import { api, parseArgs } from './api.js'

export default async function getFundFees(code) {
  if (!code) throw new Error('基金代码不能为空')
  const detail = await api(`/api/funds/${code.trim()}`)
  return { fundCode: code.trim(), fundName: detail?.name }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run get-fund-fees.js --code=110011'); process.exit(1) }
  const result = await getFundFees(args.code)
  console.log(JSON.stringify(result, null, 2))
}
