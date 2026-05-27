// 获取用户当前基金持仓列表
import { api } from './api.js'

export default async function getPortfolio() {
  return api('/api/holdings')
}

if (import.meta.url === process.argv[1]) {
  const result = await getPortfolio()
  console.log(JSON.stringify(result, null, 2))
}
