import ReactEChartsCore from 'echarts-for-react'
import type { DistributionItem } from '../types'

interface Props {
  data: DistributionItem[]
}

export default function DistributionChart({ data }: Props) {
  const option = {
    tooltip: {
      trigger: 'item' as const,
      formatter: (params: any) => `${params.name}: ¥${params.value.toLocaleString()} (${params.percent}%)`,
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '50%'],
      data: data.map((d) => ({ name: d.fundName, value: d.value })),
      label: { formatter: '{b}: {d}%' },
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' },
      },
    }],
  }

  return <ReactEChartsCore option={option} style={{ height: 400 }} />
}
