// 获取行业板块分析数据
import { api, parseArgs } from './api.js'

export default async function getIndustryAnalysis(industry) {
  const q = industry ? `?industry=${encodeURIComponent(industry)}` : ''
  return api(`/api/market/industry${q}`)
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  const result = await getIndustryAnalysis(args.industry)
  console.log(JSON.stringify(result, null, 2))
}
