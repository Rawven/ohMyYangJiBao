import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Card, Table, Button, Modal, Form, Input, Select, DatePicker, InputNumber, message, Tag, Spin, Alert, Popconfirm } from 'antd'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import { fetchTransactions, addTransaction, deleteTransaction } from '../api/transaction'
import { fetchFunds } from '../api/fund'
import { formatMoney, formatDateTime } from '../utils/format'
import type { Transaction } from '../types'

export default function Transactions() {
  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()
  const queryClient = useQueryClient()

  const { data: transactions, isLoading, isError } = useQuery({
    queryKey: ['transactions'],
    queryFn: () => fetchTransactions(),
  })

  const { data: funds } = useQuery({
    queryKey: ['funds-list'],
    queryFn: () => fetchFunds(undefined, undefined, 1, 10000),
  })

  const addMutation = useMutation({
    mutationFn: addTransaction,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['holdings'] })
      queryClient.invalidateQueries({ queryKey: ['analysis'] })
      message.success('添加成功')
      setModalOpen(false)
      form.resetFields()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteTransaction,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['holdings'] })
      queryClient.invalidateQueries({ queryKey: ['analysis'] })
      message.success('删除成功')
    },
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (isError) return <Alert type="error" message="加载交易记录失败" banner />

  const handleAdd = () => {
    form.validateFields().then(values => {
      addMutation.mutate({
        fundCode: values.fundCode,
        type: values.type,
        amount: values.amount,
        nav: values.nav,
        shares: values.shares,
        transactionDate: values.transactionDate.format('YYYY-MM-DD HH:mm:ss'),
        note: values.note || '',
      })
    }).catch(() => {})
  }

  const columns = [
    { title: '基金代码', dataIndex: 'fundCode', key: 'fundCode' },
    {
      title: '类型', dataIndex: 'type', key: 'type',
      render: (v: string) => (
        <Tag color={v === 'BUY' ? 'red' : 'green'}>{v === 'BUY' ? '买入' : '卖出'}</Tag>
      ),
    },
    { title: '金额', dataIndex: 'amount', key: 'amount', render: (v: number) => `¥${formatMoney(v)}` },
    { title: '净值', dataIndex: 'nav', key: 'nav', render: (v: number) => formatMoney(v) },
    { title: '份额', dataIndex: 'shares', key: 'shares', render: (v: number) => v.toLocaleString() },
    { title: '时间', dataIndex: 'transactionDate', key: 'transactionDate', render: (v: string) => formatDateTime(v) },
    { title: '备注', dataIndex: 'note', key: 'note' },
    {
      title: '操作', key: 'action',
      render: (_: unknown, record: Transaction) => (
        <Popconfirm title="确定删除该交易记录？" onConfirm={() => deleteMutation.mutate(record.id)} okText="确定" cancelText="取消">
          <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <Card
      title="交易记录"
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>新增交易</Button>}
    >
      <Table columns={columns} dataSource={transactions || []} rowKey="id" pagination={{ pageSize: 10 }} />
      <Modal
        title="新增交易"
        open={modalOpen}
        onOk={handleAdd}
        onCancel={() => setModalOpen(false)}
        confirmLoading={addMutation.isPending}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="fundCode" label="基金" rules={[{ required: true, message: '请选择基金' }]}>
            <Select
              showSearch
              placeholder="选择基金"
              optionFilterProp="label"
              options={(funds?.items || []).map((f) => ({ label: `${f.code} - ${f.name}`, value: f.code }))}
            />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Select options={[{ label: '买入', value: 'BUY' }, { label: '卖出', value: 'SELL' }]} />
          </Form.Item>
          <Form.Item name="amount" label="金额" rules={[{ required: true, message: '请输入金额' }]}>
            <InputNumber style={{ width: '100%' }} precision={2} min={0} />
          </Form.Item>
          <Form.Item name="nav" label="净值" rules={[{ required: true, message: '请输入净值' }]}>
            <InputNumber style={{ width: '100%' }} precision={4} min={0} />
          </Form.Item>
          <Form.Item name="shares" label="份额" rules={[{ required: true, message: '请输入份额' }]}>
            <InputNumber style={{ width: '100%' }} precision={2} min={0} />
          </Form.Item>
          <Form.Item name="transactionDate" label="交易时间" rules={[{ required: true, message: '请选择时间' }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="note" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
