import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Card, Table, Spin, Alert, Row, Col, Button, Modal, message as antMessage, Tag } from 'antd'
import { MessageOutlined, CameraOutlined } from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import { fetchHoldings, parsePhoto, replaceHoldings } from '../api/holding'
import { formatMoney, formatPercent } from '../utils/format'
import { useNavigate } from 'react-router-dom'
import type { HoldingDTO, ParsedHolding } from '../types'
import { useState, useRef } from 'react'

export default function Portfolio() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [parsedHoldings, setParsedHoldings] = useState<ParsedHolding[]>([])
  const [parsing, setParsing] = useState(false)
  const [replacing, setReplacing] = useState(false)

  const { data: holdings, isLoading, isError } = useQuery({
    queryKey: ['holdings'],
    queryFn: fetchHoldings,
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (isError) return <Alert type="error" message="加载持仓失败" banner />

  const columns = [
    { title: '基金名称', dataIndex: 'fundName', key: 'fundName' },
    { title: '持有份额', dataIndex: 'shares', key: 'shares', render: (v: number) => v.toLocaleString() },
    { title: '成本净值', dataIndex: 'costNav', key: 'costNav', render: (v: number) => formatMoney(v) },
    { title: '当前净值', dataIndex: 'currentNav', key: 'currentNav', render: (v: number) => formatMoney(v) },
    { title: '市值', dataIndex: 'marketValue', key: 'marketValue', render: (v: number) => `¥${formatMoney(v)}` },
    { title: '成本金额', dataIndex: 'costValue', key: 'costValue', render: (v: number) => `¥${formatMoney(v)}` },
    {
      title: '盈亏', dataIndex: 'profit', key: 'profit',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)', fontWeight: 'bold' }}>
          {v >= 0 ? '+' : ''}¥{formatMoney(v)}
        </span>
      ),
    },
    {
      title: '收益率', dataIndex: 'profitRate', key: 'profitRate',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)', fontWeight: 'bold' }}>
          {formatPercent(v)}
        </span>
      ),
    },
  ]

  // 按基金类型汇总市值
  const typeData = (holdings || []).reduce<Record<string, number>>((acc, h) => {
    const t = h.fundType || '未知'
    acc[t] = (acc[t] || 0) + h.marketValue
    return acc
  }, {})
  const pieOption = {
    tooltip: { trigger: 'item' as const, formatter: '{b}: ¥{c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' as const },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      label: { formatter: '{b}\n{d}%' },
      data: Object.entries(typeData)
        .filter(([_, v]) => v > 0)
        .map(([k, v]) => ({ name: k, value: Math.round(v) })),
    }],
  }

  const fundPieOption = {
    tooltip: { trigger: 'item' as const, formatter: '{b}: ¥{c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' as const },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      label: { formatter: '{b}\n{d}%' },
      data: (holdings || [])
        .filter(h => h.marketValue > 0)
        .map(h => ({ name: h.fundName, value: Math.round(h.marketValue) })),
    }],
  }

  const totalProfit = (holdings || []).reduce((sum, h) => sum + h.profit, 0)
  const totalValue = (holdings || []).reduce((sum, h) => sum + h.marketValue, 0)

  // 上传截图解析持仓
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setParsing(true)
    try {
      const result = await parsePhoto(file)
      setParsedHoldings(result)
      setModalOpen(true)
    } catch (err: any) {
      antMessage.error(err?.response?.data?.message || '解析截图失败')
    } finally {
      setParsing(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  // 确认替换持仓
  const handleReplace = async () => {
    setReplacing(true)
    try {
      await replaceHoldings(parsedHoldings)
      antMessage.success('持仓已替换成功')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['holdings'] })
    } catch (err: any) {
      antMessage.error(err?.response?.data?.message || '替换持仓失败')
    } finally {
      setReplacing(false)
    }
  }

  const parsedColumns = [
    { title: '基金代码', dataIndex: 'fundCode', key: 'fundCode', width: 100 },
    { title: '基金名称', dataIndex: 'fundName', key: 'fundName', ellipsis: true },
    { title: '持有份额', dataIndex: 'shares', key: 'shares', render: (v: number) => v.toLocaleString() },
    { title: '成本净值', dataIndex: 'costNav', key: 'costNav', render: (v: number) => formatMoney(v) },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div>
          <span style={{ fontSize: 20, fontWeight: 600, color: 'var(--ft-text-primary)' }}>我的持仓</span>
          <span style={{ marginLeft: 16, fontSize: 14, color: 'var(--ft-text-muted)' }}>
            总市值 ¥{formatMoney(totalValue)}
          </span>
          <span style={{ marginLeft: 12, fontSize: 14, fontWeight: 500, color: totalProfit >= 0 ? 'var(--ft-red)' : 'var(--ft-accent)' }}>
            {totalProfit >= 0 ? '+' : ''}¥{formatMoney(totalProfit)}
          </span>
        </div>
        <input
          type="file"
          ref={fileInputRef}
          accept="image/*"
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
        <Button
          icon={<CameraOutlined />}
          onClick={() => fileInputRef.current?.click()}
          loading={parsing}
          style={{ marginRight: 8 }}
        >
          上传截图
        </Button>
        <Button
          type="primary"
          icon={<MessageOutlined />}
          onClick={() => navigate('/chat?q=帮我分析我的基金持仓，看看每只基金的盈亏和风险，给出建议')}
          style={{ background: 'var(--ft-accent)', borderColor: 'var(--ft-accent)' }}
        >
          AI 分析持仓
        </Button>
      </div>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <Card title="持仓分布(按基金类型)">
            <ReactECharts option={pieOption} style={{ height: 320 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card title="持仓分布(按基金)">
            <ReactECharts option={fundPieOption} style={{ height: 320 }} />
          </Card>
        </Col>
      </Row>
      <Card title="持仓明细">
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
      <Modal
        title="确认替换持仓"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleReplace}
        confirmLoading={replacing}
        okText="确认替换"
        cancelText="取消"
        width={700}
      >
        <p style={{ marginBottom: 12, color: 'var(--ft-text-muted)' }}>
          将从截图中识别到以下 <Tag>{parsedHoldings.length}</Tag> 只基金持仓，确认后将替换当前全部持仓数据：
        </p>
        <Table
          dataSource={parsedHoldings}
          columns={parsedColumns}
          rowKey="fundCode"
          pagination={false}
          size="small"
        />
      </Modal>
    </div>
  )
}
