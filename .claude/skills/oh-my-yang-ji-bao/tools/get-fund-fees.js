// 获取基金费率 — 从天天基金详情页抓取
import { parseArgs, fetchUrl, extractByPattern } from './api.js'

export default async function getFundFees(code) {
  if (!code) throw new Error('基金代码不能为空')
  code = code.trim()

  const html = await (await fetchUrl(`https://fundf10.eastmoney.com/jbgk_${code}.html`)).text()

  const managementFee = extractByPattern(html, /管理费率[^：:]*[：:]([^%\n]+%)/)
  const custodianFee = extractByPattern(html, /托管费率[^：:]*[：:]([^%\n]+%)/)
  const serviceFee = extractByPattern(html, /销售服务费率[^：:]*[：:]([^%\n]+%)/)

  return { fundCode: code, managementFee, custodianFee, serviceFee }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run get-fund-fees.js --code=110011'); process.exit(1) }
  const result = await getFundFees(args.code)
  console.log(JSON.stringify(result, null, 2))
}
