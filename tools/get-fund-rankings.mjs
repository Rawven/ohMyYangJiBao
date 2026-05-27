// 基金涨幅排行 — 从天天基金 rankhandler API 获取真实排行数据
import { isMainModule, parseArgs, fetchUrl } from './api.mjs'

// 天天基金排行数据格式（逗号分隔）：
// 0:代码 1:名称 2:拼音 3:日期 4:单位净值 5:累计净值 6:日涨跌 7:近1周
// 8:近1月 9:近3月 10:近6月 11:近1年 12:近3年 13:今年以来 14:成立来
// 15:成立日期 16:...
const COL = { code: 0, name: 1, nav: 4, accNav: 5, dayInc: 6, w1: 7, m1: 8, m3: 9, m6: 10, y1: 11, y3: 12, ytd: 13 }

export default async function getFundRankings({ type, orderBy = 'm1', orderDir = 'desc', topN = 20 } = {}) {
  // type 映射
  const typeMap = {
    '股票型': 'gp', '混合型': 'hh', '债券型': 'zq', '指数型': 'zs',
    'QDII': 'qdii', 'FOF': 'fof', '货币型': 'hb',
  }
  const ft = typeMap[type] || 'all'

  const sortField = orderBy === 'dayInc' ? 'zzf' : orderBy === 'm3' ? 'lnzf' : 'zzf'

  const url = `https://fund.eastmoney.com/data/rankhandler.aspx?op=ph&dt=kf&ft=${ft}&rs=&gs=0&sc=${sortField}&st=${orderDir}&pi=1&pn=${topN}`
  const text = await (await fetchUrl(url, {
    headers: { 'Referer': 'https://fund.eastmoney.com/data/fundranking.html' }
  })).text()

  // 解析 {datas:[...], allRecords:N, ...} 非标准 JSON
  const m = text.match(/{datas:\s*\[(.*?)\],\s*allRecords:\s*(\d+)/)
  if (!m) throw new Error('解析排行数据失败')

  const records = m[1]
  const total = parseInt(m[2]) || 0

  // 手动解析 CSV 格式（考虑引号内的逗号）
  const items = []
  let buf = '', depth = 0
  for (const ch of records) {
    if (ch === '"') { depth ^= 1; continue }
    if (ch === ',' && !depth) {
      const cols = buf.split(',')
      if (cols.length > 14) {
        items.push({
          code: cols[COL.code],
          name: cols[COL.name],
          nav: parseFloat(cols[COL.nav]) || 0,
          dayIncrease: parseFloat(cols[COL.dayInc]) || null,
          weekIncrease: parseFloat(cols[COL.w1]) || null,
          monthIncrease: parseFloat(cols[COL.m1]) || null,
          quarterIncrease: parseFloat(cols[COL.m3]) || null,
          halfYearIncrease: parseFloat(cols[COL.m6]) || null,
          yearIncrease: parseFloat(cols[COL.y1]) || null,
          threeYearIncrease: parseFloat(cols[COL.y3]) || null,
          ytdIncrease: parseFloat(cols[COL.ytd]) || null,
        })
      }
      buf = ''
    } else {
      buf += ch
    }
  }

  return { total, items: items.slice(0, topN).map((item, i) => ({ ...item, rank: i + 1 })) }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  const result = await getFundRankings(args)
  console.log(JSON.stringify(result, null, 2))
}
