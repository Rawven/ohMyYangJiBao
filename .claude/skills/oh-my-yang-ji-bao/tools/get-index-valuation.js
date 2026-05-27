// 获取主要指数估值数据（PE/PB 百分位）
import { api } from './api.js'

export default async function getIndexValuation() {
  return api('/api/market/index-valuation')
}

if (import.meta.url === process.argv[1]) {
  const result = await getIndexValuation()
  console.log(JSON.stringify(result, null, 2))
}
