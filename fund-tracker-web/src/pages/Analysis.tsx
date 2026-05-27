import { useQuery } from '@tanstack/react-query'
import { Card, Row, Col, Statistic, Spin, Alert } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons'
import { fetchAnalysis } from '../api/analysis'
import ProfitChart from '../components/ProfitChart'
import DistributionChart from '../components/DistributionChart'

export default function Analysis() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analysis'],
    queryFn: fetchAnalysis,
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (isError) return <Alert type="error" message="加载分析数据失败" banner />

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic title="持仓总市值" value={data?.totalMarketValue || 0} precision={2} prefix="¥" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="总成本" value={data?.totalCost || 0} precision={2} prefix="¥" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总盈亏"
              value={data?.totalProfit || 0}
              precision={2}
              prefix={data?.totalProfit !== undefined && data.totalProfit >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
              valueStyle={{ color: data?.totalProfit !== undefined && data.totalProfit >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总收益率"
              value={data?.totalProfitRate || 0}
              precision={2}
              suffix="%"
              valueStyle={{ color: data?.totalProfitRate !== undefined && data.totalProfitRate >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}
            />
          </Card>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={14}>
          <Card title="收益趋势">
            {data?.profitTrend ? <ProfitChart data={data.profitTrend} /> : <Alert message="暂无数据" type="info" />}
          </Card>
        </Col>
        <Col span={10}>
          <Card title="持仓分布">
            {data?.distribution ? <DistributionChart data={data.distribution} /> : <Alert message="暂无数据" type="info" />}
          </Card>
        </Col>
      </Row>
    </div>
  )
}
