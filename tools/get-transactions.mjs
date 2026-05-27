// 交易记录 — 从本地文件读取
import { isMainModule,  parseArgs, readTransactions, showHelp } from './api.mjs'
const USAGE = 'fund txn [--page=1]'
const HELP_DESC = '交易记录'


export default async function getTransactions(page = 1, size = 20) {
  let list = readTransactions()
  list.sort((a, b) => new Date(b.transactionDate) - new Date(a.transactionDate))
  const total = list.length
  const start = (page - 1) * size
  return { total, page, size, items: list.slice(start, start + size) }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  if (args.help) { showHelp(USAGE, HELP_DESC) }
  const result = await getTransactions(Number(args.page) || 1, Number(args.size) || 20)
  console.log(JSON.stringify(result, null, 2))
}
