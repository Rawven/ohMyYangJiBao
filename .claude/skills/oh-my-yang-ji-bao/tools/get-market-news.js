// 获取今日市场新闻简报
import { api } from './api.js'

export default async function getMarketNews() {
  return api('/api/market/news')
}

if (import.meta.url === process.argv[1]) {
  const result = await getMarketNews()
  console.log(JSON.stringify(result, null, 2))
}
