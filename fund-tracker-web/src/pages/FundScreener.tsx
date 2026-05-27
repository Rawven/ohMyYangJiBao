import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, Table, Input, Select, InputNumber, DatePicker, Tag, Spin, Alert, Button, Space, Row, Col } from 'antd'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons'
import { fetchFundsScreener, fetchFundTypes, fetchFundCompanies } from '../api/fund'
import { useNavigate } from 'react-router-dom'
import { formatMoney } from '../utils/format'
import dayjs from 'dayjs'
import type { Fund } from '../types'

export default function FundScreener() {
  const navigate = useNavigate()
  const [page, setPage] = useState(1)

  // 筛选项状态
  const [keyword, setKeyword] = useState('')
  const [type, setType] = useState<string | undefined>()
  const [company, setCompany] = useState<string | undefined>()
  const [minNav, setMinNav] = useState<number | undefined>()
  const [maxNav, setMaxNav] = useState<number | undefined>()
  const [minDayIncrease, setMinDayIncrease] = useState<number | undefined>()
  const [maxDayIncrease, setMaxDayIncrease] = useState<number | undefined>()
  const [minEstablishDate, setMinEstablishDate] = useState<string | undefined>()

  // DatePicker 的受控值（dayjs 对象）
  const establishDateValue = minEstablishDate ? dayjs(minEstablishDate) : null

  // 用于触发搜索的版本号，改变时重新查询
  const [searchVersion, setSearchVersion] = useState(0)

  // 筛选条件是否已填写（用于判断是否展示空状态提示）
  const hasFilters = !!(keyword || type || company || minNav !== undefined || maxNav !== undefined ||
    minDayIncrease !== undefined || maxDayIncrease !== undefined || minEstablishDate)

  // 获取基金类型列表
  const { data: types } = useQuery({
    queryKey: ['fundTypes'],
    queryFn: fetchFundTypes,
  })

  // 获取基金公司列表
  const { data: companies } = useQuery({
    queryKey: ['fundCompanies'],
    queryFn: fetchFundCompanies,
  })

  // 筛选查询
  const { data, isLoading, isError } = useQuery({
    queryKey: ['fundsScreener', searchVersion],
    queryFn: () => fetchFundsScreener({
      keyword: keyword || undefined,
      type: type || undefined,
      company: company || undefined,
      minNav,
      maxNav,
      minDayIncrease,
      maxDayIncrease,
      minEstablishDate: minEstablishDate || undefined,
      page,
      size: 20,
    }),
    enabled: searchVersion > 0,
  })

  // 搜索
  const handleSearch = () => {
    setPage(1)
    setSearchVersion((v) => v + 1)
  }

  // 重置
  const handleReset = () => {
    setKeyword('')
    setType(undefined)
    setCompany(undefined)
    setMinNav(undefined)
    setMaxNav(undefined)
    setMinDayIncrease(undefined)
    setMaxDayIncrease(undefined)
    setMinEstablishDate(undefined)
    setPage(1)
    setSearchVersion(0)
  }

  const columns = [
    { title: '基金代码', dataIndex: 'code', key: 'code', width: 110 },
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
      title: '日涨跌', dataIndex: 'dayIncrease', key: 'dayIncrease', width: 90,
      render: (v: number) => (
        <span style={{ color: v >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)', fontWeight: 'bold' }}>
          {(v * 100).toFixed(2)}%
        </span>
      ),
    },
    { title: '基金公司', dataIndex: 'company', key: 'company', width: 120, ellipsis: true },
    {
      title: '成立日期', dataIndex: 'establishDate', key: 'establishDate', width: 110,
      render: (v: string) => v || '-',
    },
  ]

  return (
    <Card title="基金筛选器">
      {/* 筛选表单 */}
      <Row gutter={[16, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={8} lg={6}>
          <Input.Search
            placeholder="基金名称/代码"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={handleSearch}
            allowClear
          />
        </Col>
        <Col xs={12} sm={6} md={4} lg={3}>
          <Select
            placeholder="基金类型"
            value={type}
            onChange={(v) => setType(v)}
            allowClear
            style={{ width: '100%' }}
            options={(types || []).map((t) => ({ label: t, value: t }))}
          />
        </Col>
        <Col xs={12} sm={6} md={4} lg={3}>
          <Select
            placeholder="基金公司"
            value={company}
            onChange={(v) => setCompany(v)}
            allowClear
            style={{ width: '100%' }}
            showSearch
            filterOption={(input, option) =>
              (option?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
            }
            options={(companies || []).map((c) => ({ label: c, value: c }))}
          />
        </Col>
        <Col xs={12} sm={6} md={4} lg={3}>
          <InputNumber
            placeholder="最小净值"
            value={minNav}
            onChange={(v) => setMinNav(v ?? undefined)}
            style={{ width: '100%' }}
            min={0}
            step={0.01}
            precision={2}
          />
        </Col>
        <Col xs={12} sm={6} md={4} lg={3}>
          <InputNumber
            placeholder="最大净值"
            value={maxNav}
            onChange={(v) => setMaxNav(v ?? undefined)}
            style={{ width: '100%' }}
            min={0}
            step={0.01}
            precision={2}
          />
        </Col>
        <Col xs={12} sm={6} md={4} lg={3}>
          <InputNumber
            placeholder="最小日涨跌"
            value={minDayIncrease}
            onChange={(v) => setMinDayIncrease(v ?? undefined)}
            style={{ width: '100%' }}
            step={0.01}
            precision={4}
          />
        </Col>
        <Col xs={12} sm={6} md={4} lg={3}>
          <InputNumber
            placeholder="最大日涨跌"
            value={maxDayIncrease}
            onChange={(v) => setMaxDayIncrease(v ?? undefined)}
            style={{ width: '100%' }}
            step={0.01}
            precision={4}
          />
        </Col>
        <Col xs={12} sm={6} md={4} lg={3}>
          <DatePicker
            placeholder="成立日期起"
            value={establishDateValue}
            onChange={(_date, dateString) => setMinEstablishDate(dateString as string || undefined)}
            style={{ width: '100%' }}
          />
        </Col>
        <Col xs={24} sm={12} md={8} lg={6}>
          <Space>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              搜索
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </Space>
        </Col>
      </Row>

      {/* 加载状态 */}
      {isLoading && <Spin size="large" style={{ display: 'block', marginTop: 40, marginBottom: 40 }} />}

      {/* 错误状态 */}
      {isError && <Alert type="error" message="筛选查询失败，请稍后重试" banner />}

      {/* 未搜索状态 */}
      {searchVersion === 0 && !isLoading && (
        <Alert type="info" message="请设置筛选条件后点击搜索" banner />
      )}

      {/* 空结果状态 */}
      {searchVersion > 0 && !isLoading && !isError && data && data.items?.length === 0 && (
        <Alert type="warning" message="未找到匹配的基金，请调整筛选条件" banner style={{ marginBottom: 16 }} />
      )}

      {/* 搜索结果表格 */}
      {searchVersion > 0 && data && data.items && data.items.length > 0 && (
        <Table
          dataSource={data.items}
          columns={columns}
          rowKey={(record) => record.id || record.code}
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
      )}
    </Card>
  )
}
