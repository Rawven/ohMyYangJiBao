// 获取基金经理信息 — 从天天基金详情页抓取
import { parseArgs, fetchUrl, extractByPattern } from './api.js'

export default async function getFundManager(code) {
  if (!code) throw new Error('基金代码不能为空')
  code = code.trim()

  const html = await (await fetchUrl(`https://fundf10.eastmoney.com/jbgk_${code}.html`)).text()

  const managerName = extractByPattern(html, /基金经理[^<]*<[^>]*>([^<]+)</)
  const company = extractByPattern(html, /基金管理人[^<]*<[^>]*>([^<]+)</)

  return { fundCode: code, managerName, company }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run get-fund-manager.js --code=110011'); process.exit(1) }
  const result = await getFundManager(args.code)
  console.log(JSON.stringify(result, null, 2))
}
