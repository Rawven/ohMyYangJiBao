// 获取基金经理信息 — 从天天基金详情页抓取
import { parseArgs, fetchUrl, extractByPattern, isMainModule, showHelp } from './api.mjs'
const USAGE = 'fund manager <code>'
const HELP_DESC = '基金经理信息'


export default async function getFundManager(code) {
  if (!code) throw new Error('基金代码不能为空')
  code = code.trim()

  const html = await (await fetchUrl(`https://fundf10.eastmoney.com/jbgk_${code}.html`)).text()

  const managerName = extractByPattern(html, /基金经理人[\s\S]*?<a[^>]*>([^<]+)<\/a>/)
  const company = extractByPattern(html, /基金管理人[\s\S]*?<a[^>]*>([^<]+)<\/a>/)

  return { fundCode: code, managerName, company }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (!args.code || args.help) { showHelp(USAGE, HELP_DESC) }
  const result = await getFundManager(args.code)
  console.log(JSON.stringify(result, null, 2))
}
