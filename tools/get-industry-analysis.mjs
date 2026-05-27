// 行业板块分析 — 从东方财富行情 API 获取实时板块涨跌数据
import { isMainModule, parseArgs, fetchUrl } from './api.mjs'

export default async function getIndustryAnalysis(industryName) {
  // 使用东方财富 push2 API 获取申万行业板块实时数据
  const url = 'https://push2.eastmoney.com/api/qt/clist/get?cb=&pn=1&pz=50&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:90+t:2&fields=f12,f14,f2,f3,f4,f8'
  const res = await fetchUrl(url, {
    headers: { 'Referer': 'https://quote.eastmoney.com/' }
  })
  const json = await res.json()
  const list = json?.data?.diff || []

  const industries = list.map(item => ({
    industryName: item.f14,
    code: item.f12,
    price: item.f2,
    changePct: item.f3,
    changeAmount: item.f4,
    turnover: item.f8,
    trend: item.f3 > 0 ? 'up' : item.f3 < 0 ? 'down' : 'stable',
  }))

  // 按涨跌幅降序
  industries.sort((a, b) => (b.changePct || 0) - (a.changePct || 0))

  let result = {
    date: new Date().toISOString().slice(0, 10),
    total: industries.length,
    industries,
  }

  // 如果指定了行业，过滤并搜索相关基金
  if (industryName) {
    const filtered = industries.filter(i => i.industryName.includes(industryName))
    result = { ...result, industries: filtered }

    try {
      const { getFundList } = await import('./api.js')
      const list = await getFundList()
      const relatedFunds = list
        .filter(f => f.name.includes(industryName))
        .slice(0, 10)
        .map(f => ({ code: f.code, name: f.name, type: f.type }))
      result.relatedFunds = relatedFunds
      result.fundCount = relatedFunds.length
    } catch (e) {
      console.warn(`搜索「${industryName}」相关基金失败:`, e.message)
    }
  }

  // 行情概要
  const upCount = industries.filter(i => i.trend === 'up').length
  const downCount = industries.filter(i => i.trend === 'down').length
  result.summary = `行业板块 ${industries.length} 个，上涨 ${upCount} 个，下跌 ${downCount} 个`

  return result
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  const result = await getIndustryAnalysis(args.industry)
  console.log(JSON.stringify(result, null, 2))
}
