import ReactEChartsCore from 'echarts-for-react'
import type { NavHistory } from '../types'

interface Props {
  data: NavHistory[]
}

export default function NavChart({ data }: Props) {
  const dates = data.map((d) => d.date?.slice(5))
  const values = data.map((d) => d.nav)

  const option = {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category' as const, data: dates },
    yAxis: { type: 'value' as const, name: '净值' },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      lineStyle: { color: '#1890ff', width: 2 },
      areaStyle: { color: 'rgba(24, 144, 255, 0.1)' },
      showSymbol: false,
    }],
  }

  return <ReactEChartsCore option={option} style={{ height: 400 }} />
}
