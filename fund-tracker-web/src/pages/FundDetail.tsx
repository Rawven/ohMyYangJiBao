import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Card, Descriptions, Tag, Spin, Alert, Row, Col, Statistic, Table, Button, Typography, message } from 'antd'
import { ThunderboltOutlined } from '@ant-design/icons'
import { fetchFundDetail, fetchNavHistory, fetchHoldings } from '../api/fund'
import { fetchFundAnalysis, fetchFundFlowAnalysis } from '../api/analysis'
import NavChart from '../components/NavChart'

export default function FundDetail() {
  const { code } = useParams<{ code: string }>()
  const [aiAnalysis, setAiAnalysis] = useState<string | null>(null)
  const [aiLoading, setAiLoading] = useState(false)
  const [flowAnalysis, setFlowAnalysis] = useState<string | null>(null)
  const [flowLoading, setFlowLoading] = useState(false)

  const { data: fund, isLoading: loading1, isError: err1 } = useQuery({
    queryKey: ['fund', code],
    queryFn: () => fetchFundDetail(code!),
    enabled: !!code,
  })

  const { data: navHistory, isLoading: loading2 } = useQuery({
    queryKey: ['navHistory', code],
    queryFn: () => fetchNavHistory(code!),
    enabled: !!code,
  })

  const { data: holdings, isLoading: loading3 } = useQuery({
    queryKey: ['holdings', code],
    queryFn: () => fetchHoldings(code!),
    enabled: !!code,
    staleTime: 60000,
  })

  if (loading1 || loading2) {
    return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  }
  if (err1 || !fund) return <Alert type="error" message="基金不存在" banner />

  const holdingColumns = [
    { title: '序号', key: 'index', width: 60, render: (_: unknown, __: unknown, i: number) => i + 1 },
    { title: '股票名称', dataIndex: 'stockName', key: 'stockName' },
    {
      title: '持仓占比', dataIndex: 'holdRatio', key: 'holdRatio',
      render: (v: number) => v != null ? `${v.toFixed(2)}%` : '--',
    },
    {
      title: '涨跌幅', dataIndex: 'changeRatio', key: 'changeRatio',
      render: (v: number) => {
        if (v == null) return '--'
        const color = v >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)'
        return <span style={{ color }}>{v >= 0 ? '+' : ''}{v.toFixed(2)}%</span>
      },
    },
  ]

  return (
    <div>
      <Card title={`${fund.name}（${fund.code}）`} style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}><Statistic title="最新净值" value={fund.nav} precision={4} /></Col>
          <Col span={6}>
            <Statistic
              title="日涨跌"
              value={(fund.dayIncrease * 100).toFixed(2)}
              suffix="%"
              valueStyle={{ color: fund.dayIncrease >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}
            />
          </Col>
          <Col span={6}><Statistic title="净值日期" value={fund.navDate} /></Col>
          <Col span={6}><Statistic title="基金公司" value={fund.company || '--'} /></Col>
        </Row>
      </Card>
      <Card title="净值走势">
        {navHistory && navHistory.length > 0
          ? <NavChart data={navHistory} />
          : <Alert message="暂无净值数据" type="info" />}
      </Card>
      <Card title="股票持仓" style={{ marginTop: 16 }}
        extra={
          holdings && holdings.length > 0
            ? <div style={{ display: 'flex', gap: 8 }}>
                <Button
                  type="primary"
                  icon={<ThunderboltOutlined />}
                  loading={aiLoading}
                  onClick={async () => {
                    setAiLoading(true)
                    setAiAnalysis(null)
                    try {
                      const result = await fetchFundAnalysis(code!)
                      setAiAnalysis(result)
                    } catch (e: any) {
                      message.error(e?.message || '分析失败')
                    } finally {
                      setAiLoading(false)
                    }
                  }}
                >
                  持仓分析
                </Button>
                <Button
                  icon={<ThunderboltOutlined />}
                  loading={flowLoading}
                  onClick={async () => {
                    setFlowLoading(true)
                    setFlowAnalysis(null)
                    try {
                      const result = await fetchFundFlowAnalysis(code!)
                      setFlowAnalysis(result)
                    } catch (e: any) {
                      message.error(e?.message || '分析失败')
                    } finally {
                      setFlowLoading(false)
                    }
                  }}
                >
                  资金流向
                </Button>
              </div>
            : undefined
        }
      >
        {loading3
          ? <Spin />
          : holdings && holdings.length > 0
            ? (
              <div>
                <p style={{ marginBottom: 12, color: 'var(--ft-text-muted)' }}>
                  报告期: {holdings[0].reportDate}（数据来源: 天天基金）
                </p>
                <Table
                  columns={holdingColumns}
                  dataSource={holdings}
                  rowKey="stockName"
                  pagination={false}
                  size="small"
                />
                {aiAnalysis && (
                  <Card
                    type="inner"
                    title={<><ThunderboltOutlined /> 持仓分析报告</>}
                    style={{ marginTop: 16 }}
                  >
                    <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                      {aiAnalysis}
                    </Typography.Paragraph>
                  </Card>
                )}
                {flowAnalysis && (
                  <Card
                    type="inner"
                    title={<><ThunderboltOutlined /> 资金流向分析</>}
                    style={{ marginTop: 16 }}
                  >
                    <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                      {flowAnalysis}
                    </Typography.Paragraph>
                  </Card>
                )}
              </div>
            )
            : <Alert message="暂无持仓数据" type="info" />
        }
      </Card>
      <Card title="基本信息" style={{ marginTop: 16 }}>
        <Descriptions column={2}>
          <Descriptions.Item label="基金代码">{fund.code}</Descriptions.Item>
          <Descriptions.Item label="基金名称">{fund.name}</Descriptions.Item>
          <Descriptions.Item label="基金类型"><Tag>{fund.type}</Tag></Descriptions.Item>
          <Descriptions.Item label="成立日期">{fund.establishDate || '--'}</Descriptions.Item>
          <Descriptions.Item label="基金公司">{fund.company || '--'}</Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  )
}
