import client from './client'
import type { ApiResponse } from '../types'

export interface SyncResult {
  totalFunds: number
  listInserted: number
  listFail: boolean
  navSuccess: number
  navFail: number
  historySuccess: number
  historyFail: number
}

export async function triggerSync(): Promise<SyncResult> {
  const res = await client.post<any, ApiResponse<SyncResult>>('/sync')
  return res.data
}
