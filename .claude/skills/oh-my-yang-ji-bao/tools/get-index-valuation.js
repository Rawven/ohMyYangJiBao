// 指数估值 — 从腾讯行情 API 获取（GBK 编码）
import { isMainModule,  fetchUrl } from './api.js'

// 主要指数代码
const INDEX_CODES = ['sh000001', 'sz399001', 'sz399006', 'sh000300', 'sh000016', 'sh000688', 'sh000905', 'sh399310']

export default async function getIndexValuation() {
  const url = `https://qt.gtimg.cn/q=${INDEX_CODES.join(',')}`
  const res = await fetchUrl(url, { headers: { 'Referer': 'https://gu.qq.com/' } })
  const buffer = await res.arrayBuffer()
  // GBK 解码
  const decoder = new TextDecoder('gbk')
  const text = decoder.decode(buffer)

  const lines = text.split('\n').filter(l => l.trim())
  const result = []

  for (const line of lines) {
    const m = line.match(/"(.*)"/)
    if (!m) continue
    const fields = m[1].split('~')
    if (fields.length < 46) continue

    const name = fields[1]
    const code = fields[2]
    const price = Number(fields[3]) || 0
    const changePct = Number(fields[32]) || 0
    const pe = Number(fields[39]) || 0
    const amplitude = Number(fields[43]) || 0
    const turnover = Number(fields[38]) || 0
    const high52w = Number(fields[67]) || 0
    const low52w = Number(fields[68]) || 0

    // 计算 PE 百分位（简化：根据 PE 绝对值估算）
    let pePercentile = null
    let level = '适中'
    if (pe > 0) {
      if (pe <= 15) { pePercentile = pe / 50 * 100; level = '低估' }
      else if (pe <= 25) { pePercentile = pe / 50 * 100; level = '偏低' }
      else if (pe <= 40) { pePercentile = pe / 50 * 100; level = '适中' }
      else if (pe <= 60) { pePercentile = pe / 50 * 100; level = '偏高' }
      else { pePercentile = Math.min(pe / 50 * 100, 100); level = '高估' }
    }

    result.push({
      name, code: code.replace(/^(sh|sz)/, '').toUpperCase(),
      price: Math.round(price * 100) / 100,
      changePct: Math.round(changePct * 100) / 100,
      pe: Math.round(pe * 100) / 100,
      amplitude: Math.round(amplitude * 100) / 100,
      turnover: Math.round(turnover * 100) / 100,
      high52w, low52w,
      pePercentile: pePercentile != null ? Math.round(pePercentile * 100) / 100 : null,
      level
    })
  }

  return result
}

if (isMainModule(import.meta.url)) {
  const result = await getIndexValuation()
  console.log(JSON.stringify(result, null, 2))
}
