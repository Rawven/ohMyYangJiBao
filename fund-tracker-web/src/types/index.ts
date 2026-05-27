export interface Fund {
  id: number
  code: string
  name: string
  type: string
  nav: number
  navDate: string
  dayIncrease: number
  establishDate: string
  company: string
}

export interface NavHistory {
  id: number
  fundCode: string
  nav: number
  date: string
}

export interface HoldingDTO {
  id: number
  fundCode: string
  fundName: string
  fundType: string
  shares: number
  costNav: number
  currentNav: number
  marketValue: number
  costValue: number
  profit: number
  profitRate: number
}

export interface Transaction {
  id: number
  fundCode: string
  type: 'BUY' | 'SELL'
  amount: number
  nav: number
  shares: number
  transactionDate: string
  note: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface AnalysisData {
  totalMarketValue: number
  totalCost: number
  totalProfit: number
  totalProfitRate: number
  profitTrend: ProfitPoint[]
  distribution: DistributionItem[]
}

export interface ProfitPoint {
  date: string
  totalProfit: number
  totalMarketValue: number
}

export interface DistributionItem {
  fundName: string
  value: number
  percentage: number
}

export interface FundAnalysis {
  code: string
  name: string
  analysis: string
}

export interface ParsedHolding {
  fundCode: string
  fundName: string
  shares: number
  costNav: number
}
