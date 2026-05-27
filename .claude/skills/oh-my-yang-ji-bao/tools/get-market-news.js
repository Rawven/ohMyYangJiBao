// 市场新闻简报 — 从东方财富抓取头条新闻
import { fetchUrl } from './api.js'

export default async function getMarketNews() {
  const html = await (await fetchUrl('https://finance.eastmoney.com/', {
    headers: { 'Referer': 'https://www.eastmoney.com/' }
  })).text()

  const newsItems = []
  // 提取新闻链接
  const pattern = /<a[^>]*href="(https?:\/\/finance\.eastmoney\.com\/a\/[\d]+\.html)"[^>]*title="([^"]+)"[^>]*>/g
  let m
  while ((m = pattern.exec(html)) !== null && newsItems.length < 15) {
    const title = m[2].trim()
    const url = m[1]
    if (title && !newsItems.some(n => n.title === title)) {
      newsItems.push({ title, url, date: new Date().toISOString().slice(0, 10) })
    }
  }

  // 兜底
  if (newsItems.length === 0) {
    const altPattern = /<a[^>]*href="([^"]*)"[^>]*>([^<]{10,})<\/a>/g
    while ((m = altPattern.exec(html)) !== null && newsItems.length < 10) {
      const title = m[2].trim()
      const url = m[1].startsWith('http') ? m[1] : `https://finance.eastmoney.com${m[1]}`
      if (title.length > 5 && url.includes('eastmoney')) {
        newsItems.push({ title, url, date: new Date().toISOString().slice(0, 10) })
      }
    }
  }

  return {
    title: '今日财经头条',
    summary: `共 ${newsItems.length} 条新闻`,
    date: new Date().toISOString().slice(0, 10),
    source: '东方财富',
    newsItems
  }
}

if (import.meta.url === process.argv[1]) {
  const result = await getMarketNews()
  console.log(JSON.stringify(result, null, 2))
}
