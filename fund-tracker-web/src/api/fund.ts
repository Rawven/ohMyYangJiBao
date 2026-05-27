import client from './client'
import type { ApiResponse, Fund, NavHistory } from '../types'

export interface PaginatedResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export async function fetchFunds(keyword?: string, type?: string, page = 1, size = 20): Promise<PaginatedResult<Fund>> {
  const params: Record<string, string | number> = { page, size }
  if (keyword) params.keyword = keyword
  if (type) params.type = type
  const res = await client.get<any, ApiResponse<PaginatedResult<Fund>>>('/funds', { params })
  return res.data
}

export async function fetchFundDetail(code: string): Promise<Fund> {
  const res = await client.get<any, ApiResponse<Fund>>(`/funds/${code}`)
  return res.data
}

export async function fetchNavHistory(code: string): Promise<NavHistory[]> {
  const res = await client.get<any, ApiResponse<NavHistory[]>>(`/funds/${code}/nav`)
  return res.data
}

export async function fetchFundTypes(): Promise<string[]> {
  const res = await client.get<any, ApiResponse<string[]>>('/funds/types')
  return res.data
}

export async function fetchFundCompanies(): Promise<string[]> {
  const res = await client.get<any, ApiResponse<string[]>>('/funds/companies')
  return res.data
}

export interface ScreenerParams {
  keyword?: string
  type?: string
  company?: string
  minNav?: number
  maxNav?: number
  minDayIncrease?: number
  maxDayIncrease?: number
  minEstablishDate?: string
  page?: number
  size?: number
}

export async function fetchFundsScreener(params: ScreenerParams): Promise<PaginatedResult<Fund>> {
  const res = await client.get<any, ApiResponse<PaginatedResult<Fund>>>('/funds/screener', { params })
  return res.data
}

export interface FundHolding {
  id: number | null
  fundCode: string
  stockName: string
  stockCode: string
  holdRatio: number | null
  changeRatio: number | null
  reportDate: string
}

export async function fetchHoldings(code: string): Promise<FundHolding[]> {
  const res = await client.get<any, ApiResponse<FundHolding[]>>(`/funds/${code}/holdings`)
  return res.data
}
