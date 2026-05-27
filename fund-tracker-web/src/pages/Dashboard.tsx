import { useQuery } from '@tanstack/react-query'
import { Row, Col, Card, Statistic, Table, Spin, Alert } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined, WalletOutlined } from '@ant-design/icons'
import { fetchAnalysis } from '../api/analysis'
import { fetchHoldings } from '../api/holding'
import { formatMoney, formatPercent } from '../utils/format'
import type { HoldingDTO } from '../types'
import { useNavigate } from 'react-router-dom'

export default function Dashboard() {
  const navigate = useNavigate()
  const { data: analysis, isLoading: loading1, isError: err1 } = useQuery({
    queryKey: ['analysis'],
    queryFn: fetchAnalysis,
  })
  const { data: holdings, isLoading: loading2, isError: err2 } = useQuery({
    queryKey: ['holdings'],
    queryFn: fetchHoldings,
  })

  if (loading1 || loading2) {
    return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  }
  if (err1 || err2) return <Alert type="error" message="加载数据失败" banner />

  const columns = [
    { title: '基金名称', dataIndex: 'fundName', key: 'fundName' },
    {
      title: '市值', dataIndex: 'marketValue', key: 'marketValue',
      render: (v: number) => `¥${formatMoney(v)}`,
    },
    {
      title: '盈亏', dataIndex: 'profit', key: 'profit',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}>
          {v >= 0 ? '+' : ''}{formatMoney(v)}
        </span>
      ),
    },
    {
      title: '收益率', dataIndex: 'profitRate', key: 'profitRate',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}>
          {formatPercent(v)}
        </span>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="持仓总市值"
              value={analysis?.totalMarketValue || 0}
              precision={2}
              prefix={<WalletOutlined />}
              suffix="¥"
              valueStyle={{ color: 'var(--ft-blue)' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="总成本" value={analysis?.totalCost || 0} precision={2} prefix="¥" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总盈亏"
              value={analysis?.totalProfit || 0}
              precision={2}
              prefix={analysis?.totalProfit !== undefined && analysis.totalProfit >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
              valueStyle={{ color: analysis?.totalProfit !== undefined && analysis.totalProfit >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总收益率"
              value={analysis?.totalProfitRate || 0}
              precision={2}
              suffix="%"
              valueStyle={{ color: analysis?.totalProfitRate !== undefined && analysis.totalProfitRate >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}
            />
          </Card>
        </Col>
      </Row>
      <Card title="持仓概览">
        <Table
          dataSource={holdings || []}
          columns={columns}
          rowKey="id"
          pagination={false}
          onRow={(record: HoldingDTO) => ({
            onClick: () => navigate(`/funds/${record.fundCode}`),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>
    </div>
  )
}
