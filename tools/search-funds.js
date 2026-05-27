// 搜索基金 — 从天天基金 10000+ 基金列表中筛选
import { parseArgs, getFundList, isMainModule } from './api.js'

export default async function searchFunds({ keyword, type, company, page = 1, size = 20 } = {}) {
  let list = await getFundList()

  if (keyword) {
    const kw = keyword.toLowerCase()
    list = list.filter(f => f.code.includes(kw) || f.name.includes(kw))
  }
  if (type) {
    list = list.filter(f => f.type.includes(type))
  }

  const total = list.length
  const start = (page - 1) * size
  const items = list.slice(start, start + size).map(f => ({ code: f.code, name: f.name, type: f.type }))

  return { total, page, size, items }
}

const args = parseArgs()
if (isMainModule(import.meta.url)) {
  const result = await searchFunds(args)
  console.log(JSON.stringify(result, null, 2))
}
