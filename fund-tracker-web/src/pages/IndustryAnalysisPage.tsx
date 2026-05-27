import { useQuery } from '@tanstack/react-query'
import { Card, Table, Tag, Spin, Alert, Typography, Button } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { fetchIndustryAnalysis } from '../api/market'
import type { IndustryItem } from '../api/market'

const { Paragraph, Text } = Typography

export default function IndustryAnalysisPage() {
  const [manualRetry, setManualRetry] = useState(0)
  const { data, isLoading, isError, refetch, isRefetching } = useQuery({
    queryKey: ['industry-analysis', manualRetry],
    queryFn: fetchIndustryAnalysis,
    retry: 3,
    retryDelay: 10000,
  })

  if (isLoading) {
    return (
      <Card>
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <Spin size="large" />
          <Paragraph style={{ marginTop: 24, color: 'var(--ft-text-muted)' }}>
            正在爬取基金持仓数据并分析行业分布，预计需要 15-30 秒...
          </Paragraph>
        </div>
      </Card>
    )
  }

  if (isError) {
    return (
      <Card>
        <Alert type="error" message="加载行业分析失败" banner />
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Button icon={<ReloadOutlined />} onClick={() => { setManualRetry((n) => n + 1); refetch() }}>
            重试
          </Button>
        </div>
      </Card>
    )
  }

  // 数据还没准备好（后台仍在爬取）
  if (!data?.industries || data.industries.length === 0) {
    return (
      <Card>
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <Spin size="large" tip="数据准备中..." />
          <Paragraph style={{ marginTop: 24, color: 'var(--ft-text-muted)' }}>
            首次使用需要抓取基金持仓数据，请稍候...
          </Paragraph>
          <Button
            icon={<ReloadOutlined spin={isRefetching} />}
            onClick={() => { setManualRetry((n) => n + 1); refetch() }}
            loading={isRefetching}
            style={{ marginTop: 12 }}
          >
            检查状态
          </Button>
        </div>
      </Card>
    )
  }

  const columns = [
    {
      title: '行业名称',
      dataIndex: 'industryName' as const,
      key: 'industryName',
    },
    {
      title: '配置占比',
      dataIndex: 'totalRatio' as const,
      key: 'totalRatio',
      render: (value: number) => `${value.toFixed(2)}%`,
    },
    {
      title: '股票数量',
      dataIndex: 'stockCount' as const,
      key: 'stockCount',
    },
    {
      title: '趋势',
      dataIndex: 'trend' as const,
      key: 'trend',
      render: (value: string) => {
        const colorMap: Record<string, string> = {
          up: 'red',
          down: 'green',
          stable: 'blue',
        }
        const labelMap: Record<string, string> = {
          up: '上涨',
          down: '下跌',
          stable: '平稳',
        }
        return <Tag color={colorMap[value] || 'blue'}>{labelMap[value] || value}</Tag>
      },
    },
  ]

  return (
    <div>
      {data?.analysis && (
        <Card title="行业分析" style={{ marginBottom: 16 }}>
          <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
            {data.analysis}
          </Paragraph>
        </Card>
      )}
      <Card title="行业配置分布">
        <Table<IndustryItem>
          columns={columns}
          dataSource={data?.industries || []}
          rowKey="industryName"
          pagination={false}
        />
      </Card>
    </div>
  )
}
