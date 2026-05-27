// 获取基金详情 — 从天天基金实时净值 API + 基金列表
import { parseArgs, getRealtimeNav, getFundList, extractByPattern, fetchUrl } from './api.js'

export default async function getFundDetail(code) {
  if (!code) throw new Error('基金代码不能为空')
  code = code.trim()

  // 从基金列表获取基础信息
  const list = await getFundList()
  const info = list.find(f => f.code === code)

  // 实时净值
  let realtime = null
  try { realtime = await getRealtimeNav(code) } catch {}

  // 从详情页抓取成立日期和公司
  let establishDate = null, company = null
  try {
    const html = await (await fetchUrl(`https://fundf10.eastmoney.com/jbgk_${code}.html`)).text()
    company = extractByPattern(html, /基金管理人[^<]*<[^>]*>([^<]+)</)
    const est = extractByPattern(html, /成立日期[^<]*<[^>]*>([\d-]+)</)
    if (est) establishDate = est
  } catch {}

  return {
    code,
    name: realtime?.name || info?.name || code,
    type: info?.type || '未知',
    nav: realtime?.nav || 0,
    navDate: realtime?.navDate || null,
    dayIncrease: realtime?.estimatedChange ?? null,
    company,
    establishDate
  }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run get-fund-detail.js --code=110011'); process.exit(1) }
  const result = await getFundDetail(args.code)
  console.log(JSON.stringify(result, null, 2))
}
