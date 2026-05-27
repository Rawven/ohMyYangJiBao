import client from './client'
import type { ApiResponse, Transaction } from '../types'

export async function fetchTransactions(fundCode?: string): Promise<Transaction[]> {
  const params: Record<string, string> = {}
  if (fundCode) params.fundCode = fundCode
  const res = await client.get<any, ApiResponse<Transaction[]>>('/transactions', { params })
  return res.data
}

export async function addTransaction(data: Partial<Transaction>): Promise<Transaction> {
  const res = await client.post<any, ApiResponse<Transaction>>('/transactions', data)
  return res.data
}

export async function deleteTransaction(id: number): Promise<void> {
  await client.delete(`/transactions/${id}`)
}
