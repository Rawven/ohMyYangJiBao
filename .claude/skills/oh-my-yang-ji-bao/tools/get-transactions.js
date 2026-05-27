// 获取交易记录
import { api, parseArgs } from './api.js'

export default async function getTransactions(page = 1, size = 20) {
  return api(`/api/transactions?page=${page}&size=${size}`)
}

const args = parseArgs()
if (import.meta.url === process.argv[1]) {
  const result = await getTransactions(args.page, args.size)
  console.log(JSON.stringify(result, null, 2))
}
