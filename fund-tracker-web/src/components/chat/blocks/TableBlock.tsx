import { Table } from 'antd'

export default function TableBlock({ columns, dataSource }: { columns: any[]; dataSource: any[] }) {
  if (!columns || !dataSource) return null

  return (
    <div className="ft-table-wrap">
      <Table
        columns={columns}
        dataSource={dataSource}
        rowKey={(_, i) => String(i)}
        pagination={false}
        size="small"
        bordered={false}
      />
    </div>
  )
}
