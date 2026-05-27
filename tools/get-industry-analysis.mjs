// 行业板块分析 — 东方财富行情 API + 板块搜索
// 支持按名称搜索行业/概念板块，实时涨跌数据
import { isMainModule, parseArgs, fetchUrl } from './api.mjs'

function beijingDate() {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
}

// 板块名称→BK代码 映射（常用）
const BOARD_MAP = {
  '半导体': 'BK1036',
  '芯片': 'BK1036',
}

// 语义联想：搜索 A 时也匹配 B
const SEMANTIC_MAP = {
  '半导体': { keywords: ['半导体', '芯片', '集成电路', '封测', '电路板', '电子', '元件'], bk: 'BK1036' },
}

// 从东方财富搜索接口查找板块代码
async function searchBoard(name) {
  const url = `https://searchadapter.eastmoney.com/api/suggest/get?input=${encodeURIComponent(name)}&type=14&token=`
  const res = await fetch(url, { headers: { 'Referer': 'https://www.eastmoney.com/' } })
  const json = await res.json()
  const items = json?.QuotationCodeTable?.Data || []
  return items.filter(i => i.SecurityTypeName === '板块').map(i => ({
    name: i.Name, code: i.Code, marketType: i.MarketType, mktNum: i.MktNum,
  }))
}

// 获取板块实时行情
async function getBoardQuote(code) {
  const url = `https://push2.eastmoney.com/api/qt/stock/get?cb=&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fields=f43,f44,f45,f46,f47,f48,f50,f57,f58,f170,f171&secid=90.${code}`
  const res = await fetchUrl(url, { headers: { 'Referer': 'https://quote.eastmoney.com/' } })
  const json = await res.json()
  const d = json?.data
  if (!d) return null
  return {
    boardName: d.f58,
    code: d.f57,
    price: d.f43,
    open: d.f44,
    low: d.f45,
    high: d.f46,
    volume: d.f47,
    turnover: d.f48,
    changePct: d.f170,     // f170 = 涨跌幅
    amplitude: d.f171,     // f171 = 振幅
    trend: d.f170 > 0 ? 'up' : d.f170 < 0 ? 'down' : 'stable',
  }
}

// 获取二级行业列表
async function getIndustryList() {
  const url = 'https://push2.eastmoney.com/api/qt/clist/get?cb=&pn=1&pz=50&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:90+t:2&fields=f12,f14,f2,f3,f4,f8'
  const res = await fetchUrl(url, { headers: { 'Referer': 'https://quote.eastmoney.com/' } })
  const json = await res.json()
  return (json?.data?.diff || []).map(item => ({
    industryName: item.f14,
    code: item.f12,
    price: item.f2,
    changePct: item.f3,
    changeAmount: item.f4,
    turnover: item.f8,
    trend: item.f3 > 0 ? 'up' : item.f3 < 0 ? 'down' : 'stable',
  }))
}

export default async function getIndustryAnalysis(industryName) {
  const allIndustries = await getIndustryList()
  allIndustries.sort((a, b) => (b.changePct || 0) - (a.changePct || 0))

  const result = {
    date: beijingDate(),
    total: allIndustries.length,
    industries: allIndustries,
  }

  if (industryName) {
    // 语义匹配
    const semantic = SEMANTIC_MAP[industryName]
    const keywords = semantic ? semantic.keywords : [industryName]
    const filtered = allIndustries.filter(i => keywords.some(k => i.industryName.includes(k)))
    result.industries = filtered

    // 查询板块实时行情
    if (semantic?.bk) {
      try {
        const board = await getBoardQuote(semantic.bk)
        if (board) result.board = board
      } catch (e) { console.warn(`获取板块行情失败:`, e.message) }
    } else {
      // 搜索匹配板块
      try {
        const boards = await searchBoard(industryName)
        if (boards.length > 0) {
          const board = await getBoardQuote(boards[0].code)
          if (board) result.board = board
        }
      } catch (e) { console.warn(`搜索板块失败:`, e.message) }
    }

    // 搜索相关基金
    try {
      const { getFundList } = await import('./api.mjs')
      const list = await getFundList()
      const fundKeywords = industryName === '半导体'
          ? ['半导体', '芯片', '集成电路', '电子']
          : [industryName]
      result.relatedFunds = list
        .filter(f => fundKeywords.some(k => f.name.includes(k)))
        .slice(0, 10)
        .map(f => ({ code: f.code, name: f.name, type: f.type }))
      result.fundCount = result.relatedFunds.length
    } catch (e) {
      console.warn(`搜索相关基金失败:`, e.message)
    }
  }

  // 行情概要
  const upCount = allIndustries.filter(i => i.trend === 'up').length
  const downCount = allIndustries.filter(i => i.trend === 'down').length
  result.summary = `行业板块 ${allIndustries.length} 个，上涨 ${upCount} 个，下跌 ${downCount} 个`

  return result
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  const result = await getIndustryAnalysis(args.industry)
  console.log(JSON.stringify(result, null, 2))
}
