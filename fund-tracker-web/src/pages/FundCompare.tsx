import { useSearchParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Card, Table, Tag, Spin, Alert, Button, Descriptions, Row, Col } from 'antd'
import { ArrowLeftOutlined, ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons'
import client from '../api/client'
import { formatMoney } from '../utils/format'
import type { ApiResponse, NavHistory } from '../types'
import ReactECharts from 'echarts-for-react'

interface CompareItem {
  fundCode: string
  fundName: string
  fundType: string
  nav: number
  navDate: string
  dayIncrease: number
  company: string
  topHoldings: string[]
}

export default function FundCompare() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const codes = searchParams.get('codes') || ''

  const { data, isLoading, isError } = useQuery({
    queryKey: ['fund-compare', codes],
    queryFn: async () => {
      const res = await client.get<any, ApiResponse<CompareItem[]>>(`/funds/compare?codes=${codes}`)
      return res.data
    },
    enabled: codes.length > 0,
  })

  const { data: navData, isLoading: navLoading } = useQuery({
    queryKey: ['fund-compare-nav', codes],
    queryFn: async () => {
      const res = await client.get<any, ApiResponse<Record<string, NavHistory[]>>>(`/funds/compare-nav?codes=${codes}`)
      return res.data
    },
    enabled: codes.length > 0,
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (isError || !data || data.length === 0) {
    return <Alert type="error" message="加载对比数据失败" banner />
  }

  // 转置表格：指标为行，基金为列
  const metrics = [
    { label: '基金代码', render: (i: CompareItem) => i.fundCode },
    { label: '基金类型', render: (i: CompareItem) => <Tag>{i.fundType}</Tag> },
    { label: '最新净值', render: (i: CompareItem) => i.nav > 0 ? formatMoney(i.nav) : '-' },
    { label: '日涨跌', render: (i: CompareItem) => {
      if (i.nav <= 0) return '-'
      const v = i.dayIncrease * 100
      return (
        <span style={{ color: v >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)', fontWeight: 'bold' }}>
          {v >= 0 ? '+' : ''}{v.toFixed(2)}%
        </span>
      )
    }},
    { label: '净值日期', render: (i: CompareItem) => i.navDate || '-' },
    { label: '基金公司', render: (i: CompareItem) => i.company || '--' },
    { label: '前三大持仓', render: (i: CompareItem) => (
      <div>{i.topHoldings?.map((h, idx) => <div key={idx}>{h}</div>) || '--'}</div>
    )},
  ]

  const columns = [
    { title: '指标', dataIndex: 'label' as const, key: 'label', width: 100, fixed: 'left' as const },
    ...data.map((item) => ({
      title: `${item.fundName}(${item.fundCode})`,
      key: item.fundCode,
      render: (_: unknown, record: { label: string; render: (i: CompareItem) => any }) => record.render(item),
    })),
  ]

  const rows = metrics.map((m) => ({
    label: m.label,
    ...Object.fromEntries(data.map((i) => [i.fundCode, i])),
    render: m.render,
  }))

  // 净值走势对比
  const fundNameMap = new Map((data || []).map(d => [d.fundCode, d.fundName]))
  const allDates = navData
    ? [...new Set(Object.values(navData).flatMap(r => r.map(n => n.date)))].sort()
    : []
  const chartOption = navData && allDates.length > 0
    ? {
        tooltip: {
          trigger: 'axis',
          formatter: (params: any[]) => {
            const date = params[0]?.axisValue || ''
            let html = `<div style="font-weight:bold;margin-bottom:4px">${date}</div>`
            params.forEach(p => {
              if (p.value !== null && p.value !== undefined) {
                html += `<div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};margin-right:6px"></span>${p.seriesName}: ${Number(p.value).toFixed(4)}</div>`
              }
            })
            return html
          },
        },
        legend: {
          data: Object.keys(navData).map(code => fundNameMap.get(code) || code),
        },
        grid: { left: 60, right: 20, top: 40, bottom: 40 },
        xAxis: {
          type: 'category',
          data: allDates,
          axisLabel: { rotate: 45, fontSize: 11 },
        },
        yAxis: {
          type: 'value',
          name: '单位净值',
        },
        series: Object.entries(navData).map(([code, records]) => {
          const dateMap = new Map(records.map(r => [r.date, r.nav]))
          return {
            name: fundNameMap.get(code) || code,
            type: 'line',
            data: allDates.map(d => (dateMap.has(d) ? dateMap.get(d) : null)),
            smooth: true,
            lineStyle: { width: 2 },
            connectNulls: false,
          }
        }),
      }
    : null

  return (
    <div>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/funds')}
        style={{ marginBottom: 16 }}
      >
        返回基金列表
      </Button>
      {navLoading && (
        <div style={{ textAlign: 'center', marginBottom: 16, color: 'var(--ft-text-muted)' }}>
          <Spin size="small" /> <span style={{ marginLeft: 8 }}>加载净值走势中...</span>
        </div>
      )}
      {chartOption && (
        <Card title="净值走势对比" style={{ marginBottom: 16 }}>
          <ReactECharts option={chartOption} style={{ height: 400 }} />
        </Card>
      )}
      <Card title="基金对比">
        <Table
          columns={columns}
          dataSource={metrics}
          rowKey="label"
          pagination={false}
          bordered
          size="small"
        />
      </Card>
      <Row gutter={16} style={{ marginTop: 16 }}>
        {data.map((item) => (
          <Col span={8} key={item.fundCode}>
            <Card
              size="small"
              title={item.fundName}
              extra={<a onClick={() => navigate(`/funds/${item.fundCode}`)}>详情</a>}
            >
              <Descriptions column={1} size="small">
                <Descriptions.Item label="净值">{item.nav > 0 ? formatMoney(item.nav) : '-'}</Descriptions.Item>
                <Descriptions.Item label="日涨跌">
                  {item.nav > 0 ? (
                    <span style={{ color: item.dayIncrease >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}>
                      {(item.dayIncrease * 100).toFixed(2)}%
                    </span>
                  ) : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="类型">{item.fundType}</Descriptions.Item>
                <Descriptions.Item label="公司">{item.company || '--'}</Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  )
}
