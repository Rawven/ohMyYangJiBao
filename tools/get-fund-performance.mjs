// 基金阶段收益率（近1周/1月/3月/6月/1年/3年/今年以来）
import { isMainModule,  parseArgs, showHelp } from './api.mjs'
import getNavHistory from './get-nav-history.mjs'
const USAGE = 'fund perf <code>'
const HELP_DESC = '阶段收益率（近1周/月/3月/6月/1年/3年/今年）'


export default async function getFundPerformance(code) {
  if (!code) throw new Error('基金代码不能为空')
  const navList = await getNavHistory(code, 1095)
  if (!navList?.length) return { fundCode: code, message: '暂无净值数据' }

  navList.sort((a, b) => a.date.localeCompare(b.date))
  const latest = navList.at(-1)
  const today = new Date()

  const periods = {
    '近1周': new Date(today - 7 * 864e5),
    '近1月': new Date(today.getFullYear(), today.getMonth() - 1, today.getDate()),
    '近3月': new Date(today.getFullYear(), today.getMonth() - 3, today.getDate()),
    '近6月': new Date(today.getFullYear(), today.getMonth() - 6, today.getDate()),
    '近1年': new Date(today.getFullYear() - 1, today.getMonth(), today.getDate()),
    '近3年': new Date(today.getFullYear() - 3, today.getMonth(), today.getDate()),
    '今年以来': new Date(today.getFullYear(), 0, 1),
  }

  const periodResults = []
  for (const [name, target] of Object.entries(periods)) {
    const targetStr = target.toISOString().slice(0, 10)
    let start = null
    for (let i = navList.length - 1; i >= 0; i--) {
      if (navList[i].date <= targetStr) { start = navList[i]; break }
    }
    if (start?.nav > 0) {
      periodResults.push({
        period: name,
        returnRate: Math.round((latest.nav - start.nav) / start.nav * 10000) / 100,
        startDate: start.date, startNav: start.nav
      })
    } else {
      periodResults.push({ period: name, returnRate: null, startDate: null, startNav: null })
    }
  }

  return { fundCode: code, latestNav: latest.nav, latestDate: latest.date, periods: periodResults }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (!args.code || args.help) { showHelp(USAGE, HELP_DESC) }
  const result = await getFundPerformance(args.code)
  console.log(JSON.stringify(result, null, 2))
}
