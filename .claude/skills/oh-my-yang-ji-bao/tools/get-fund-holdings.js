// 获取基金前十大持仓 — 从天天基金页面抓取
import { parseArgs, fetchUrl, extractByPattern, extractAllByPattern } from './api.js'

export default async function getFundHoldings(code) {
  if (!code) throw new Error('基金代码不能为空')
  code = code.trim()

  const html = await (await fetchUrl(`https://fund.eastmoney.com/${code}.html`)).text()

  // 报告日期
  const reportDate = extractByPattern(html, /持仓截止日期[：:]\s*([\d-]+)/)

  // 持仓表格行
  const rows = html.match(/<td[^>]*class="[^"]*left[^"]*"[^>]*>([\s\S]*?)<\/td>/g) || []
  const stockNames = html.match(/<td[^>]*class="[^"]*left[^"]*"[^>]*><a[^>]*>([^<]+)<\/a><\/td>/g)?.map(m => m.replace(/<[^>]+>/g, '').trim()) || []

  // 更稳健的方式：找持仓表格
  const tableMatch = html.match(/<table[^>]*class="[^"]*holdTable[^"]*"[^>]*>([\s\S]*?)<\/table>/i)
  let holdings = []
  if (tableMatch) {
    const rows = tableMatch[1].match(/<tr[^>]*>([\s\S]*?)<\/tr>/g) || []
    for (const row of rows.slice(1)) { // skip header
      const cells = row.match(/<td[^>]*>([\s\S]*?)<\/td>/g) || []
      if (cells.length >= 4) {
        const name = cells[0].replace(/<[^>]+>/g, '').trim()
        const codeMatch = cells[0].match(/code=(\d{6})/)
        const ratio = parseFloat(cells[2]?.replace(/<[^>]+>/g, '').trim())
        const change = parseFloat(cells[3]?.replace(/<[^>]+>/g, '').trim())
        if (name) {
          holdings.push({
            stockName: name,
            stockCode: codeMatch ? codeMatch[1] : null,
            holdRatio: isNaN(ratio) ? null : ratio,
            changeRatio: isNaN(change) ? null : change,
            reportDate
          })
        }
      }
    }
  }

  // 兜底：正则提取
  if (holdings.length === 0) {
    const lines = html.match(/<td[^>]*class="[^"]*left[^"]*"[^>]*>([^<]+)<\/td>/g) || []
    for (let i = 0; i < Math.min(lines.length, 10); i++) {
      const name = lines[i].replace(/<[^>]+>/g, '').trim()
      if (name && !name.includes('基金')) {
        holdings.push({ stockName: name, stockCode: null, holdRatio: null, changeRatio: null, reportDate })
      }
    }
  }

  return { fundCode: code, reportDate, holdings: holdings.slice(0, 10) }
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  if (!args.code) { console.error('用法: bun run get-fund-holdings.js --code=110011'); process.exit(1) }
  const result = await getFundHoldings(args.code)
  console.log(JSON.stringify(result, null, 2))
}
