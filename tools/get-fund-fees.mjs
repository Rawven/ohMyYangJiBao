// 获取基金费率 — 从天天基金详情页抓取
import { parseArgs, fetchUrl, extractByPattern, isMainModule, showHelp } from './api.mjs'
const USAGE = 'fund fees <code>'
const HELP_DESC = '管理费/托管费/销售服务费'


export default async function getFundFees(code) {
  if (!code) throw new Error('基金代码不能为空')
  code = code.trim()

  const html = await (await fetchUrl(`https://fundf10.eastmoney.com/jbgk_${code}.html`)).text()

  const managementFee = extractByPattern(html, /管理费率[\s\S]*?<td[^>]*>([^<]+%)\s*（/)
  const custodianFee = extractByPattern(html, /托管费率[\s\S]*?<td[^>]*>([^<]+%)\s*（/)
  const serviceFee = extractByPattern(html, /销售服务费率[\s\S]*?<td[^>]*>([^<]+%)\s*（/)

  return { fundCode: code, managementFee, custodianFee, serviceFee }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (!args.code || args.help) { showHelp(USAGE, HELP_DESC) }
  const result = await getFundFees(args.code)
  console.log(JSON.stringify(result, null, 2))
}
