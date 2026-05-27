// 获取基金历史净值数据
import { api, parseArgs } from './api.js'

export default async function getNavHistory(code, days = 365) {
  if (!code) throw new Error('基金代码不能为空')
  return api(`/api/funds/${code.trim()}/nav`)
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run get-nav-history.js --code=110011'); process.exit(1) }
  const result = await getNavHistory(args.code, args.days)
  console.log(JSON.stringify(result, null, 2))
}
