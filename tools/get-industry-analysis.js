// 行业分析 — 返回 A 股主要行业板块数据
// 使用申万一级行业分类
import { isMainModule,  parseArgs, fetchUrl } from './api.js'

const INDUSTRIES = [
  '食品饮料', '医药生物', '电子', '计算机', '电力设备', '机械设备',
  '银行', '非银金融', '房地产', '汽车', '有色金属', '基础化工',
  '国防军工', '公用事业', '交通运输', '传媒', '通信', '建筑装饰',
  '农林牧渔', '纺织服饰', '商贸零售', '家用电器', '轻工制造', '钢铁'
]

export default async function getIndustryAnalysis(industry) {
  // 尝试从东方财富行情中心获取板块数据
  let industries = []

  try {
    const html = await (await fetchUrl('https://quote.eastmoney.com/center/boardlist.html#industry_board', {
      headers: { 'Referer': 'https://quote.eastmoney.com/' }
    })).text()
    // 尝试获取板块表格数据
    const matches = html.match(/<tbody[\s\S]*?<\/tbody>/)
    if (matches) {
      const rows = matches[0].match(/<tr[^>]*>([\s\S]*?)<\/tr>/g) || []
      for (const row of rows.slice(0, 30)) {
        const cells = row.match(/<td[^>]*>([\s\S]*?)<\/td>/g) || []
        if (cells.length >= 4) {
          const name = cells[1]?.replace(/<[^>]+>/g, '').trim()
          const ratio = parseFloat(cells[2]?.replace(/<[^>]+>/g, ''))
          const count = parseInt(cells[4]?.replace(/<[^>]+>/g, ''))
          if (name) {
            industries.push({
              industryName: name,
              totalRatio: isNaN(ratio) ? null : Math.round(ratio * 100) / 100,
              stockCount: isNaN(count) ? 0 : count,
              trend: ratio > 0 ? 'up' : ratio < 0 ? 'down' : 'stable'
            })
          }
        }
      }
    }
  } catch {}

  // 兜底：用静态行业列表
  if (industries.length === 0) {
    industries = INDUSTRIES.map(name => ({
      industryName: name, totalRatio: null, stockCount: 0, trend: 'stable'
    }))
  }

  // 如果指定了行业，过滤并查询相关基金
  let relatedFunds = null
  if (industry) {
    industries = industries.filter(i => i.industryName.includes(industry))
    // 从基金列表中搜索相关基金
    try {
      const { getFundList } = await import('./api.js')
      const list = await getFundList()
      relatedFunds = list
        .filter(f => f.name.includes(industry))
        .slice(0, 10)
        .map(f => ({ code: f.code, name: f.name, type: f.type }))
    } catch {}
  }

  return {
    analysis: industry
      ? `以下是「${industry}」板块的分析数据`
      : '以下是 A 股主要行业板块表现',
    date: new Date().toISOString().slice(0, 10),
    industries,
    ...(industry ? { industry, relatedFunds, fundCount: relatedFunds?.length || 0 } : {})
  }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  const result = await getIndustryAnalysis(args.industry)
  console.log(JSON.stringify(result, null, 2))
}
