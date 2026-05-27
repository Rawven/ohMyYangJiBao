import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import FundMarket from './pages/FundMarket'
import FundDetail from './pages/FundDetail'
import FundCompare from './pages/FundCompare'
import Portfolio from './pages/Portfolio'
import Transactions from './pages/Transactions'
import Analysis from './pages/Analysis'
import MarketNews from './pages/MarketNews'
import IndustryAnalysisPage from './pages/IndustryAnalysisPage'
import FundFlow from './pages/FundFlow'
import IndexValuation from './pages/IndexValuation'
import FundScreener from './pages/FundScreener'
import AIChat from './pages/AIChat'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Layout />}>
        <Route index element={<Navigate to="/chat" replace />} />
        <Route path="chat" element={<AIChat />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="funds" element={<FundMarket />} />
        <Route path="funds/:code" element={<FundDetail />} />
        <Route path="funds/compare" element={<FundCompare />} />
        <Route path="portfolio" element={<Portfolio />} />
        <Route path="transactions" element={<Transactions />} />
        <Route path="analysis" element={<Analysis />} />
        <Route path="market/news" element={<MarketNews />} />
        <Route path="market/industry" element={<IndustryAnalysisPage />} />
        <Route path="market/fund-flow" element={<FundFlow />} />
        <Route path="market/index-valuation" element={<IndexValuation />} />
        <Route path="funds/screener" element={<FundScreener />} />
      </Route>
    </Routes>
  )
}
