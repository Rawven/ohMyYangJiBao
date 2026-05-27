import client from './client'
import type { ApiResponse } from '../types'

export interface NewsItem {
  title: string
  url: string
  date: string
  summary: string
}

export interface NewsBriefing {
  title: string
  summary: string
  newsItems: NewsItem[]
  date: string
  source: string
}

export interface IndustryItem {
  industryName: string
  totalRatio: number
  stockCount: number
  trend: string
}

export interface IndustryAnalysis {
  analysis: string
  industries: IndustryItem[]
  date: string
}

export async function fetchNews(): Promise<NewsBriefing> {
  const res = await client.get<any, ApiResponse<NewsBriefing>>('/market/news')
  return res.data
}

export async function fetchIndustryAnalysis(): Promise<IndustryAnalysis> {
  const res = await client.get<any, ApiResponse<IndustryAnalysis>>('/market/industry')
  return res.data
}

export interface FundFlowItem {
  fundCode: string
  fundName: string
  fundType: string
  institutionRatio: number | null
  personalRatio: number | null
  netSubscribe: number | null
  scaleChangeRate: string | null
}

export async function fetchFundFlow(): Promise<FundFlowItem[]> {
  const res = await client.get<any, ApiResponse<FundFlowItem[]>>('/market/fund-flow')
  return res.data
}

export interface IndexValuation {
  name: string
  code: string
  price: number
  changePct: number
  pe: number | null
  amplitude: number
  turnover: number
  high52w: number
  low52w: number
  pePercentile: number
  level: string
}

export async function fetchIndexValuation(): Promise<IndexValuation[]> {
  const res = await client.get<any, ApiResponse<IndexValuation[]>>('/market/index-valuation')
  return res.data
}
