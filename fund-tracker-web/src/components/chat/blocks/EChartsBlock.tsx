import ReactECharts from 'echarts-for-react'

export default function EChartsBlock({ option }: { option: any }) {
  if (!option) return null

  const lightOption = {
    ...option,
    backgroundColor: 'transparent',
    textStyle: { color: '#475569', fontFamily: 'Fira Code, monospace' },
    title: option.title ? {
      ...option.title,
      textStyle: { ...option.title?.textStyle, color: '#0F172A', fontSize: 14, fontWeight: 600 },
    } : undefined,
    legend: option.legend ? {
      ...option.legend,
      textStyle: { ...option.legend?.textStyle, color: '#64748B', fontSize: 11 },
    } : undefined,
    xAxis: option.xAxis ? {
      ...option.xAxis,
      axisLine: { ...option.xAxis?.axisLine, lineStyle: { color: '#E2E8F0' } },
      axisLabel: { ...option.xAxis?.axisLabel, color: '#64748B', fontSize: 11, fontFamily: 'Fira Sans' },
      splitLine: { show: false },
    } : undefined,
    yAxis: option.yAxis ? {
      ...option.yAxis,
      axisLine: { show: false },
      axisLabel: { ...option.yAxis?.axisLabel, color: '#64748B', fontSize: 11, fontFamily: 'Fira Code, monospace' },
      splitLine: { ...option.yAxis?.splitLine, lineStyle: { color: '#E2E8F0', type: 'dashed' } },
    } : undefined,
    tooltip: option.tooltip ? {
      ...option.tooltip,
      backgroundColor: '#FFFFFF',
      borderColor: '#E2E8F0',
      textStyle: { color: '#0F172A', fontSize: 12 },
      trigger: 'axis',
    } : undefined,
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  }

  return (
    <div className="ft-chart-card">
      <ReactECharts option={lightOption} style={{ height: 320 }} notMerge />
    </div>
  )
}
