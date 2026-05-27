import ReactEChartsCore from 'echarts-for-react'
import type { ProfitPoint } from '../types'

interface Props {
  data: ProfitPoint[]
}

export default function ProfitChart({ data }: Props) {
  const dates = data.map((d) => d.date)
  const profits = data.map((d) => d.totalProfit)
  const values = data.map((d) => d.totalMarketValue)

  const option = {
    tooltip: { trigger: 'axis' as const },
    legend: { data: ['总市值', '总盈亏'] },
    grid: { left: 60, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category' as const, data: dates },
    yAxis: [
      { type: 'value' as const, name: '市值' },
      { type: 'value' as const, name: '盈亏' },
    ],
    series: [
      {
        name: '总市值', type: 'line', data: values,
        smooth: true, lineStyle: { color: '#1890ff', width: 2 },
        itemStyle: { color: '#1890ff' },
      },
      {
        name: '总盈亏', type: 'bar', data: profits, yAxisIndex: 1,
        itemStyle: {
          color: (params: any) => params.value >= 0 ? '#f5222d' : '#52c41a',
        },
      },
    ],
  }

  return <ReactEChartsCore option={option} style={{ height: 400 }} />
}
