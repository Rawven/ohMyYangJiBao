import { useQuery } from '@tanstack/react-query'
import { Card, Table, Tag, Spin, Alert, Typography } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined, MinusOutlined } from '@ant-design/icons'
import { fetchFundFlow } from '../api/market'
import type { FundFlowItem } from '../api/market'

const { Text } = Typography

export default function FundFlow() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['fund-flow'],
    queryFn: fetchFundFlow,
    retry: 2,
    retryDelay: 5000,
  })

  if (isLoading) {
    return (
      <Card>
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <Spin size="large" />
          <div style={{ marginTop: 24, color: 'var(--ft-text-muted)' }}>正在加载资金流向数据...</div>
        </div>
      </Card>
    )
  }
  if (isError) return <Alert type="error" message="加载资金流向数据失败" banner />

  const columns = [
    {
      title: '基金代码', dataIndex: 'fundCode' as const, key: 'fundCode',
      width: 100,
    },
    {
      title: '基金名称', dataIndex: 'fundName' as const, key: 'fundName',
      ellipsis: true,
    },
    {
      title: '机构持有',
      key: 'institution',
      render: (_: unknown, r: FundFlowItem) => {
        if (r.institutionRatio == null) return <Text type="secondary">--</Text>
        const v = Number(r.institutionRatio)
        return (
          <span style={{ color: v > 50 ? 'var(--ft-blue)' : 'var(--ft-text-muted)', fontWeight: v > 50 ? 'bold' : 'normal' }}>
            {v.toFixed(2)}%
          </span>
        )
      },
      sorter: (a: FundFlowItem, b: FundFlowItem) => (b.institutionRatio ?? 0) - (a.institutionRatio ?? 0),
    },
    {
      title: '个人持有',
      key: 'personal',
      render: (_: unknown, r: FundFlowItem) => {
        if (r.personalRatio == null) return <Text type="secondary">--</Text>
        return <span>{Number(r.personalRatio).toFixed(2)}%</span>
      },
    },
    {
      title: '净申购(亿份)',
      key: 'netSubscribe',
      render: (_: unknown, r: FundFlowItem) => {
        if (r.netSubscribe == null) return <Text type="secondary">--</Text>
        const v = Number(r.netSubscribe)
        const isInflow = v > 0
        return (
          <span style={{ color: isInflow ? 'var(--ft-red)' : 'var(--ft-accent)', fontWeight: 'bold' }}>
            {isInflow ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
            {Math.abs(v).toFixed(2)}
          </span>
        )
      },
      sorter: (a: FundFlowItem, b: FundFlowItem) => (b.netSubscribe ?? 0) - (a.netSubscribe ?? 0),
    },
    {
      title: '规模变动',
      key: 'scaleChange',
      render: (_: unknown, r: FundFlowItem) => {
        if (!r.scaleChangeRate) return <Text type="secondary">--</Text>
        const isPositive = !r.scaleChangeRate.startsWith('-')
        return (
          <Tag color={isPositive ? 'red' : 'green'}>
            {isPositive ? '+' : ''}{r.scaleChangeRate}
          </Tag>
        )
      },
    },
    {
      title: '资金流向',
      key: 'flowDirection',
      render: (_: unknown, r: FundFlowItem) => {
        const inst = r.institutionRatio ?? 0
        const net = r.netSubscribe ?? 0
        if (inst > 60 && net > 0) return <Tag color="red"><ArrowUpOutlined /> 主力流入</Tag>
        if (inst > 60 && net < 0) return <Tag color="orange"><ArrowDownOutlined /> 主力流出</Tag>
        if (inst < 40 && net > 0) return <Tag color="blue"><ArrowUpOutlined /> 散户流入</Tag>
        if (inst < 40 && net < 0) return <Tag color="green"><ArrowDownOutlined /> 散户流出</Tag>
        return <Tag><MinusOutlined /> 平稳</Tag>
      },
    },
  ]

  return (
    <Card title="资金流向" style={{ marginBottom: 16 }}>
      <div style={{ marginBottom: 16, color: 'var(--ft-text-muted)', fontSize: 13 }}>
        基于最近一期季报数据 | 净申购 = 期间申购 - 期间赎回 | 资金流向前 50 只基金
      </div>
      <Table<FundFlowItem>
        columns={columns}
        dataSource={data || []}
        rowKey="fundCode"
        pagination={false}
        size="small"
      />
    </Card>
  )
}
