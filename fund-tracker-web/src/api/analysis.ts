import client from './client'
import type { ApiResponse, AnalysisData } from '../types'

export async function fetchAnalysis(): Promise<AnalysisData> {
  const res = await client.get<any, ApiResponse<AnalysisData>>('/analysis')
  return res.data
}

export async function fetchFundAnalysis(code: string): Promise<string> {
  const res = await client.get<any, ApiResponse<string>>(`/analysis/fund/${code}`)
  return res.data
}

export async function fetchFundFlowAnalysis(code: string): Promise<string> {
  const res = await client.get<any, ApiResponse<string>>(`/analysis/fund/${code}/flow`)
  return res.data
}
