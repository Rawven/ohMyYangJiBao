import { useState, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, Table, Input, Select, Tag, Spin, Alert, Button } from 'antd'
import { StarOutlined, StarFilled, SwapOutlined } from '@ant-design/icons'
import { fetchFunds, fetchFundTypes } from '../api/fund'
import { useNavigate } from 'react-router-dom'
import { formatMoney } from '../utils/format'
import type { Fund } from '../types'

export default function FundMarket() {
  const navigate = useNavigate()
  const searchRef = useRef<any>(null)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [typeFilter, setTypeFilter] = useState<string>('')
  const [page, setPage] = useState(1)
  const [watchlist, setWatchlist] = useState<Set<string>>(new Set(['110011', '005827', '260108']))
  const [selectedCodes, setSelectedCodes] = useState<string[]>([])

  const doSearch = (value: string) => {
    setSearchKeyword(value.trim())
    setPage(1)
  }

  const { data, isLoading } = useQuery({
    queryKey: ['funds', searchKeyword, typeFilter, page],
    queryFn: () => fetchFunds(searchKeyword || undefined, typeFilter || undefined, page, 20),
  })

  const { data: types } = useQuery({
    queryKey: ['fundTypes'],
    queryFn: fetchFundTypes,
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (!data) return <Alert type="error" message="加载基金列表失败" banner />

  const toggleWatchlist = (code: string) => {
    setWatchlist((prev) => {
      const next = new Set(prev)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  }

  const columns = [
    {
      title: '自选',
      key: 'watch',
      width: 60,
      render: (_: unknown, record: Fund) => (
        <span onClick={(e) => { e.stopPropagation(); toggleWatchlist(record.code) }} style={{ cursor: 'pointer' }}>
          {watchlist.has(record.code) ? <StarFilled style={{ color: '#faad14' }} /> : <StarOutlined />}
        </span>
      ),
    },
    { title: '基金代码', dataIndex: 'code', key: 'code', width: 100 },
    { title: '基金名称', dataIndex: 'name', key: 'name', ellipsis: true },
    {
      title: '类型', dataIndex: 'type', key: 'type', width: 100,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: '最新净值', dataIndex: 'nav', key: 'nav', width: 100,
      render: (v: number) => v > 0 ? formatMoney(v) : '-',
    },
    {
      title: '日涨跌', dataIndex: 'dayIncrease', key: 'dayIncrease', width: 80,
      render: (v: number, record: Fund) => record.nav > 0 ? (
        <span style={{ color: v >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)', fontWeight: 'bold' }}>
          {(v * 100).toFixed(2)}%
        </span>
      ) : '-',
    },
  ]

  return (
    <Card
      title={`基金市场 (${data.total?.toLocaleString() || 0} 只)`}
      extra={
        <div style={{ display: 'flex', gap: 8 }}>
          <Input.Search
            ref={searchRef}
            placeholder="搜索基金名称/代码"
            onSearch={doSearch}
            style={{ width: 240 }}
            allowClear
          />
          <Select
            placeholder="基金类型"
            value={typeFilter || undefined}
            onChange={(v) => { setTypeFilter(v || ''); setPage(1) }}
            allowClear
            style={{ width: 130 }}
            options={(types || []).map((t) => ({ label: t, value: t }))}
          />
        </div>
      }
    >
      <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
        {selectedCodes.length >= 2 && (
          <Button
            type="primary"
            icon={<SwapOutlined />}
            onClick={() => navigate(`/funds/compare?codes=${selectedCodes.join(',')}`)}
          >
            对比选中({selectedCodes.length})
          </Button>
        )}
        {selectedCodes.length > 0 && selectedCodes.length < 2 && (
          <span style={{ color: 'var(--ft-text-muted)', fontSize: 13 }}>请选择至少 2 只基金进行对比</span>
        )}
      </div>
      <Table
        dataSource={data.items || []}
        columns={columns}
        rowKey={(record) => record.id || record.code}
        rowSelection={{
          type: 'checkbox',
          selectedRowKeys: selectedCodes.map(c => c),
          onChange: (keys: React.Key[]) => setSelectedCodes(keys.map(String)),
          getCheckboxProps: () => ({ onClick: (e: React.MouseEvent) => e.stopPropagation() }),
        }}
        onRow={(record) => ({
          onClick: () => navigate(`/funds/${record.code}`),
          style: { cursor: 'pointer' },
        })}
        pagination={{
          current: page,
          pageSize: 20,
          total: data.total || 0,
          onChange: (p) => setPage(p),
          showTotal: (total) => `共 ${total} 只基金`,
          showSizeChanger: false,
        }}
        size="small"
      />
    </Card>
  )
}
