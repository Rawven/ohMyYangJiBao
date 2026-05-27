// 获取基金详情 — 从天天基金实时净值 API + 基金列表
import { parseArgs, getRealtimeNav, getFundList, extractByPattern, fetchUrl, isMainModule } from './api.mjs'

export default async function getFundDetail(code) {
  if (!code) throw new Error('基金代码不能为空')
  code = code.trim()

  // 从基金列表获取基础信息
  const list = await getFundList()
  const info = list.find(f => f.code === code)

  // 实时净值
  let realtime = null
  try { realtime = await getRealtimeNav(code) } catch (e) { console.warn(`获取实时净值失败 ${code}:`, e.message) }

  // 从详情页抓取成立日期和公司
  let establishDate = null, company = null
  try {
    const html = await (await fetchUrl(`https://fundf10.eastmoney.com/jbgk_${code}.html`)).text()
    company = extractByPattern(html, /基金管理人[\s\S]*?<a[^>]*>([^<]+)<\/a>/)
    const est = extractByPattern(html, /成立日期[^<]*<[^>]*>([\d-]+)</)
    if (est) establishDate = est
  } catch (e) { console.warn(`获取 F10 详情失败 ${code}:`, e.message) }

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
if (isMainModule(import.meta.url)) {
  if (!args.code) { console.error('用法: bun run get-fund-detail.js --code=110011'); process.exit(1) }
  const result = await getFundDetail(args.code)
  console.log(JSON.stringify(result, null, 2))
}
