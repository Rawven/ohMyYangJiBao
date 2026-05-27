// 获取基金资金流向数据
import { api } from './api.js'

export default async function getFundFlow() {
  return api('/api/market/fund-flow')
}

if (import.meta.url === process.argv[1]) {
  const result = await getFundFlow()
  console.log(JSON.stringify(result, null, 2))
}
