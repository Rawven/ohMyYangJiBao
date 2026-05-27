// 获取基金前十大持仓 — 从天天基金页面抓取
import { parseArgs, fetchUrl, extractByPattern, extractAllByPattern, isMainModule } from './api.mjs'

export default async function getFundHoldings(code) {
  if (!code) throw new Error('基金代码不能为空')
  code = code.trim()

  const html = await (await fetchUrl(`https://fund.eastmoney.com/${code}.html`)).text()

  // 报告日期（新格式：end_date span / 旧格式：持仓截止日期）
  let reportDate = extractByPattern(html, /end_date['"]>([\d-]+)/)
  if (!reportDate) reportDate = extractByPattern(html, /持仓截止日期[：:]\s*([\d-]+)/)

  const holdings = []

  // 优先找 position_shares 区域的表格（天天基金当前版本）
  const sharesMatch = html.match(/id=['"]position_shares['"][\s\S]*?<table[\s\S]*?<\/table>/i)
  if (sharesMatch) {
    const rows = sharesMatch[0].match(/<tr[^>]*>([\s\S]*?)<\/tr>/g) || []
    for (const row of rows.slice(1)) { // skip header
      const cells = row.match(/<td[^>]*>([\s\S]*?)<\/td>/g) || []
      if (cells.length >= 4) {
        const name = cells[0].replace(/<[^>]+>/g, '').trim()
        // ratio is in cells[1], change in cells[2]
        const codeMatch = cells[0].match(/code=(\d{6})/)
        const ratio = parseFloat(cells[1]?.replace(/<[^>]+>/g, '').trim())
        const change = cells[2] ? parseFloat(cells[2].replace(/<[^>]+>/g, '').trim()) : null
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

  // 兜底：从任何表格中提取 <a> 标签的 title（老版本页面或其他格式）
  if (holdings.length === 0) {
    const tableRows = html.match(/<tr[^>]*>[\s\S]*?<td[^>]*class="[^"]*alignLeft[^"]*"[^>]*>[\s\S]*?<a[^>]*title="([^"]+)"[\s\S]*?<\/tr>/g)
    if (tableRows) {
      for (const row of tableRows) {
        const name = row.match(/title="([^"]+)"/)?.[1]
        const ratioMatch = row.match(/<td[^>]*class="[^"]*alignRight[^"]*bold[^"]*"[^>]*>([\d.]+)%/)
        if (name) {
          holdings.push({
            stockName: name,
            stockCode: null,
            holdRatio: ratioMatch ? parseFloat(ratioMatch[1]) : null,
            changeRatio: null,
            reportDate
          })
        }
      }
    }
  }

  return { fundCode: code, reportDate, holdings: holdings.slice(0, 10) }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (!args.code) { console.error('用法: bun run get-fund-holdings.js --code=110011'); process.exit(1) }
  const result = await getFundHoldings(args.code)
  console.log(JSON.stringify(result, null, 2))
}
