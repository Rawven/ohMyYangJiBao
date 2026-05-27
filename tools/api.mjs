// 共享工具 — 直接爬取天天基金/东方财富/腾讯公开数据，零依赖
import { existsSync, mkdirSync, readFileSync, writeFileSync, statSync } from 'fs'
import { homedir } from 'os'

export const DATA_DIR = `${homedir()}/.ohmyyangjibao`
if (!existsSync(DATA_DIR)) mkdirSync(DATA_DIR, { recursive: true })

// ---------- HTTP 请求 ----------
export async function fetchUrl(url, opts = {}) {
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
      'Referer': 'https://fund.eastmoney.com/',
      ...opts.headers
    },
    ...opts
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res
}

// ---------- JSONP 解析 ----------
export function stripJsonp(text) {
  // 去掉 jsonpgz() / var xxx = 等前缀，找到第一个 { 或 [
  const idx = text.search(/[{[]/)
  if (idx === -1) return text.trim()
  let s = text.slice(idx).trim()
  // 去掉末尾的 );，保留纯 JSON
  s = s.replace(/\)\s*;?\s*$/, '')
  return s
}

// ---------- 基金代码列表（天天基金）----------
let _fundList = null

export async function getFundList(force = false) {
  if (_fundList && !force) return _fundList
  const cacheFile = `${DATA_DIR}/fund-list.json`
  if (!force && existsSync(cacheFile) && Date.now() - statSync(cacheFile).mtimeMs < 3600000) {
    _fundList = JSON.parse(readFileSync(cacheFile, 'utf-8'))
    return _fundList
  }
  const text = await (await fetchUrl('https://fund.eastmoney.com/js/fundcode_search.js')).text()
  const start = text.indexOf('[')
  const end = text.lastIndexOf(']')
  if (start === -1 || end === -1) throw new Error('解析基金列表失败')
  const raw = JSON.parse(text.slice(start, end + 1))
  // 格式: [code, py, name, type, py]
  _fundList = raw.map(r => ({ code: String(r[0]), name: r[2], type: r[3] }))
  writeFileSync(cacheFile, JSON.stringify(_fundList))
  return _fundList
}

// ---------- 实时净值（JSONP）----------
export async function getRealtimeNav(code) {
  const text = await (await fetchUrl(`https://fundgz.1234567.com.cn/js/${code}.js`)).text()
  const json = JSON.parse(stripJsonp(text))
  return {
    code: json.fundcode,
    name: json.name,
    nav: Number(json.dwjz),
    estimatedNav: Number(json.gsz),
    estimatedChange: Number(json.gszzl),
    navDate: json.jzrq,
    estimateDate: json.gztime?.slice(0, 10)
  }
}

// ---------- 历史净值（东方财富 API）----------
export async function getNavHistoryFromApi(code, days = 365) {
  const allData = []
  // 该 API 强制每页 20 条，不受 pageSize 参数影响
  const pageSize = 20
  const totalPages = Math.ceil(days / pageSize)

  for (let page = 1; page <= totalPages; page++) {
    const url = `https://api.fund.eastmoney.com/f10/lsjz?fundCode=${code}&pageIndex=${page}&pageSize=50`
    const text = await (await fetchUrl(url, {
      headers: { 'Referer': `https://fundf10.eastmoney.com/jbgk_${code}.html` }
    })).text()
    const json = JSON.parse(stripJsonp(text))
    const list = json?.Data?.LSJZList
    if (!list?.length) break
    for (const item of list) {
      allData.push({ date: item.FSRQ, nav: Number(item.DWJZ) })
    }
    // 不足一页说明到末尾了
    if (list.length < pageSize) break
  }

  return allData.sort((a, b) => a.date.localeCompare(b.date))
}

// ---------- HTML 解析辅助 ----------
export function extractByPattern(html, regex, group = 1) {
  const m = html.match(regex)
  return m ? m[group].trim() : null
}

export function extractAllByPattern(html, regex, group = 1) {
  const results = []
  let m
  while ((m = regex.exec(html)) !== null) {
    results.push(m[group].trim())
  }
  return results
}

// ---------- 本地持仓 ----------
const HOLDINGS_FILE = `${DATA_DIR}/holdings.json`
const TXN_FILE = `${DATA_DIR}/transactions.json`

export function readHoldings() {
  try { return JSON.parse(readFileSync(HOLDINGS_FILE, 'utf-8')) }
  catch { return [] }
}

export function writeHoldings(data) {
  writeFileSync(HOLDINGS_FILE, JSON.stringify(data, null, 2))
}

export function readTransactions() {
  try { return JSON.parse(readFileSync(TXN_FILE, 'utf-8')) }
  catch { return [] }
}

export function writeTransactions(data) {
  writeFileSync(TXN_FILE, JSON.stringify(data, null, 2))
}

// ---------- 格式化 ----------
export function pct(v, d = 2) {
  return v != null ? (v >= 0 ? '+' : '') + Number(v).toFixed(d) + '%' : '--'
}

export function money(v) {
  return v != null ? '¥' + Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) : '--'
}

// ---------- 命令行参数 ----------
export function parseArgs() {
  const args = {}
  const raw = process.argv.slice(2)
  if (raw.includes('--help') || raw.includes('-h')) args.help = true
  for (const arg of raw) {
    const [k, v] = arg.replace(/^--/, '').split('=')
    args[k] = v ?? true
  }
  return args
}

export function showHelp(usage, description) {
  console.log(`用法: ${usage}\n`)
  if (description) console.log(`${description}\n`)
  process.exit(0)
}

// 检测是否被直接运行（兼容 Bun 和 Node）
import { resolve } from 'path'
export function isMainModule(metaUrl) {
  if (typeof process === 'undefined' || !process.argv[1]) return false
  const absPath = resolve(process.argv[1])
  const fileUrl = `file://${encodeURI(absPath)}`
  return metaUrl === absPath || metaUrl === fileUrl
}
