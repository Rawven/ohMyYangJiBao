// 获取基金历史净值 — 从东方财富 API 直接爬取
import { isMainModule, parseArgs, getNavHistoryFromApi, showHelp } from './api.mjs'
const USAGE = 'fund nav <code> [--days=365]'
const HELP_DESC = '历史净值明细'


export default async function getNavHistory(code, days = 365) {
  if (!code) throw new Error('基金代码不能为空')
  return getNavHistoryFromApi(code.trim(), days)
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (!args.code || args.help) { showHelp(USAGE, HELP_DESC) }
  const result = await getNavHistory(args.code, Number(args.days) || 365)
  console.log(JSON.stringify(result, null, 2))
}
