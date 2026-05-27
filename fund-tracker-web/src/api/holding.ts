import client from './client'
import type { ApiResponse, HoldingDTO, ParsedHolding } from '../types'

export async function fetchHoldings(): Promise<HoldingDTO[]> {
  const res = await client.get<any, ApiResponse<HoldingDTO[]>>('/holdings')
  return res.data
}

export async function parsePhoto(file: File): Promise<ParsedHolding[]> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await client.post<any, ApiResponse<ParsedHolding[]>>('/portfolio/parse-photo', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}

export async function replaceHoldings(holdings: ParsedHolding[]): Promise<void> {
  await client.post('/portfolio/replace-holdings', holdings)
}
