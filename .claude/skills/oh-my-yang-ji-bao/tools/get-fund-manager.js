// 获取基金经理信息
import { api, parseArgs } from './api.js'

export default async function getFundManager(code) {
  if (!code) throw new Error('基金代码不能为空')
  // 后端无独立接口时从基金详情抓取
  const detail = await api(`/api/funds/${code.trim()}`)
  return { fundCode: code.trim(), fundName: detail?.name }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run get-fund-manager.js --code=110011'); process.exit(1) }
  const result = await getFundManager(args.code)
  console.log(JSON.stringify(result, null, 2))
}
