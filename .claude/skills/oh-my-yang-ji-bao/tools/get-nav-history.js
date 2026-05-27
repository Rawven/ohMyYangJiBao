// 获取基金历史净值 — 从东方财富 API 直接爬取
import { isMainModule, parseArgs, getNavHistoryFromApi } from './api.js'

export default async function getNavHistory(code, days = 365) {
  if (!code) throw new Error('基金代码不能为空')
  return getNavHistoryFromApi(code.trim(), days)
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (!args.code) { console.error('用法: bun run get-nav-history.js --code=110011'); process.exit(1) }
  const result = await getNavHistory(args.code, Number(args.days) || 365)
  console.log(JSON.stringify(result, null, 2))
}
