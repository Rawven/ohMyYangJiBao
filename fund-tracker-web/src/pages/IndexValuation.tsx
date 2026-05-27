import { useQuery } from '@tanstack/react-query'
import { Card, Table, Tag, Spin, Alert, Typography, Progress } from 'antd'
import { fetchIndexValuation } from '../api/market'
import type { IndexValuation } from '../api/market'
import { formatMoney, formatPercent } from '../utils/format'

const { Paragraph, Text } = Typography

const levelColorMap: Record<string, string> = {
  '低估': 'green',
  '偏低': 'lime',
  '适中': 'blue',
  '偏高': 'orange',
  '高估': 'red',
}

function getPercentileColor(value: number): string {
  if (value <= 20) return 'var(--ft-accent)'
  if (value <= 40) return 'var(--ft-accent-hover)'
  if (value <= 60) return 'var(--ft-blue)'
  if (value <= 80) return 'var(--ft-yellow)'
  return 'var(--ft-red)'
}

export default function IndexValuation() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['index-valuation'],
    queryFn: fetchIndexValuation,
    retry: 2,
    retryDelay: 5000,
  })

  if (isLoading) {
    return (
      <Card>
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <Spin size="large" />
          <div style={{ marginTop: 24, color: 'var(--ft-text-muted)' }}>正在加载指数估值数据...</div>
        </div>
      </Card>
    )
  }

  if (isError) {
    return <Alert type="error" message="加载指数估值数据失败" banner />
  }

  if (!data || data.length === 0) {
    return (
      <Card>
        <Alert type="info" message="暂无指数估值数据" banner />
      </Card>
    )
  }

  const columns = [
    {
      title: '指数名称',
      dataIndex: 'name' as const,
      key: 'name',
      width: 120,
    },
    {
      title: '最新点数',
      dataIndex: 'price' as const,
      key: 'price',
      width: 110,
      render: (value: number) => (
        <Text strong>{formatMoney(value)}</Text>
      ),
    },
    {
      title: '涨跌幅',
      dataIndex: 'changePct' as const,
      key: 'changePct',
      width: 90,
      render: (value: number) => (
        <Text
          strong
          style={{ color: value >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}
        >
          {formatPercent(value)}
        </Text>
      ),
    },
    {
      title: '市盈率(PE)',
      dataIndex: 'pe' as const,
      key: 'pe',
      width: 110,
      render: (value: number | null) =>
        value != null ? (
          <Text>{value.toFixed(2)}</Text>
        ) : (
          <Text type="secondary">--</Text>
        ),
    },
    {
      title: '估值水平',
      dataIndex: 'level' as const,
      key: 'level',
      width: 100,
      render: (value: string) => (
        <Tag color={levelColorMap[value] || 'default'}>{value}</Tag>
      ),
    },
    {
      title: '52周高/低',
      key: 'range52w',
      width: 150,
      render: (_: unknown, record: IndexValuation) => (
        <Text>
          {record.low52w.toFixed(2)} - {record.high52w.toFixed(2)}
        </Text>
      ),
    },
    {
      title: 'PE百分位',
      dataIndex: 'pePercentile' as const,
      key: 'pePercentile',
      width: 160,
      render: (value: number) => (
        <Progress
          percent={Math.round(value)}
          size="small"
          strokeColor={getPercentileColor(value)}
          format={(pct) => `${pct}%`}
        />
      ),
    },
    {
      title: '振幅',
      dataIndex: 'amplitude' as const,
      key: 'amplitude',
      width: 80,
      render: (value: number) => `${value.toFixed(2)}%`,
    },
    {
      title: '换手率',
      dataIndex: 'turnover' as const,
      key: 'turnover',
      width: 80,
      render: (value: number) => `${value.toFixed(2)}%`,
    },
  ]

  return (
    <Card title="指数估值" style={{ marginBottom: 16 }}>
      <div style={{ marginBottom: 16, color: 'var(--ft-text-muted)', fontSize: 13 }}>
        基于腾讯财经数据 | PE百分位 = 当前PE在52周区间中的位置
      </div>
      <Table<IndexValuation>
        columns={columns}
        dataSource={data}
        rowKey="code"
        pagination={false}
        size="small"
      />
    </Card>
  )
}
